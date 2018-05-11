package za.org.grassroot.graph.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.*;
import org.neo4j.ogm.id.UuidStrategy;
import za.org.grassroot.graph.domain.enums.EventType;
import za.org.grassroot.graph.domain.enums.GraphEntityType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@NodeEntity @Getter @Setter @ToString
public class Event extends GrassrootGraphEntity {

    @Id @GeneratedValue(strategy = UuidStrategy.class) private String id;

    @Property private Instant creationTime; // creation time _in graph_ (not necessarily on platform)
    @Property private String platformUid;

    @Property private EventType eventType;

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

    public Event() {
        this.entityType = GraphEntityType.EVENT;
    }

    public Event(GrassrootGraphEntity creator, EventType eventType) {
        this();
        this.creationTime = Instant.now();
        this.creator = creator;
        this.eventType = eventType;
    }

    @Override
    public void addParticipatingActor(Actor actor) {
        if (this.participants == null)
            this.participants = new ArrayList<>();
        this.participants.add(actor);
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
