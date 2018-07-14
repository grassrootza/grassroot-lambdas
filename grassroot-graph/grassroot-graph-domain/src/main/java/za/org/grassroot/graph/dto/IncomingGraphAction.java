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
    private List<IncomingAnnotation> annotations;

    public void addDataObject(IncomingDataObject dataObject) {
        if (this.dataObjects == null)
            this.dataObjects = new ArrayList<>();
        this.dataObjects.add(dataObject);
    }

    public void addRelationship(IncomingRelationship incomingRelationship) {
        if (this.relationships == null)
            this.relationships = new ArrayList<>();
        this.relationships.add(incomingRelationship);
    }

    public void addAnnotation(IncomingAnnotation incomingAnnotation) {
        if (this.annotations == null)
            this.annotations = new ArrayList<>();
        this.annotations.add(incomingAnnotation);
    }

    public long operationsCount() {
        return (dataObjects != null ? dataObjects.size() : 0) +
                (relationships != null  ? relationships.size() : 0) +
                (annotations != null ? annotations.size() : 0);
    }

}
