package za.org.grassroot.graph.domain.repository;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import za.org.grassroot.graph.domain.Interaction;

public interface InteractionRepository extends Neo4jRepository<Interaction, String> {
}
