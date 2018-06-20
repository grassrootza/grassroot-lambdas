package za.org.grassroot.graph.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.annotation.*;
import org.neo4j.ogm.id.UuidStrategy;
import za.org.grassroot.graph.domain.enums.GraphEntityType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@NodeEntity @Getter @Setter @Slf4j
public class Interaction extends GrassrootGraphEntity {

    @Id @GeneratedValue(strategy = UuidStrategy.class) String id;
    @Property protected Instant creationTime; // creation time _in graph_ (not necessarily on platform)

    @Relationship(type = "GENERATOR", direction = Relationship.INCOMING)
    private Actor initiator;

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

    @Override
    public String getPlatformUid() {
        return null;
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
