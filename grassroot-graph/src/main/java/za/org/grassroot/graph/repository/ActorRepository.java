package za.org.grassroot.graph.repository;

import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.graph.domain.Actor;

import java.util.Collection;

public interface ActorRepository extends Neo4jRepository<Actor, String> {

    Actor findByPlatformUid(String platformId);

    Actor findByPlatformUid(String platformId, int depth);

    // todo : decide on optimal level here, since can't (unfortunately) parameterize depth (maybe have shallow & deep methods)
    // todo : consider using @Depth annotation on a more general method to do this (see docs)
    @Query("match (:Actor {platformUid: {platformUid}, actorType: 'MOVEMENT'})<-[:PARTICIPATES*1..5]-(actor) return actor;")
    Collection<Actor> findMovementParticipantsInDepth(@Param("platformUid") final String platformUid);

    long countByPlatformUid(final String platformUid);

    @Transactional
    Long deleteByPlatformUid(String platformId);

    @Transactional
    Long deleteByPlatformUidContaining(String platformUidFragment);

}
