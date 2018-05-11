package za.org.grassroot.graph.domain.repository;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import za.org.grassroot.graph.domain.Event;

public interface EventRepository extends Neo4jRepository<Event, String> {

    Event findByPlatformUid(String platformId);

}
