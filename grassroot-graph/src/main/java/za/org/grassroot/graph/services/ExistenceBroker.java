package za.org.grassroot.graph.services;

import za.org.grassroot.graph.domain.enums.GrassrootRelationship;

public interface ExistenceBroker {

    boolean entityExists(PlatformEntityDTO platformEntity);

    boolean relationshipExists(PlatformEntityDTO tailEntity, PlatformEntityDTO headEntity,
                                  GrassrootRelationship.Type relationshipType);

    boolean addEntityToGraph(PlatformEntityDTO platformEntity);

}
