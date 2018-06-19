package za.org.grassroot.graph.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.id.UuidStrategy;
import za.org.grassroot.graph.domain.enums.GraphEntityType;

import java.util.*;

@NodeEntity @Getter @Setter @Slf4j
public class Interaction extends GrassrootGraphEntity {

    @Id @GeneratedValue(strategy = UuidStrategy.class) String id;

    @Relationship(type = "GENERATOR", direction = Relationship.INCOMING)
    private GrassrootGraphEntity initiator;

    @Relationship(type = "PARTICIPATES", direction = Relationship.INCOMING)
    private List<Actor> participants;

    private Interaction() {
        this.entityType = GraphEntityType.INTERACTION;
    }

    public Interaction(Actor initiator, Actor firstParticipant) {
        this();
        this.initiator = initiator;
        this.participants = new ArrayList<>();
        this.participants.add(firstParticipant);
    }

    private void addParticipant(Actor participant) {
        if (this.participants == null)
            this.participants = new ArrayList<>();
        this.participants.add(participant);
    }

    public void addParticipatingActor(Actor actor) {
        this.addParticipant(actor);
    }

    public void addParticipatesInEntity(GrassrootGraphEntity graphEntity) {
        log.error("Should not be calling this on interaction");
    }

    public Set<Actor> getParticipatingActors() {
        return new HashSet<>(getParticipants());
    }

    public void addParticipatingEvent(Event event) {
        // for the moment, we're not allowing this. but we might.
        throw new IllegalArgumentException("Error! Cannot have an event participating in an interaction ... yet");
    }

    public void addGenerator(GrassrootGraphEntity graphEntity) {
        this.initiator = graphEntity;
    }

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
        Interaction that = (Interaction) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {

        return Objects.hash(id);
    }


}
