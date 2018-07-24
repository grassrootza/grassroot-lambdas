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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static za.org.grassroot.graph.GraphApplicationTests.TEST_ENTITY_PREFIX;

@RunWith(SpringRunner.class) @Slf4j
@SpringBootTest(properties = {"sqs.pull.enabled=false","sqs.push.enabled=false"})
public class RelationshipTests {

    @Autowired IncomingActionProcessor incomingActionProcessor;

    @Autowired ActorRepository actorRepository;
    @Autowired EventRepository eventRepository;
    @Autowired InteractionRepository interactionRepository;

    @Test
    @Rollback
    public void addValidGenerationRelationships() {
        dispatchActorAction(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "generatorActor", ActionType.CREATE_ENTITY);
        dispatchActorAction(ActorType.GROUP, TEST_ENTITY_PREFIX + "group", ActionType.CREATE_ENTITY);
        dispatchEventAction(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        dispatchInteractionAction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);
        dispatchEventAction(EventType.MEETING, TEST_ENTITY_PREFIX + "generatorEvent", ActionType.CREATE_ENTITY);
        dispatchEventAction(EventType.VOTE, TEST_ENTITY_PREFIX + "vote", ActionType.CREATE_ENTITY);

        IncomingGraphAction action = new IncomingGraphAction(TEST_ENTITY_PREFIX + "generatorActor",
                ActionType.CREATE_RELATIONSHIP, null, new ArrayList<>(), null);
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "generatorActor",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), TEST_ENTITY_PREFIX + "group",
                GraphEntityType.ACTOR, ActorType.GROUP.name(), GrassrootRelationship.Type.GENERATOR));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "generatorActor",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), TEST_ENTITY_PREFIX + "meeting",
                GraphEntityType.EVENT, EventType.MEETING.name(), GrassrootRelationship.Type.GENERATOR));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "generatorActor",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), TEST_ENTITY_PREFIX + "survey",
                GraphEntityType.INTERACTION, InteractionType.SURVEY.name(), GrassrootRelationship.Type.GENERATOR));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "generatorEvent",
                GraphEntityType.EVENT, EventType.MEETING.name(), TEST_ENTITY_PREFIX + "vote",
                GraphEntityType.EVENT, EventType.VOTE.name(), GrassrootRelationship.Type.GENERATOR));
        incomingActionProcessor.processIncomingAction(action).block();

        Actor groupFromDB = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "group");
        Event meetingFromDB = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        Interaction surveyFromDB = interactionRepository.findById(TEST_ENTITY_PREFIX + "survey").orElse(null);
        Event voteFromDB = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "vote");
        assertThat(groupFromDB.getCreatedByActor(), is(notNullValue()));
        assertThat(meetingFromDB.getCreator(), is(notNullValue()));
        assertThat(surveyFromDB.getInitiator(), is(notNullValue()));
        assertThat(voteFromDB.getCreator(), is(notNullValue()));
    }

    @Test
    @Rollback
    public void addInvalidGenerationRelationships() {
        dispatchEventAction(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        dispatchActorAction(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "person", ActionType.CREATE_ENTITY);
        dispatchInteractionAction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);

        IncomingGraphAction action = new IncomingGraphAction(TEST_ENTITY_PREFIX + "meeting",
                ActionType.CREATE_RELATIONSHIP, null, new ArrayList<>(), null);
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "meeting",
                GraphEntityType.EVENT, EventType.MEETING.name(), TEST_ENTITY_PREFIX + "person",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), GrassrootRelationship.Type.GENERATOR));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "meeting",
                GraphEntityType.EVENT, EventType.MEETING.name(), TEST_ENTITY_PREFIX + "survey",
                GraphEntityType.INTERACTION, InteractionType.SURVEY.name(), GrassrootRelationship.Type.GENERATOR));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "survey",
                GraphEntityType.INTERACTION, InteractionType.SURVEY.name(), TEST_ENTITY_PREFIX + "person",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), GrassrootRelationship.Type.GENERATOR));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "survey",
                GraphEntityType.INTERACTION, InteractionType.SURVEY.name(), TEST_ENTITY_PREFIX + "meeting",
                GraphEntityType.EVENT, EventType.MEETING.name(), GrassrootRelationship.Type.GENERATOR));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "survey",
                GraphEntityType.INTERACTION, InteractionType.SURVEY.name(),TEST_ENTITY_PREFIX + "survey",
                GraphEntityType.INTERACTION, InteractionType.SURVEY.name(), GrassrootRelationship.Type.GENERATOR));
        incomingActionProcessor.processIncomingAction(action).block();

        Actor personFromDB = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "person");
        Event meetingFromDB = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        Interaction surveyFromDB = interactionRepository.findById(TEST_ENTITY_PREFIX + "survey").orElse(null);
        assertThat(personFromDB.getCreatedByActor(), is(nullValue()));
        assertThat(meetingFromDB.getCreator(), is(nullValue()));
        assertThat(surveyFromDB.getInitiator(), is(nullValue()));
    }

    @Test
    @Rollback
    public void addAndRemoveActorActorRelationships() {
        dispatchActorAction(ActorType.GROUP, TEST_ENTITY_PREFIX + "group", ActionType.CREATE_ENTITY);
        List<Actor> participatingActors = IntStream.range(0, 5).mapToObj(index ->
                new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "participant-" + index)).collect(Collectors.toList());

        List<IncomingDataObject> dataObjects = participatingActors.stream().map(a ->
                new IncomingDataObject(GraphEntityType.ACTOR, a)).collect(Collectors.toList());
        List<IncomingRelationship> relationships = participatingActors.stream().map(actor ->
                new IncomingRelationship(actor.getPlatformUid(), actor.getEntityType(), actor.getActorType().name(),
                        TEST_ENTITY_PREFIX + "group", GraphEntityType.ACTOR, ActorType.GROUP.name(),
                        GrassrootRelationship.Type.PARTICIPATES)).collect(Collectors.toList());

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "group",
                ActionType.CREATE_ENTITY, dataObjects, relationships, null)).block();
        verifyRelationshipPersistence(participatingActors, GraphEntityType.ACTOR);

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "group",
                ActionType.REMOVE_RELATIONSHIP, null, relationships, null)).block();
        verifyRelationshipRemoval(participatingActors, GraphEntityType.ACTOR);
    }

    @Test
    @Rollback
    public void addAndRemoveActorEventRelationships() {
        dispatchEventAction(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        List<Actor> participatingActors = IntStream.range(0, 5).mapToObj(index ->
                new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "participant-" + index)).collect(Collectors.toList());

        List<IncomingDataObject> dataObjects = participatingActors.stream().map(a ->
                new IncomingDataObject(GraphEntityType.ACTOR, a)).collect(Collectors.toList());
        List<IncomingRelationship> relationships = participatingActors.stream().map(actor ->
                new IncomingRelationship(actor.getPlatformUid(), actor.getEntityType(), actor.getActorType().name(),
                        TEST_ENTITY_PREFIX + "meeting", GraphEntityType.EVENT, EventType.MEETING.name(),
                        GrassrootRelationship.Type.PARTICIPATES)).collect(Collectors.toList());

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "meeting",
                ActionType.CREATE_ENTITY, dataObjects, relationships, null)).block();
        verifyRelationshipPersistence(participatingActors, GraphEntityType.EVENT);

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "meeting",
                ActionType.REMOVE_RELATIONSHIP, null, relationships, null)).block();
        verifyRelationshipRemoval(participatingActors, GraphEntityType.EVENT);
    }

    @Test
    @Rollback
    public void addAndRemoveActorInteractionRelationships() {
        dispatchInteractionAction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);
        List<Actor> participatingActors = IntStream.range(0, 5).mapToObj(index ->
                new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "participant-" + index)).collect(Collectors.toList());

        List<IncomingDataObject> dataObjects = participatingActors.stream().map(a ->
                new IncomingDataObject(GraphEntityType.ACTOR, a)).collect(Collectors.toList());
        List<IncomingRelationship> relationships = participatingActors.stream().map(actor ->
                new IncomingRelationship(actor.getPlatformUid(), actor.getEntityType(), actor.getActorType().name(),
                        TEST_ENTITY_PREFIX + "survey", GraphEntityType.INTERACTION, InteractionType.SURVEY.name(),
                        GrassrootRelationship.Type.PARTICIPATES)).collect(Collectors.toList());

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "survey",
                ActionType.CREATE_ENTITY, dataObjects, relationships, null)).block();
        verifyRelationshipPersistence(participatingActors, GraphEntityType.INTERACTION);

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "survey",
                ActionType.REMOVE_RELATIONSHIP, null, relationships, null)).block();
        verifyRelationshipRemoval(participatingActors, GraphEntityType.INTERACTION);
    }

    @Test
    @Rollback
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
        assertThat(!CollectionUtils.isEmpty(eventFromDb.getParticipatesIn()), is(true));
        assertThat(!CollectionUtils.isEmpty(eventFromDb.getChildEvents()), is(true));
    }

    @Test
    @Rollback
    public void checkDuplication() {
        log.info("Creating interaction with 25 participating actors.");
        dispatchInteractionAction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);
        List<Actor> participatingActors = IntStream.range(0, 25).mapToObj(index ->
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

    @Test
    @Rollback
    public void addInvalidParticipationRelationships() {
        dispatchEventAction(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        dispatchActorAction(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "person", ActionType.CREATE_ENTITY);
        dispatchInteractionAction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);

        IncomingGraphAction action = new IncomingGraphAction(TEST_ENTITY_PREFIX + "meeting",
                ActionType.CREATE_RELATIONSHIP, null, new ArrayList<>(), null);
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "meeting",
                GraphEntityType.EVENT, EventType.MEETING.name(), TEST_ENTITY_PREFIX + "meeting",
                GraphEntityType.EVENT, EventType.MEETING.name(), GrassrootRelationship.Type.PARTICIPATES));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "meeting",
                GraphEntityType.EVENT, EventType.MEETING.name(), TEST_ENTITY_PREFIX + "survey",
                GraphEntityType.INTERACTION, InteractionType.SURVEY.name(), GrassrootRelationship.Type.PARTICIPATES));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "survey",
                GraphEntityType.INTERACTION, InteractionType.SURVEY.name(), TEST_ENTITY_PREFIX + "person",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), GrassrootRelationship.Type.PARTICIPATES));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "survey",
                GraphEntityType.INTERACTION, InteractionType.SURVEY.name(), TEST_ENTITY_PREFIX + "meeting",
                GraphEntityType.EVENT, EventType.MEETING.name(), GrassrootRelationship.Type.PARTICIPATES));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "survey",
                GraphEntityType.INTERACTION, InteractionType.SURVEY.name(),TEST_ENTITY_PREFIX + "survey",
                GraphEntityType.INTERACTION, InteractionType.SURVEY.name(), GrassrootRelationship.Type.PARTICIPATES));
        incomingActionProcessor.processIncomingAction(action).block();

        Event meetingFromDB = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        assertThat(CollectionUtils.isEmpty(meetingFromDB.getParticipatesIn()), is(true));
    }

    private void dispatchActorAction(ActorType actorType, String platformId, ActionType actionType) {
        Actor testActor = new Actor(actorType, platformId);
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.ACTOR, testActor);
        IncomingGraphAction graphAction = new IncomingGraphAction(platformId, actionType,
                Collections.singletonList(dataObject), null, null);

        incomingActionProcessor.processIncomingAction(graphAction).block();
    }

    private void dispatchEventAction(EventType eventType, String platformId, ActionType actionType) {
        Event testEvent = new Event(eventType, platformId, Instant.now().toEpochMilli());
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.EVENT, testEvent);
        IncomingGraphAction graphAction = new IncomingGraphAction(platformId, actionType,
                Collections.singletonList(dataObject), null,null);

        incomingActionProcessor.processIncomingAction(graphAction).block();
    }

    private void dispatchInteractionAction(InteractionType interactionType, String id, ActionType actionType) {
        Interaction testInteraction = new Interaction();
        testInteraction.setId(id);
        testInteraction.setInteractionType(interactionType);
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.INTERACTION, testInteraction);
        IncomingGraphAction graphAction = new IncomingGraphAction(id, actionType,
                Collections.singletonList(dataObject), null, null);

        incomingActionProcessor.processIncomingAction(graphAction).block();
    }

    private void verifyRelationshipPersistence(List<Actor> participants, GraphEntityType headEntityType) {
        List<Actor> actorsFromDB = participants.stream().map(actor ->
                actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
        assertThat(actorsFromDB.size(), is(participants.size()));

        boolean relationshipsPersisted = false;
        switch (headEntityType) {
            case ACTOR:         relationshipsPersisted = actorsFromDB.stream().map(a ->
                    !CollectionUtils.isEmpty(a.getParticipatesInActors())).reduce(true, (a, b) -> a && b); break;
            case EVENT:         relationshipsPersisted = actorsFromDB.stream().map(a ->
                    !CollectionUtils.isEmpty(a.getParticipatesInEvents())).reduce(true, (a, b) -> a && b); break;
            case INTERACTION:   relationshipsPersisted = actorsFromDB.stream().map(a ->
                    !CollectionUtils.isEmpty(a.getParticipatesInInteractions())).reduce(true, (a, b) -> a && b); break;
        }
        assertThat(relationshipsPersisted, is(true));
    }

    private void verifyRelationshipRemoval(List<Actor> participants, GraphEntityType headEntityType) {
        List<Actor> actorsFromDB = participants.stream().map(actor ->
                actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
        assertThat(actorsFromDB.size(), is(participants.size()));

        boolean relationshipsRemoved = false;
        switch (headEntityType) {
            case ACTOR:         relationshipsRemoved = actorsFromDB.stream().map(a ->
                    CollectionUtils.isEmpty(a.getParticipatesInActors())).reduce(true, (a, b) -> a && b); break;
            case EVENT:         relationshipsRemoved = actorsFromDB.stream().map(a ->
                    CollectionUtils.isEmpty(a.getParticipatesInEvents())).reduce(true, (a, b) -> a && b); break;
            case INTERACTION:   relationshipsRemoved = actorsFromDB.stream().map(a ->
                    CollectionUtils.isEmpty(a.getParticipatesInInteractions())).reduce(true, (a, b) -> a && b); break;
        }
        assertThat(relationshipsRemoved, is(true));
    }

    @After
    public void cleanDb() {
        actorRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        eventRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        interactionRepository.deleteAll();
    }

}