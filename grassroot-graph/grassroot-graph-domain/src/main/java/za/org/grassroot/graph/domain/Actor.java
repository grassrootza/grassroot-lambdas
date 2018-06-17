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

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@NodeEntity @Getter @Setter @Slf4j
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Actor extends GrassrootGraphEntity {

    @Id @GeneratedValue(strategy = UuidStrategy.class) private String id;

    @Property @Index(unique = true) protected String platformUid;
    @Property @Index private ActorType actorType;

    @Relationship(type = GrassrootRelationship.TYPE_GENERATOR, direction = Relationship.INCOMING)
    private Actor createdByActor;

    @Relationship(type = GrassrootRelationship.TYPE_GENERATOR, direction = Relationship.OUTGOING)
    private Set<Event> createdEvents;

    @Relationship(type = GrassrootRelationship.TYPE_GENERATOR, direction = Relationship.OUTGOING)
    private Set<Actor> createdActors;

    @Relationship(type = GrassrootRelationship.TYPE_GENERATOR, direction = Relationship.OUTGOING)
    private Set<Actor> createdInteractions;

    @Relationship(type = GrassrootRelationship.TYPE_PARTICIPATES, direction = Relationship.OUTGOING)
    private Set<Actor> participatesInActors;

    @Relationship(type = GrassrootRelationship.TYPE_PARTICIPATES, direction = Relationship.OUTGOING)
    private Set<Event> participatesInEvents;

    @Relationship(type = GrassrootRelationship.TYPE_PARTICIPATES, direction = Relationship.OUTGOING)
    private Set<Actor> participatesInInteractions;

    // note: from my understanding of docs (and queries as shown in logs), this is going to be an efficient call
    // whenever we load something with a lot of participants, because eager fetching to depth 1 is cheap in graph.
    // but - keep an eye on it.
    @Relationship(type = GrassrootRelationship.TYPE_PARTICIPATES, direction = Relationship.INCOMING)
    private Set<Actor> participants;

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

    public void addParticipatesInActor(Actor actor, boolean dontWireBothSide) {
        if (this.participatesInActors == null)
            this.participatesInActors = new HashSet();
        this.participatesInActors.add(actor);
        if (!dontWireBothSide)
            actor.addParticipant(this, true);
    }

    @Override
    public void addParticipatingActor(Actor actor) {
        this.addParticipant(actor, false);
    }

    @Override
    public void addParticipatesInEntity(GrassrootGraphEntity graphEntity) {
        switch (graphEntity.getEntityType()) {
            case ACTOR:
                this.addParticipatesInActor((Actor) graphEntity, true);
                break;
            case EVENT:
                this.getParticipatesInEvents().add((Event) graphEntity);
                break;
            case INTERACTION:
                break;
        }
    }

    @Override
    public Set<Actor> getParticipatingActors() {
        return new HashSet<>(getParticipants());
    }

    @Override
    public void addParticipatingEvent(Event event) {
        if (this.participatingEvents == null)
            this.participatingEvents = new HashSet<>();
        this.participatingEvents.add(event);
    }

    @Override
    public void addGenerator(GrassrootGraphEntity graphEntity) {
        if (!GraphEntityType.ACTOR.equals(graphEntity.getEntityType())) {
            throw new IllegalArgumentException("Error! Only actors can generate actors");
        }
        this.createdByActor = (Actor) graphEntity;
    }

    @Override
    public void removeParticipant(GrassrootGraphEntity participant) {
        if (participant.isActor() && this.participants != null) {
            this.participants.remove((Actor) participant);
        } else if (participant.isEvent() && this.participatingEvents != null) {
            this.participatingEvents.remove((Event) participant);
        }
    }

    private void addParticipant(Actor actor, boolean callFromChild) {
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
