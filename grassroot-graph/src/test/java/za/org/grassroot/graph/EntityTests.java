package za.org.grassroot.graph;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.Interaction;
import za.org.grassroot.graph.domain.enums.*;
import za.org.grassroot.graph.dto.ActionType;
import za.org.grassroot.graph.dto.IncomingDataObject;
import za.org.grassroot.graph.dto.IncomingGraphAction;
import za.org.grassroot.graph.dto.IncomingRelationship;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;
import za.org.grassroot.graph.repository.InteractionRepository;
import za.org.grassroot.graph.services.IncomingActionProcessor;

import java.time.Instant;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static za.org.grassroot.graph.GraphApplicationTests.TEST_ENTITY_PREFIX;

@RunWith(SpringRunner.class) @Slf4j
@SpringBootTest(properties = {"sqs.pull.enabled=false","sqs.push.enabled=false"})
public class EntityTests {

    @Autowired IncomingActionProcessor incomingActionProcessor;

    @Autowired ActorRepository actorRepository;
    @Autowired EventRepository eventRepository;
    @Autowired InteractionRepository interactionRepository;

    @Test
    @Rollback
    public void addAndRemoveActor() {
        dispatchActorAction(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "individual", ActionType.CREATE_ENTITY);
        Actor individualFromDB = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "individual");
        assertThat(individualFromDB, notNullValue());

        // try adding when actor already exists.
        dispatchActorAction(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "individual", ActionType.CREATE_ENTITY);
        assertThat(actorRepository.count(), is(1L));
        Actor individualFromDB2 = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "individual");
        assertThat(individualFromDB2.getCreationTime(), is(individualFromDB.getCreationTime()));

        dispatchActorAction(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "individual", ActionType.REMOVE_ENTITY);
        Actor individualFromDB3 = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "individual");
        assertThat(individualFromDB3, nullValue());

        // try removing after actor already removed.
        boolean removedActor = dispatchActorAction(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "individual", ActionType.REMOVE_ENTITY);
        assertThat(removedActor, is(false));
    }

    @Test
    @Rollback
    public void addAndRemoveEvent() {
        dispatchEventAction(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        Event meetingFromDB = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        assertThat(meetingFromDB, notNullValue());
        assertThat(meetingFromDB.getCreator().getPlatformUid(), is(TEST_ENTITY_PREFIX + "creator"));

        // try adding when event already exists
        dispatchEventAction(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        assertThat(eventRepository.count(), is(2L));
        Event meetingFromDB2 = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        assertThat(meetingFromDB2.getCreationTime(), is(meetingFromDB.getCreationTime()));

        dispatchEventAction(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.REMOVE_ENTITY);
        Event meetingFromDB3 = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        assertThat(meetingFromDB3, nullValue());

        // try removing after event already removed
        boolean removedEvent = dispatchEventAction(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.REMOVE_ENTITY);
        assertThat(removedEvent, is(false));
    }

    @Test
    @Rollback
    public void addAndRemoveInteraction() {
        dispatchInteractionAction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);
        Interaction surveyFromDB = interactionRepository.findById(TEST_ENTITY_PREFIX + "survey").orElse(null);
        assertThat(surveyFromDB, notNullValue());
        assertThat(surveyFromDB.getInitiator().getPlatformUid(), is(TEST_ENTITY_PREFIX + "initiator"));

        // try adding when interaction already exists
        dispatchInteractionAction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);
        assertThat(interactionRepository.count(), is(1L));
        Interaction surveyFromDB2 = interactionRepository.findById(TEST_ENTITY_PREFIX + "survey").orElse(null);
        assertThat(surveyFromDB2.getCreationTime(), is(surveyFromDB.getCreationTime()));

        dispatchInteractionAction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.REMOVE_ENTITY);
        Interaction surveyFromDB3 = interactionRepository.findById(TEST_ENTITY_PREFIX + "survey").orElse(null);
        assertThat(surveyFromDB3, nullValue());

        // try removing after interaction already removed
        boolean removedInteraction = dispatchInteractionAction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.REMOVE_ENTITY);
        assertThat(removedInteraction, is(false));
    }

    private boolean dispatchActorAction(ActorType actorType, String platformId, ActionType actionType) {
        Actor testActor = new Actor(actorType, platformId);
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.ACTOR, testActor);
        IncomingGraphAction graphAction = new IncomingGraphAction(platformId, actionType,
                Collections.singletonList(dataObject), null, null);

        return incomingActionProcessor.processIncomingAction(graphAction).block();
    }

    private boolean dispatchEventAction(EventType eventType, String platformId, ActionType actionType) {
        Actor creator = new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "creator");
        dispatchActorAction(creator.getActorType(), creator.getPlatformUid(), ActionType.CREATE_ENTITY);

        Event testEvent = new Event(eventType, platformId, Instant.now().toEpochMilli());
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.EVENT, testEvent);
        IncomingRelationship relationship = new IncomingRelationship(creator.getPlatformUid(), creator.getEntityType(),
                creator.getActorType().name(), testEvent.getPlatformUid(), testEvent.getEntityType(),
                testEvent.getEventType().name(), GrassrootRelationship.Type.GENERATOR);
        IncomingGraphAction graphAction = new IncomingGraphAction(platformId, actionType,
                Collections.singletonList(dataObject), Collections.singletonList(relationship), null);

        return incomingActionProcessor.processIncomingAction(graphAction).block();
    }

    private boolean dispatchInteractionAction(InteractionType interactionType, String id, ActionType actionType) {
        Actor initiator = new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "initiator");
        dispatchActorAction(initiator.getActorType(), initiator.getPlatformUid(), ActionType.CREATE_ENTITY);

        Interaction testInteraction = new Interaction(interactionType, initiator);
        testInteraction.setId(id);
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.INTERACTION, testInteraction);
        IncomingRelationship relationship = new IncomingRelationship(initiator.getPlatformUid(), initiator.getEntityType(),
                initiator.getActorType().name(), testInteraction.getId(), testInteraction.getEntityType(),
                testInteraction.getInteractionType().name(), GrassrootRelationship.Type.GENERATOR);
        IncomingGraphAction graphAction = new IncomingGraphAction(id, actionType,
                Collections.singletonList(dataObject), Collections.singletonList(relationship), null);

        return incomingActionProcessor.processIncomingAction(graphAction).block();
    }

    @After
    public void cleanDb() {
        actorRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        eventRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        interactionRepository.deleteAll();
    }

}