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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@NodeEntity @Getter @Setter @ToString @Slf4j
public class Event extends GrassrootGraphEntity {

    @Id @GeneratedValue(strategy = UuidStrategy.class) private String id;

    @Property @Index private String platformUid;

    @Property private EventType eventType;

    @Property private long eventStartTimeEpochMilli;

    // todo someone has to generate the event (= creator), but we want some form of parent mapping (other side of below)
    @Relationship(type = "GENERATOR", direction = Relationship.INCOMING)
    private Actor creator;

    @Relationship(type = "PARTICIPATES", direction = Relationship.INCOMING)
    private List<ActorInEvent> participants;

    @Relationship(type = "PARTICIPATES", direction = Relationship.OUTGOING)
    private List<Actor> participatesIn;

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

    public void addParticipatingActor(Actor actor) {
        if (this.participants == null)
            this.participants = new ArrayList<>();
        this.participants.add(new ActorInEvent(actor, this));
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
