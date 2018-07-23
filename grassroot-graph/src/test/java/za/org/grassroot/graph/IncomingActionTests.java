package za.org.grassroot.graph;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.CollectionUtils;
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

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static za.org.grassroot.graph.GraphApplicationTests.TEST_ENTITY_PREFIX;

@RunWith(SpringRunner.class) @Slf4j
@SpringBootTest(properties = {"sqs.pull.enabled=false","sqs.push.enabled=false"})
public class IncomingActionTests {

    @Autowired IncomingActionProcessor incomingActionProcessor;

    @Autowired ActorRepository actorRepository;
    @Autowired EventRepository eventRepository;
    @Autowired InteractionRepository interactionRepository;

    @Test @Rollback
    public void addAndRemoveActor() {
        dispatchActorAction(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "individual", ActionType.CREATE_ENTITY);
        Actor individualFromDB = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "individual");
        assertThat(individualFromDB, notNullValue());

        dispatchActorAction(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "individual", ActionType.REMOVE_ENTITY);
        Actor individualFromDB2 = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "individual");
        assertThat(individualFromDB2, nullValue());
    }

    @Test @Rollback
    public void addAndRemoveEvent() {
        dispatchEventAction(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        Event meetingFromDB = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        assertThat(meetingFromDB, notNullValue());
        assertThat(meetingFromDB.getCreator().getPlatformUid(), is(TEST_ENTITY_PREFIX + "creator"));

        dispatchEventAction(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.REMOVE_ENTITY);
        Event meetingFromDB2 = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        assertThat(meetingFromDB2, nullValue());
    }

    @Test @Rollback
    public void addAndRemoveInteraction() {
        dispatchInteractionAction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);
        Interaction surveyFromDB = interactionRepository.findById(TEST_ENTITY_PREFIX + "survey").orElse(null);
        assertThat(surveyFromDB, notNullValue());
        assertThat(surveyFromDB.getInitiator().getPlatformUid(), is(TEST_ENTITY_PREFIX + "initiator"));

        dispatchInteractionAction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.REMOVE_ENTITY);
        Interaction surveyFromDB2 = interactionRepository.findById(TEST_ENTITY_PREFIX + "survey").orElse(null);
        assertThat(surveyFromDB2, nullValue());
    }

    @Test @Rollback
    public void addAndRemoveActorActorRelationships() {
        dispatchActorAction(ActorType.GROUP, TEST_ENTITY_PREFIX + "group", ActionType.CREATE_ENTITY);
        List<Actor> participatingActors = IntStream.range(0, 10).mapToObj(index ->
                new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "participant-" + index)).collect(Collectors.toList());

        List<IncomingDataObject> dataObjects = participatingActors.stream().map(a ->
                new IncomingDataObject(GraphEntityType.ACTOR, a)).collect(Collectors.toList());
        List<IncomingRelationship> relationships = participatingActors.stream().map(actor ->
                new IncomingRelationship(actor.getPlatformUid(), actor.getEntityType(), actor.getActorType().name(),
                        TEST_ENTITY_PREFIX + "group", GraphEntityType.ACTOR, ActorType.GROUP.name(),
                        GrassrootRelationship.Type.PARTICIPATES)).collect(Collectors.toList());

        // add relationships

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "group",
                ActionType.CREATE_ENTITY, dataObjects, relationships, null)).block();

        List<Actor> actorsFromDB = participatingActors.stream().map(actor ->
                actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
        assertThat(actorsFromDB.size(), is(10));

        boolean relationshipsPersisted = actorsFromDB.stream().map(actor ->
                !CollectionUtils.isEmpty(actor.getParticipatesInActors())).reduce(true, (a, b) -> a && b);
        assertThat(relationshipsPersisted, is(true));

        // remove relationships

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "group",
                ActionType.REMOVE_RELATIONSHIP, null, relationships, null)).block();

        List<Actor> actorsFromDB2 = participatingActors.stream().map(actor ->
                actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
        assertThat(actorsFromDB2.size(), is(10));

        boolean relationshipsDeleted = actorsFromDB2.stream().map(actor ->
                CollectionUtils.isEmpty(actor.getParticipatesInActors())).reduce(true, (a, b) -> a && b);
        assertThat(relationshipsDeleted, is(true));
    }

    @Test @Rollback
    public void addAndRemoveActorEventRelationship() {
        dispatchEventAction(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        List<Actor> participatingActors = IntStream.range(0, 10).mapToObj(index ->
                new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "participant-" + index)).collect(Collectors.toList());

        List<IncomingDataObject> dataObjects = participatingActors.stream().map(a ->
                new IncomingDataObject(GraphEntityType.ACTOR, a)).collect(Collectors.toList());
        List<IncomingRelationship> relationships = participatingActors.stream().map(actor ->
                new IncomingRelationship(actor.getPlatformUid(), actor.getEntityType(), actor.getActorType().name(),
                        TEST_ENTITY_PREFIX + "meeting", GraphEntityType.EVENT, EventType.MEETING.name(),
                        GrassrootRelationship.Type.PARTICIPATES)).collect(Collectors.toList());

        // add relationships

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "meeting",
                ActionType.CREATE_ENTITY, dataObjects, relationships, null)).block();

        List<Actor> actorsFromDB = participatingActors.stream().map(actor ->
                actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
        assertThat(actorsFromDB.size(), is(10));

        boolean relationshipsPersisted = actorsFromDB.stream().map(actor ->
                !CollectionUtils.isEmpty(actor.getParticipatesInEvents())).reduce(true, (a, b) -> a && b);
        assertThat(relationshipsPersisted, is(true));

        // remove relationships

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "meeting",
                ActionType.REMOVE_RELATIONSHIP, null, relationships, null)).block();

        List<Actor> actorsFromDB2 = participatingActors.stream().map(actor ->
                actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
        assertThat(actorsFromDB2.size(), is(10));

        boolean relationshipsDeleted = actorsFromDB2.stream().map(actor ->
                CollectionUtils.isEmpty(actor.getParticipatesInEvents())).reduce(true, (a, b) -> a && b);
        assertThat(relationshipsDeleted, is(true));
    }

    @Test @Rollback
    public void addAndRemoveActorInteractionRelationship() {
        dispatchInteractionAction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);
        List<Actor> participatingActors = IntStream.range(0, 10).mapToObj(index ->
                new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "participant-" + index)).collect(Collectors.toList());

        List<IncomingDataObject> dataObjects = participatingActors.stream().map(a ->
                new IncomingDataObject(GraphEntityType.ACTOR, a)).collect(Collectors.toList());
        List<IncomingRelationship> relationships = participatingActors.stream().map(actor ->
                new IncomingRelationship(actor.getPlatformUid(), actor.getEntityType(), actor.getActorType().name(),
                        TEST_ENTITY_PREFIX + "survey", GraphEntityType.INTERACTION, InteractionType.SURVEY.name(),
                        GrassrootRelationship.Type.PARTICIPATES)).collect(Collectors.toList());

        // add relationships

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "survey",
                ActionType.CREATE_ENTITY, dataObjects, relationships, null)).block();

        List<Actor> actorsFromDB = participatingActors.stream().map(actor ->
                actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
        assertThat(actorsFromDB.size(), is(10));

        boolean relationshipsPersisted = actorsFromDB.stream().map(actor ->
                !CollectionUtils.isEmpty(actor.getParticipatesInInteractions())).reduce(true, (a, b) -> a && b);
        assertThat(relationshipsPersisted, is(true));

        // remove relationships

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "survey",
                ActionType.REMOVE_RELATIONSHIP, null, relationships, null)).block();

        List<Actor> actorsFromDB2 = participatingActors.stream().map(actor ->
                actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
        assertThat(actorsFromDB2.size(), is(10));

        boolean relationshipsDeleted = actorsFromDB2.stream().map(actor ->
                CollectionUtils.isEmpty(actor.getParticipatesInInteractions())).reduce(true, (a, b) -> a && b);
        assertThat(relationshipsDeleted, is(true));
    }

    @Test @Rollback
    public void addEventRelationships() {
        dispatchEventAction(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        dispatchActorAction(ActorType.GROUP, TEST_ENTITY_PREFIX + "graphParent", ActionType.CREATE_ENTITY);
        dispatchEventAction(EventType.VOTE, TEST_ENTITY_PREFIX + "childEvent", ActionType.CREATE_ENTITY);

        IncomingGraphAction action = new IncomingGraphAction(TEST_ENTITY_PREFIX + "meeting",
                ActionType.CREATE_RELATIONSHIP, null, new ArrayList<>(), null);

        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "meeting",
                GraphEntityType.EVENT, EventType.MEETING.name(), TEST_ENTITY_PREFIX + "graphParent",
                GraphEntityType.ACTOR, ActorType.GROUP.name(), GrassrootRelationship.Type.PARTICIPATES));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "meeting",
                GraphEntityType.EVENT, EventType.MEETING.name(), TEST_ENTITY_PREFIX + "childEvent",
                GraphEntityType.EVENT, EventType.VOTE.name(), GrassrootRelationship.Type.GENERATOR));

        incomingActionProcessor.processIncomingAction(action).block();

        Event eventFromDb = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        assertThat(eventFromDb, notNullValue());
        assertThat(CollectionUtils.isEmpty(eventFromDb.getParticipatesIn()), is(false));
        assertThat(CollectionUtils.isEmpty(eventFromDb.getChildEvents()), is(false));
    }

    @Test @Rollback
    public void checkDuplication() {
        log.info("Creating interaction with 50 participating actors.");
        dispatchInteractionAction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);
        List<Actor> participatingActors = IntStream.range(0, 50).mapToObj(index ->
                new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "participant-" + index)).collect(Collectors.toList());

        List<IncomingDataObject> dataObjects = participatingActors.stream().map(a ->
                new IncomingDataObject(GraphEntityType.ACTOR, a)).collect(Collectors.toList());
        List<IncomingRelationship> relationships = participatingActors.stream().map(actor ->
                new IncomingRelationship(actor.getPlatformUid(), actor.getEntityType(), actor.getActorType().name(),
                        TEST_ENTITY_PREFIX + "survey", GraphEntityType.INTERACTION, InteractionType.SURVEY.name(),
                        GrassrootRelationship.Type.PARTICIPATES)).collect(Collectors.toList());

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "survey",
                ActionType.CREATE_ENTITY, dataObjects, relationships, null)).block();

        log.info("Now adding one actor to interaction, verify existing relationships are not persisted again");
        dispatchActorAction(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "new-user", ActionType.CREATE_ENTITY);
        IncomingRelationship participation = new IncomingRelationship(TEST_ENTITY_PREFIX + "new-user",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), TEST_ENTITY_PREFIX + "survey",
                GraphEntityType.INTERACTION, InteractionType.SURVEY.name(), GrassrootRelationship.Type.PARTICIPATES);
        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "new-user",
                ActionType.CREATE_RELATIONSHIP, null, Collections.singletonList(participation), null)).block();
    }

    @After
    public void cleanDb() {
        actorRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        eventRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        interactionRepository.deleteByIdContaining(TEST_ENTITY_PREFIX);
    }

    private void dispatchActorAction(ActorType actorType, String platformId, ActionType actionType) {
        Actor testActor = new Actor(actorType, platformId);
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.ACTOR, testActor);
        IncomingGraphAction graphAction = new IncomingGraphAction(platformId, actionType,
                Collections.singletonList(dataObject), null, null);

        incomingActionProcessor.processIncomingAction(graphAction).block();
    }

    private void dispatchEventAction(EventType eventType, String platformId, ActionType actionType) {
        Actor creator = new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "creator");
        dispatchActorAction(creator.getActorType(), creator.getPlatformUid(), ActionType.CREATE_ENTITY);

        Event testEvent = new Event(eventType, platformId, Instant.now().toEpochMilli());
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.EVENT, testEvent);
        IncomingRelationship relationship = new IncomingRelationship(creator.getPlatformUid(), creator.getEntityType(),
                creator.getActorType().name(), testEvent.getPlatformUid(), testEvent.getEntityType(),
                testEvent.getEventType().name(), GrassrootRelationship.Type.GENERATOR);
        IncomingGraphAction graphAction = new IncomingGraphAction(platformId, actionType,
                Collections.singletonList(dataObject), Collections.singletonList(relationship), null);

        incomingActionProcessor.processIncomingAction(graphAction).block();
    }

    private void dispatchInteractionAction(InteractionType interactionType, String id, ActionType actionType) {
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

        incomingActionProcessor.processIncomingAction(graphAction).block();
    }

}