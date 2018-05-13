package za.org.grassroot.graph.dto;

public enum ActionType {

    // note: keeping create & alter separate, even though might have case of two instructions in queue, and parallel
    // lambdas resulting in alter being called in parallel or first. but in that case this invocation should just put
    // it back in queue or not accept the message, and instead put it back into the queue

    CREATE_ENTITY,
    ALTER_ENTITY,
    REMOVE_ENTITY,

    CREATE_RELATIONSHIP,
    ALTER_RELATIONSHIP,
    REMOVE_RELATIONSHIP

}
