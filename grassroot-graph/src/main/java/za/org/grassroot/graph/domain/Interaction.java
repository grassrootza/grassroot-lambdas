package za.org.grassroot.graph.domain;

import lombok.Getter;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.id.UuidStrategy;

import java.util.UUID;

@NodeEntity @Getter
public class Interaction implements GrassrootGraphEntity {

    @Id @GeneratedValue(strategy = UuidStrategy.class) UUID id;

}
