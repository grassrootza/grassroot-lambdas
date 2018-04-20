package za.org.grassroot.graph.domain;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.id.UuidStrategy;
import za.org.grassroot.graph.domain.enums.GraphEntityType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@NodeEntity @Getter @Setter
public class Interaction extends GrassrootGraphEntity {

    @Id @GeneratedValue(strategy = UuidStrategy.class) String id;

    @Relationship(type = "GENERATOR", direction = Relationship.INCOMING)
    private Actor initiator;

    @Relationship(type = "PARTICIPATES", direction = Relationship.INCOMING)
    private List<Actor> participants;

    public Interaction() {
        this.entityType = GraphEntityType.INTERACTION;
    }

    public Interaction(Actor initiator, Actor firstParticipant) {
        this();
        this.creationTime = Instant.now();
        this.initiator = initiator;
        this.participants = new ArrayList<>();
        this.participants.add(firstParticipant);
    }

    public void addParticipant(Actor participant) {
        if (this.participants == null)
            this.participants = new ArrayList<>();
        this.participants.add(participant);
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
