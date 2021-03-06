package za.org.grassroot.graph.repository;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.graph.domain.Event;

public interface EventRepository extends Neo4jRepository<Event, String> {

    Event findByPlatformUid(String platformId);

    Event findByPlatformUid(String platformId, int depth);

    long countByPlatformUid(String platformUid);

    @Transactional
    Long deleteByPlatformUid(String platformId);

    @Transactional
    Long deleteByPlatformUidContaining(String platformUidFragment);

}
