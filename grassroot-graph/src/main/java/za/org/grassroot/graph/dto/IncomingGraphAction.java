package za.org.grassroot.graph.dto;

import lombok.*;

import java.util.List;

@Getter @Setter @AllArgsConstructor @NoArgsConstructor @ToString
public class IncomingGraphAction {

    private String actorPlatformId;
    private ActionType actionType;
    private List<IncomingDataObject> dataObjects;
    private List<IncomingRelationship> relationships;

}
