package za.org.grassroot.graph.dto;

import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Getter @Setter @ToString @Slf4j @AllArgsConstructor @NoArgsConstructor
public class IncomingGraphAction {

    private String actorPlatformId;
    private ActionType actionType;
    private List<IncomingDataObject> dataObjects;
    private List<IncomingRelationship> relationships;

    // used in platform
    public void addDataObject(IncomingDataObject dataObject) {
        if (this.dataObjects == null)
            this.dataObjects = new ArrayList<>();
        this.dataObjects.add(dataObject);
    }

    // used in platform
    public void addRelationship(IncomingRelationship incomingRelationship) {
        if (this.relationships == null)
            this.relationships = new ArrayList<>();
        this.relationships.add(incomingRelationship);
    }

}
