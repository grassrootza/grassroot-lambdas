package za.org.grassroot.graph.services;

import za.org.grassroot.graph.domain.enums.GrassrootRelationship;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface AnnotationBroker {

    boolean annotateEntity(PlatformEntityDTO platformEntity, Map<String, String> properties, Set<String> tags);

    boolean removeEntityAnnotation(PlatformEntityDTO platformEntity, Set<String> keysToRemove, Set<String> tagsToRemove);

    boolean annotateParticipation(PlatformEntityDTO tailEntity, PlatformEntityDTO headEntity, Set<String> tags);

    boolean removeParticipationAnnotation(PlatformEntityDTO tailEntity, PlatformEntityDTO headEntity, Set<String> tagsToRemove);

}
