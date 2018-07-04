package za.org.grassroot.graph.dto;

import lombok.*;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.enums.GrassrootRelationship;

@Getter @Setter @AllArgsConstructor @NoArgsConstructor @ToString
public class IncomingRelationship {

    private String tailEntityPlatformId;
    private GraphEntityType tailEntityType;
    private String tailEntitySubtype;

    private String headEntityPlatformId;
    private GraphEntityType headEntityType;
    private String headEntitySubtype;

    private GrassrootRelationship.Type relationshipType;

}
