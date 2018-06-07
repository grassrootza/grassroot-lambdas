package za.org.grassroot.graph.repository;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.graph.domain.Interaction;

public interface InteractionRepository extends Neo4jRepository<Interaction, String> {

    Interaction findByPlatformUid(String platformId);

    @Transactional
    Long deleteByPlatformUidContaining(String platformUidFragment);

}
