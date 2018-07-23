package za.org.grassroot.graph.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.annotation.*;
import org.neo4j.ogm.id.UuidStrategy;
import za.org.grassroot.graph.domain.enums.EventType;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.enums.GrassrootRelationship;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

@NodeEntity @Getter @Setter @ToString @Slf4j
public class Event extends GrassrootGraphEntity {

    @Id @GeneratedValue(strategy = UuidStrategy.class) private String id;
    @Property protected Instant creationTime; // creation time _in graph_ (not necessarily on platform)

    @Property @Index(unique = true) private String platformUid;

    @Property private EventType eventType;

    @Property private long eventStartTimeEpochMilli;

    @Properties private Map<String, String> stdProps;

    @Property private String[] stdTags;

    @Relationship(type = GrassrootRelationship.TYPE_PARTICIPATES)
    private Set<Actor> participatesIn;

    @Relationship(type = GrassrootRelationship.TYPE_GENERATOR)
    private Set<Event> childEvents;

    @Relationship(type = GrassrootRelationship.TYPE_GENERATOR)
    private Set<Interaction> childInteractions;

    @Relationship(type = GrassrootRelationship.TYPE_GENERATOR, direction = Relationship.INCOMING)
    private GrassrootGraphEntity creator;

    public Event() {
        this.entityType = GraphEntityType.EVENT;
        this.stdProps = new HashMap<>();
        this.participatesIn = new HashSet<>();
        this.childEvents = new HashSet<>();
        this.childInteractions = new HashSet<>();
    }

    public Event(EventType eventType, String platformId, long startTimeMillis) {
        this();
        this.eventType = eventType;
        this.platformUid = platformId;
        this.eventStartTimeEpochMilli = startTimeMillis;
    }

    public void addParticipatesInActor(Actor actor) {
        this.participatesIn.add(actor);
    }

    public void removeParticipatesInActor(Actor actor) {
        this.participatesIn.remove(actor);
    }

    public void addChildEvent(Event event) {
        this.childEvents.add(event);
    }

    public void addChildInteraction(Interaction interaction) {
        this.childInteractions.add(interaction);
    }

    public void addProperties(Map<String, String> newProperties) {
        if (newProperties != null) this.stdProps.putAll(newProperties);
    }

    public void removeProperties(Set<String> keysToRemove) {
        if (keysToRemove != null)
            for (String key : keysToRemove) this.stdProps.put(key, "");
    }

    public void addTags(Set<String> newTags) {
        this.stdTags = GraphStringUtils.addStringsToArray(this.stdTags, newTags);
    }

    public void removeTags(Set<String> tagsToRemove) {
        this.stdTags = GraphStringUtils.removeStringsFromArray(this.stdTags, tagsToRemove);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return Objects.equals(platformUid, event.platformUid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(platformUid);
    }

}