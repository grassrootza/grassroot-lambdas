package za.org.grassroot.graph.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.annotation.*;
import org.neo4j.ogm.id.UuidStrategy;
import za.org.grassroot.graph.domain.enums.EventType;
import za.org.grassroot.graph.domain.enums.GraphEntityType;

import java.util.*;

@NodeEntity @Getter @Setter @ToString @Slf4j
public class Event extends GrassrootGraphEntity {

    @Id @GeneratedValue(strategy = UuidStrategy.class) private String id;

    @Property @Index private String platformUid;

    @Property private EventType eventType;

    @Property private long eventStartTimeEpochMilli;

    @Relationship(type = "GENERATOR", direction = Relationship.INCOMING)
    private GrassrootGraphEntity creator;

    @Relationship(type = "PARTICIPATES", direction = Relationship.OUTGOING)
    private List<Actor> participatesIn;

    @Relationship(type = "PARTICIPATES", direction = Relationship.INCOMING)
    private List<Actor> participants;

    @Relationship(type = "GENERATOR", direction = Relationship.OUTGOING)
    private List<Event> childEvents;

    @Relationship(type = "GENERATOR", direction = Relationship.OUTGOING)
    private List<Interaction> childInteractions;

    private Event() {
        this.entityType = GraphEntityType.EVENT;
    }

    public Event(EventType eventType, String platformId, long startTimeMillis) {
        this();
        this.eventType = eventType;
        this.eventStartTimeEpochMilli = startTimeMillis;
        this.platformUid = platformId;
    }

    @Override
    public void addParticipatingActor(Actor actor) {
        if (this.participants == null)
            this.participants = new ArrayList<>();
        this.participants.add(actor);
    }

    @Override
    public void addParticipatesInEntity(GrassrootGraphEntity graphEntity) {
        this.getParticipants().add((Actor) graphEntity); // todo: clean up
    }

    @Override
    public Set<Actor> getParticipatingActors() {
        return new HashSet<>(getParticipants());
    }

    @Override
    public void addParticipatingEvent(Event event) {
        throw new IllegalArgumentException("Error! Events cannot participate in other events (can only have generation relationship");
    }

    @Override
    public void addGenerator(GrassrootGraphEntity graphEntity) {
        this.creator = graphEntity;
    }

    @Override
    public void removeParticipant(GrassrootGraphEntity participant) {
        if (participant.isActor()) {
            if (participants != null)
                participants.remove((Actor) participant);
        } else {
            throw new IllegalArgumentException("Trying to remove a non-actor entity from an event");
        }
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
