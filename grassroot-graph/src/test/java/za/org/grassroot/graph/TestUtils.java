package za.org.grassroot.graph;

import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.Interaction;
import za.org.grassroot.graph.domain.enums.ActorType;
import za.org.grassroot.graph.domain.enums.EventType;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.enums.InteractionType;
import za.org.grassroot.graph.dto.ActionType;
import za.org.grassroot.graph.dto.IncomingDataObject;
import za.org.grassroot.graph.dto.IncomingGraphAction;

import java.time.Instant;
import java.util.Collections;

public class TestUtils {

    private TestUtils() {}

    public static IncomingGraphAction wrapActorAction(ActorType actorType, String platformId, ActionType actionType) {
        Actor testActor = new Actor(actorType, platformId);
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.ACTOR, testActor);
        return new IncomingGraphAction(platformId, actionType, Collections.singletonList(dataObject), null, null);
    }

    public static IncomingGraphAction wrapEventAction(EventType eventType, String platformId, ActionType actionType) {
        Event testEvent = new Event(eventType, platformId, Instant.now().toEpochMilli());
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.EVENT, testEvent);
        return new IncomingGraphAction(platformId, actionType, Collections.singletonList(dataObject), null, null);
    }

    public static IncomingGraphAction wrapInteractionAction(InteractionType interactionType, String id, ActionType actionType) {
        Interaction testInteraction = new Interaction();
        testInteraction.setInteractionType(interactionType);
        testInteraction.setId(id);
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.INTERACTION, testInteraction);
        return new IncomingGraphAction(id, actionType, Collections.singletonList(dataObject), null, null);
    }

}