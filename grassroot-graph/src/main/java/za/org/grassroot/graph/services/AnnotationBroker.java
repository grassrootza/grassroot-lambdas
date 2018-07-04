package za.org.grassroot.graph.services;

import za.org.grassroot.graph.domain.enums.GrassrootRelationship;

import java.util.List;
import java.util.Map;

public interface AnnotationBroker {

    boolean annotateEntity(PlatformEntityDTO platformEntity, Map<String, String> properties, List<String> tags);

    boolean annotateRelationship(PlatformEntityDTO tailEntity, PlatformEntityDTO headEntity,
                                 GrassrootRelationship.Type relationshipType, List<String> tags);

}
