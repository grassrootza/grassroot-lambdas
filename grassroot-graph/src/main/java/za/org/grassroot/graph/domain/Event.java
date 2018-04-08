package za.org.grassroot.graph.domain;

import lombok.Getter;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.id.UuidStrategy;
import za.org.grassroot.graph.domain.enums.EventType;

import java.time.Instant;
import java.util.UUID;

@NodeEntity @Getter
public class Event implements GrassrootGraphEntity {

    @Id @GeneratedValue(strategy = UuidStrategy.class) private UUID id;

    @Property private Instant creationTime; // creation time _in graph_ (not necessarily on platform)
    @Property private String platformUid;

    @Property private EventType eventType;

}
