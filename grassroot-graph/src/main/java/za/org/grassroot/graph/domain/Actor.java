package za.org.grassroot.graph.domain;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.*;
import org.neo4j.ogm.id.UuidStrategy;
import za.org.grassroot.graph.domain.enums.ActorType;
import za.org.grassroot.graph.domain.enums.GraphEntityType;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@NodeEntity @Getter @Setter
public class Actor extends GrassrootGraphEntity {

    @Id @GeneratedValue(strategy = UuidStrategy.class) private String id;

    @Property @Index protected String platformUid;
    @Property @Index private ActorType actorType;

    @Relationship(type = "GENERATOR", direction = Relationship.INCOMING)
    private Actor createdByActor;

    @Relationship(type = "GENERATOR", direction = Relationship.OUTGOING)
    private Set<Event> createdEvents;

    @Relationship(type = "GENERATOR", direction = Relationship.OUTGOING)
    private Set<Actor> createdActors;

    @Relationship(type = "GENERATOR", direction = Relationship.OUTGOING)
    private Set<Actor> createdInteractions;

    @Relationship(type = "PARTICIPATES", direction = Relationship.OUTGOING)
    private Set<Actor> participatesInActors;

    @Relationship(type = "PARTICIPATES", direction = Relationship.OUTGOING)
    private Set<Actor> partcipatesInEvents;

    @Relationship(type = "PARTICIPATES", direction = Relationship.OUTGOING)
    private Set<Actor> participatesInInteractions;

    // note: from my understanding of docs (and queries as shown in logs), this is going to be an efficient call
    // whenever we load something with a lot of participants, because eager fetching to depth 1 is cheap in graph.
    // but - keep an eye on it.
    @Relationship(type = "PARTICIPATES", direction = Relationship.INCOMING)
    private Set<Actor> participants;

    public Actor() {
        this.entityType = GraphEntityType.ACTOR;
    }

    public Actor(ActorType actorType) {
        this();
        this.creationTime = Instant.now();
        this.actorType = actorType;
    }

    public void addParticipatesInActor(Actor actor, boolean callFromParent) {
        if (this.participatesInActors == null)
            this.participatesInActors = new HashSet();
        this.participatesInActors.add(actor);
        if (!callFromParent)
            actor.addParticipant(this, true);
    }

    public void addParticipant(Actor actor, boolean callFromChild) {
        if (this.participants == null)
            this.participants = new HashSet<>();
        this.participants.add(actor);
        if (!callFromChild)
            actor.addParticipatesInActor(this, true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Actor actor = (Actor) o;
        return Objects.equals(id, actor.id);
    }

    @Override
    public int hashCode() {

        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Actor{" +
                "id='" + id + '\'' +
                ", actorType=" + actorType +
                ", platformUid='" + platformUid + '\'' +
                '}';
    }
}
