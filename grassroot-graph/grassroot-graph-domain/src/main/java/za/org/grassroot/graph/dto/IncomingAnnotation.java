package za.org.grassroot.graph.dto;

import lombok.*;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.enums.GraphEntityType;

@Getter @Setter @AllArgsConstructor @NoArgsConstructor @ToString
public class IncomingAnnotation {

    private String platformId;
    private GraphEntityType entityType;

    private String description;
    private String[] tags;
    private String language;
    private String location;

}