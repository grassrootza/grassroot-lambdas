package za.org.grassroot.graph.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.annotation.*;
import org.neo4j.ogm.id.UuidStrategy;
import za.org.grassroot.graph.domain.enums.ActorType;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.enums.GrassrootRelationship;
import za.org.grassroot.graph.domain.relationship.ActorInActor;
import za.org.grassroot.graph.domain.relationship.ActorInEvent;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@NodeEntity @Getter @Setter @Slf4j
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Actor extends GrassrootGraphEntity {

    @Id @GeneratedValue(strategy = UuidStrategy.class) private String id;

    @Property @Index(unique = true) protected String platformUid;
    @Property @Index private ActorType actorType;

    @Relationship(type = GrassrootRelationship.TYPE_PARTICIPATES)
    private Set<ActorInActor> participatesInActors;

    @Relationship(type = GrassrootRelationship.TYPE_PARTICIPATES)
    private Set<ActorInEvent> participatesInEvents;

    @Relationship(type = GrassrootRelationship.TYPE_GENERATOR, direction = Relationship.INCOMING)
    private Actor createdByActor;

    @Relationship(type = GrassrootRelationship.TYPE_GENERATOR, direction = Relationship.OUTGOING)
    private Set<Event> createdEvents;

    @Relationship(type = GrassrootRelationship.TYPE_GENERATOR, direction = Relationship.OUTGOING)
    private Set<Actor> createdActors;

    @Relationship(type = GrassrootRelationship.TYPE_GENERATOR, direction = Relationship.OUTGOING)
    private Set<Actor> createdInteractions;

    @Relationship(type = GrassrootRelationship.TYPE_PARTICIPATES, direction = Relationship.OUTGOING)
    private Set<Actor> participatesInInteractions;

    @Relationship(type = GrassrootRelationship.TYPE_PARTICIPATES, direction = Relationship.INCOMING)
    private Set<Event> participatingEvents;

    // for JSON, JPA, etc
    public Actor() {
        this.entityType = GraphEntityType.ACTOR;
    }

    public Actor(ActorType actorType, String platformId) {
        this.actorType = actorType;
        this.platformUid = platformId;
    }

    public void addParticipatesInActor(Actor actor) {
        if (this.participatesInActors == null)
            this.participatesInActors = new HashSet();
        this.participatesInActors.add(new ActorInActor(this, actor, Instant.now())); // todo : get from incoming
    }

    public void addParticipatesInEvent(Event event) {
        if (this.participatesInEvents == null)
            this.participatingEvents = new HashSet<>();
        this.participatesInEvents.add(new ActorInEvent(this, event));
    }

    public void addGenerator(GrassrootGraphEntity graphEntity) {
        if (!GraphEntityType.ACTOR.equals(graphEntity.getEntityType())) {
            throw new IllegalArgumentException("Error! Only actors can generate actors");
        }
        this.createdByActor = (Actor) graphEntity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Actor actor = (Actor) o;
        return Objects.equals(platformUid, actor.platformUid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(platformUid);
    }

    @Override
    public String toString() {
        return "Actor{" +
                "id='" + id + '\'' +
                ", platformUid='" + platformUid + '\'' +
                ", actorType=" + actorType +
                ", creationTime=" + creationTime +
                '}';
    }
}
