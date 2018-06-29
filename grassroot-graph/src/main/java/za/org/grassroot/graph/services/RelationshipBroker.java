package za.org.grassroot.graph.services;

import za.org.grassroot.graph.domain.GrassrootGraphEntity;

public interface RelationshipBroker {

    boolean addParticipation(PlatformEntityDTO participant, PlatformEntityDTO participatesIn);

    boolean removeParticipation(PlatformEntityDTO participant, PlatformEntityDTO participatesIn);

    boolean setGeneration(PlatformEntityDTO generator, PlatformEntityDTO generated);

    boolean addObserver(PlatformEntityDTO observer, PlatformEntityDTO observed);

}
