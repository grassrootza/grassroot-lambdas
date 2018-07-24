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

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static za.org.grassroot.graph.GraphApplicationTests.TEST_ENTITY_PREFIX;
import static za.org.grassroot.graph.TestUtils.wrapActorAction;
import static za.org.grassroot.graph.TestUtils.wrapEventAction;
import static za.org.grassroot.graph.TestUtils.wrapInteractionAction;

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
        dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "individual", ActionType.CREATE_ENTITY);
        Actor individualFromDB = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "individual");
        assertThat(individualFromDB, notNullValue());

        // try adding when actor already exists.
        dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "individual", ActionType.CREATE_ENTITY);
        assertThat(actorRepository.count(), is(1L));
        Actor individualFromDB2 = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "individual");
        assertThat(individualFromDB2.getCreationTime(), is(individualFromDB.getCreationTime()));

        dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "individual", ActionType.REMOVE_ENTITY);
        Actor individualFromDB3 = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "individual");
        assertThat(individualFromDB3, nullValue());

        // try removing after actor already removed.
        boolean removalSuccessful = dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "individual", ActionType.REMOVE_ENTITY);
        assertThat(removalSuccessful, is(false));
    }

    @Test
    @Rollback
    public void addAndRemoveEvent() {
        dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        addGenerator(TEST_ENTITY_PREFIX + "meeting", GraphEntityType.EVENT, EventType.MEETING.name());
        Event meetingFromDB = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        assertThat(meetingFromDB, notNullValue());
        assertThat(meetingFromDB.getCreator().getPlatformUid(), is(TEST_ENTITY_PREFIX + "creator"));

        // try adding when event already exists
        dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        assertThat(eventRepository.count(), is(1L));
        Event meetingFromDB2 = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        assertThat(meetingFromDB2.getCreationTime(), is(meetingFromDB.getCreationTime()));

        dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.REMOVE_ENTITY);
        Event meetingFromDB3 = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        assertThat(meetingFromDB3, nullValue());

        // try removing after event already removed
        boolean removalSuccessful = dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.REMOVE_ENTITY);
        assertThat(removalSuccessful, is(false));
    }

    @Test
    @Rollback
    public void addAndRemoveInteraction() {
        dispatchInteraction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);
        addGenerator(TEST_ENTITY_PREFIX + "survey", GraphEntityType.INTERACTION, InteractionType.SURVEY.name());
        Interaction surveyFromDB = interactionRepository.findById(TEST_ENTITY_PREFIX + "survey").orElse(null);
        assertThat(surveyFromDB, notNullValue());
        assertThat(surveyFromDB.getInitiator().getPlatformUid(), is(TEST_ENTITY_PREFIX + "creator"));

        // try adding when interaction already exists
        dispatchInteraction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);
        assertThat(interactionRepository.count(), is(1L));
        Interaction surveyFromDB2 = interactionRepository.findById(TEST_ENTITY_PREFIX + "survey").orElse(null);
        assertThat(surveyFromDB2.getCreationTime(), is(surveyFromDB.getCreationTime()));

        dispatchInteraction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.REMOVE_ENTITY);
        Interaction surveyFromDB3 = interactionRepository.findById(TEST_ENTITY_PREFIX + "survey").orElse(null);
        assertThat(surveyFromDB3, nullValue());

        // try removing after interaction already removed
        boolean removalSuccessful = dispatchInteraction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.REMOVE_ENTITY);
        assertThat(removalSuccessful, is(false));
    }

    private boolean dispatchActor(ActorType actorType, String platformId, ActionType actionType) {
        return incomingActionProcessor.processIncomingAction(wrapActorAction(actorType, platformId, actionType)).block();
    }

    private boolean dispatchEvent(EventType eventType, String platformId, ActionType actionType) {
        return incomingActionProcessor.processIncomingAction(wrapEventAction(eventType, platformId, actionType)).block();
    }

    private boolean dispatchInteraction(InteractionType interactionType, String id, ActionType actionType) {
        return incomingActionProcessor.processIncomingAction(wrapInteractionAction(interactionType, id, actionType)).block();
    }

    private void addGenerator(String targetId, GraphEntityType targetType, String targetSubType) {
        Actor creator = new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "creator");
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.ACTOR, creator);
        IncomingRelationship relationship = new IncomingRelationship(creator.getPlatformUid(), creator.getEntityType(),
                creator.getActorType().name(), targetId, targetType, targetSubType, GrassrootRelationship.Type.GENERATOR);
        IncomingGraphAction graphAction = new IncomingGraphAction(creator.getPlatformUid(), ActionType.CREATE_ENTITY,
                Collections.singletonList(dataObject), Collections.singletonList(relationship), null);
        incomingActionProcessor.processIncomingAction(graphAction).block();
    }

    @After
    public void cleanDb() {
        actorRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        eventRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        interactionRepository.deleteAll();
    }

}