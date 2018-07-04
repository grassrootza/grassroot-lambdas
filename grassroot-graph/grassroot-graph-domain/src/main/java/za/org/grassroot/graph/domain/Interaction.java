package za.org.grassroot.graph.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.annotation.*;
import org.neo4j.ogm.id.UuidStrategy;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.enums.InteractionType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;

@NodeEntity @Getter @Setter @Slf4j
public class Interaction extends GrassrootGraphEntity {

    @Id @GeneratedValue(strategy = UuidStrategy.class) String id;
    @Property protected Instant creationTime; // creation time _in graph_ (not necessarily on platform)

    @Property private InteractionType interactionType;

    @Relationship(type = "GENERATOR", direction = Relationship.INCOMING)
    private Actor initiator;

    public Interaction() {
        this.entityType = GraphEntityType.INTERACTION;
    }

    public Interaction(Actor initiator) {
        this();
        this.initiator = initiator;
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