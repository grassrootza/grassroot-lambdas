package za.org.grassroot.graph.services;

import za.org.grassroot.graph.domain.enums.GrassrootRelationship;

public interface ExistenceBroker {

    boolean doesEntityExistInGraph(PlatformEntityDTO platformEntity);

    boolean doesRelationshipExistInGraph(PlatformEntityDTO tailEntity, PlatformEntityDTO headEntity,
                                         GrassrootRelationship.Type relationshipType);

    boolean addEntityToGraph(PlatformEntityDTO platformEntity);

}
