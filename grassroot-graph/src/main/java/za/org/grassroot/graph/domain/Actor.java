package za.org.grassroot.graph.domain;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.id.UuidStrategy;
import za.org.grassroot.graph.domain.enums.ActorType;

import java.time.Instant;
import java.util.UUID;

@NodeEntity @Getter @Setter
public class Actor implements GrassrootGraphEntity {

    @Id @GeneratedValue(strategy = UuidStrategy.class) private UUID id;
    @Property private Instant creationTime; // creation time _in graph_ (not necessarily on platform)

    // UID of entity on main platform (to enable audit / traceability later)
    @Property private String platformUid;
    @Property private ActorType type;

    public Actor(ActorType actorType) {
        this.creationTime = Instant.now();
        this.type = actorType;
    }

}
