package za.org.grassroot.graph.services;

import java.util.List;
import java.util.Map;

public interface AnnotationBroker {

    boolean annotateEntity(PlatformEntityDTO platformEntity, Map<String, String> properties, List<String> tags);

}
