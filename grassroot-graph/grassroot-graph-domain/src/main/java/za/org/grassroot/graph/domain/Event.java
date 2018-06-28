package za.org.grassroot.graph.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.annotation.*;
import org.neo4j.ogm.id.UuidStrategy;
import za.org.grassroot.graph.domain.enums.EventType;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.relationship.ActorInEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@NodeEntity @Getter @Setter @ToString @Slf4j
public class Event extends GrassrootGraphEntity {

    @Id @GeneratedValue(strategy = UuidStrategy.class) private String id;
    @Property protected Instant creationTime; // creation time _in graph_ (not necessarily on platform)

    @Property @Index private String platformUid;

    @Property private EventType eventType;

    @Property private long eventStartTimeEpochMilli;

    @Relationship(type = "PARTICIPATES", direction = Relationship.OUTGOING)
    private List<Actor> participatesIn;

    @Relationship(type = "GENERATOR", direction = Relationship.INCOMING)
    private Actor creator;

    @Relationship(type = "GENERATOR", direction = Relationship.OUTGOING)
    private List<Event> childEvents;

    @Relationship(type = "GENERATOR", direction = Relationship.OUTGOING)
    private List<Interaction> childInteractions;

    public Event() {
        this.entityType = GraphEntityType.EVENT;
    }

    public Event(EventType eventType, String platformId, long startTimeMillis) {
        this();
        this.eventType = eventType;
        this.eventStartTimeEpochMilli = startTimeMillis;
        this.platformUid = platformId;
    }

    public void addParticipatesInActor(Actor actor) {
        if (this.participatesIn == null)
            participatesIn = new ArrayList<>();
        this.participatesIn.add(actor);
    }

    public void removeParticipatesInActor(Actor actor) {
        this.participatesIn.remove(actor);
    }

    public void addChildEvent(Event event) {
        if (this.childEvents == null)
            this.childEvents = new ArrayList<>();
        this.childEvents.add(event);
    }

    public void addChildInteraction(Interaction interaction) {
        if (this.childInteractions == null)
            this.childInteractions = new ArrayList<>();
        this.childInteractions.add(interaction);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return Objects.equals(id, event.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
