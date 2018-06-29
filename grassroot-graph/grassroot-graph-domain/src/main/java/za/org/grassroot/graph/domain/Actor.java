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
    @Property protected Instant creationTime; // creation time _in graph_ (not necessarily on platform)

    @Property @Index(unique = true) protected String platformUid;

    @Property @Index private ActorType actorType;

    @Relationship(type = GrassrootRelationship.TYPE_PARTICIPATES)
    private Set<ActorInActor> participatesInActors;

    @Relationship(type = GrassrootRelationship.TYPE_PARTICIPATES)
    private Set<ActorInEvent> participatesInEvents;

    @Relationship(type = GrassrootRelationship.TYPE_PARTICIPATES)
    private Set<Interaction> participatesInInteractions;

    @Relationship(type = GrassrootRelationship.TYPE_GENERATOR, direction = Relationship.INCOMING)
    private Actor createdByActor;

    // for JSON, JPA, etc
    public Actor() {
        this.entityType = GraphEntityType.ACTOR;
    }

    public Actor(ActorType actorType, String platformId) {
        this();
        this.actorType = actorType;
        this.platformUid = platformId;

        this.participatesInActors = new HashSet<>();
        this.participatesInEvents = new HashSet<>();
        this.participatesInInteractions = new HashSet<>();
    }

    public void addParticipatesInInteraction(Interaction interaction) {
        this.participatesInInteractions.add(interaction);
    }

    public void removeParticipationInInteraction(Interaction interaction) {
        this.participatesInInteractions.remove(interaction);
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
                ", actor participant size=" + getParticipatesInActors().size() +
                ", event participant size=" + getParticipatesInEvents().size() +
                ", interaction participant size=" + getParticipatesInInteractions().size() +
                '}';
    }

}