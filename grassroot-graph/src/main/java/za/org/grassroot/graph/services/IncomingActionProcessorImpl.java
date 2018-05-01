package za.org.grassroot.graph.services;

import org.springframework.stereotype.Service;
import za.org.grassroot.graph.dto.IncomingDataObject;
import za.org.grassroot.graph.dto.IncomingGraphAction;
import za.org.grassroot.graph.dto.IncomingRelationship;

@Service
public class IncomingActionProcessorImpl implements IncomingActionProcessor {

    @Override
    public boolean processIncomingAction(IncomingGraphAction action) {
        switch (action.getActionType()) {
            case CREATE_ENTITY:
                break;
            case ALTER_ENTITY:
                break;
            case REMOVE_ENTITY:
                break;
            case CREATE_RELATIONSHIP:
                break;
            case ALTER_RELATIONSHIP:
                break;
            case REMOVE_RELATIONSHIP:
                break;
        }
        return false;
    }

    private boolean createEntity(IncomingGraphAction action) {
        // first, create or update the entities, including possibly multiple (e.g., if packet is group creation)
        boolean executionSucceeded = action.getDataObjects().stream().reduce((dataObject, dataObject2) -> );

        // second, wire up any relationships
        executionSucceeded = executionSucceeded && action.getRelationships().stream().reduce();

        // then, return true if everything went to plan
        return executionSucceeded;
    }

    private boolean createOrUpdateSingleEntity(IncomingDataObject dataObject) {
        switch (dataObject.getEntityType()) {
            case ACTOR:
                break;
            case EVENT:
                break;
            case INTERACTION:
                break;
        }
    }

    private boolean removeSingleEntity(IncomingDataObject dataObject) {
        switch (dataObject.getEntityType()) {
            case ACTOR:
                break;
            case EVENT:
                break;
            case INTERACTION:
                break;
        }
    }

    private boolean establishRelationship(IncomingRelationship relationship) {

    }

    private boolean removeRelationship(IncomingRelationship relationship) {

    }
}
