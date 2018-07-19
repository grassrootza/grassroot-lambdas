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
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.Interaction;
import za.org.grassroot.graph.domain.enums.*;
import za.org.grassroot.graph.domain.relationship.ActorInActor;
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
    public void addActor() {
        addActorViaIncoming(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "individual");
        Actor individualFromDB = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "individual");
        assertThat(individualFromDB, notNullValue());
    }

    @Test @Rollback
    public void addEvent() {
        addEventViaIncoming(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting");
        Event meetingFromDB = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        assertThat(meetingFromDB, notNullValue());
        assertThat(meetingFromDB.getCreator().getPlatformUid(), is(TEST_ENTITY_PREFIX + "creator"));
    }

    @Test @Rollback
    public void addInteraction() {
        addInteractionViaIncoming(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey");
        Interaction surveyFromDB = interactionRepository.findById(TEST_ENTITY_PREFIX + "survey").orElse(null);
        assertThat(surveyFromDB, notNullValue());
        assertThat(surveyFromDB.getInitiator().getPlatformUid(), is(TEST_ENTITY_PREFIX + "initiator"));
    }

    @Test @Rollback
    public void addActorActorRelationships() {
        addActorViaIncoming(ActorType.GROUP, TEST_ENTITY_PREFIX + "group");
        List<Actor> participatingActors = IntStream.range(0, 10).mapToObj(index ->
                new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "participant-" + index)).collect(Collectors.toList());

        List<IncomingDataObject> dataObjects = participatingActors.stream().map(a ->
                new IncomingDataObject(GraphEntityType.ACTOR, a)).collect(Collectors.toList());
        List<IncomingRelationship> relationships = participatingActors.stream().map(actor ->
                new IncomingRelationship(actor.getPlatformUid(), actor.getEntityType(), actor.getActorType().name(),
                        TEST_ENTITY_PREFIX + "group", GraphEntityType.ACTOR, ActorType.GROUP.name(),
                        GrassrootRelationship.Type.PARTICIPATES)).collect(Collectors.toList());

        // process relationships and verify persistence

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "group",
                ActionType.CREATE_ENTITY, dataObjects, relationships, null)).block();

        List<Actor> actorsFromDB = participatingActors.stream().map(actor ->
                actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
        assertThat(actorsFromDB.size(), is(10));

        boolean relationshipsPersisted = actorsFromDB.stream().map(actor ->
                !CollectionUtils.isEmpty(actor.getParticipatesInActors())).reduce(true, (a, b) -> a && b);
        assertThat(relationshipsPersisted, is(true));
    }

    @Test @Rollback
    public void addActorEventRelationship() {
        List<IncomingDataObject> dataObjects = new ArrayList<>();
        List<IncomingRelationship> relationships = new ArrayList<>();

        Event graphEvent = new Event(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", Instant.now().toEpochMilli());
        Actor graphParent = new Actor(ActorType.GROUP, TEST_ENTITY_PREFIX + "parent-group");
        List<Actor> participatingActors = IntStream.range(0, 10).mapToObj(index ->
                new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "participant-" + index)).collect(Collectors.toList());

        dataObjects.addAll(participatingActors.stream().map(a ->
                new IncomingDataObject(GraphEntityType.ACTOR, a)).collect(Collectors.toList()));
        dataObjects.add(new IncomingDataObject(GraphEntityType.EVENT, graphEvent));
        dataObjects.add(new IncomingDataObject(GraphEntityType.ACTOR, graphParent));

        relationships.addAll(participatingActors.stream().map(actor ->
                new IncomingRelationship(actor.getPlatformUid(), GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                        graphEvent.getPlatformUid(), GraphEntityType.EVENT, EventType.MEETING.name(),
                        GrassrootRelationship.Type.PARTICIPATES)).collect(Collectors.toList()));
        relationships.add(new IncomingRelationship(graphEvent.getPlatformUid(), GraphEntityType.EVENT,
                EventType.MEETING.name(), graphParent.getPlatformUid(), GraphEntityType.ACTOR, ActorType.GROUP.name(),
                GrassrootRelationship.Type.PARTICIPATES));

        // process relationships and verify persistence

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "creating-user",
                ActionType.CREATE_ENTITY, dataObjects, relationships, null)).block();

        List<Actor> actorsFromDB = participatingActors.stream().map(actor ->
                actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
        assertThat(actorsFromDB.size(), is(10));

        boolean relationshipsPersisted = actorsFromDB.stream().map(actor ->
                !CollectionUtils.isEmpty(actor.getParticipatesInEvents())).reduce(true, (a, b) -> a && b);
        assertThat(relationshipsPersisted, is(true));

        Event eventFromDb = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        assertThat(eventFromDb, notNullValue());

        Set<Actor> parents = eventFromDb.getParticipatesIn();
        assertThat(CollectionUtils.isEmpty(parents), is(false));
        assertThat(parents, contains(graphParent));
    }

    @Test @Rollback
    public void addActorInteractionRelationship() {
        addInteractionViaIncoming(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey");
        List<Actor> participatingActors = IntStream.range(0, 10).mapToObj(index ->
                new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "participant-" + index)).collect(Collectors.toList());

        List<IncomingDataObject> dataObjects = participatingActors.stream().map(a ->
                new IncomingDataObject(GraphEntityType.ACTOR, a)).collect(Collectors.toList());
        List<IncomingRelationship> relationships = participatingActors.stream().map(actor ->
                new IncomingRelationship(actor.getPlatformUid(), actor.getEntityType(), actor.getActorType().name(),
                        TEST_ENTITY_PREFIX + "survey", GraphEntityType.INTERACTION, InteractionType.SURVEY.name(),
                        GrassrootRelationship.Type.PARTICIPATES)).collect(Collectors.toList());

        // process relationships and verify persistence

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "survey",
                ActionType.CREATE_ENTITY, dataObjects, relationships, null)).block();

        List<Actor> actorsFromDB = participatingActors.stream().map(actor ->
                actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
        assertThat(actorsFromDB.size(), is(10));

        boolean relationshipsPersisted = actorsFromDB.stream().map(actor ->
                !CollectionUtils.isEmpty(actor.getParticipatesInInteractions())).reduce(true, (a, b) -> a && b);
        assertThat(relationshipsPersisted, is(true));
    }

    @Test @Rollback
    public void checkDuplication() {
        log.info("Creating interaction with 500 participating actors.");
        List<IncomingDataObject> graphDataObjects = new ArrayList<>();
        List<IncomingRelationship> graphRelationships = new ArrayList<>();

        Actor initiator = new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "creating-user");
        Interaction interaction = new Interaction(InteractionType.SURVEY, initiator);
        interaction.setId(TEST_ENTITY_PREFIX + "survey");
        List<Actor> participatingActors = IntStream.range(0, 50).mapToObj(index ->
                new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "participant-" + index)).collect(Collectors.toList());

        graphDataObjects.add(new IncomingDataObject(GraphEntityType.ACTOR, initiator));
        graphDataObjects.add(new IncomingDataObject(GraphEntityType.INTERACTION, interaction));
        graphDataObjects.addAll(participatingActors.stream().map(a ->
                new IncomingDataObject(GraphEntityType.ACTOR, a)).collect(Collectors.toList()));

        graphRelationships.addAll(participatingActors.stream().map(actor ->
                new IncomingRelationship(actor.getPlatformUid(), GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                        interaction.getId(), GraphEntityType.INTERACTION, InteractionType.SURVEY.name(),
                        GrassrootRelationship.Type.PARTICIPATES)).collect(Collectors.toList()));
        graphRelationships.add(new IncomingRelationship(initiator.getPlatformUid(), GraphEntityType.ACTOR,
                ActorType.INDIVIDUAL.name(), interaction.getId(), GraphEntityType.INTERACTION,
                InteractionType.SURVEY.name(), GrassrootRelationship.Type.GENERATOR));

        IncomingGraphAction graphAction = new IncomingGraphAction(TEST_ENTITY_PREFIX + "creating-user",
                ActionType.CREATE_ENTITY, graphDataObjects, graphRelationships, null);
        incomingActionProcessor.processIncomingAction(graphAction).block();

        log.info("Now adding one actor to interaction, verify existing relationships are not persisted again");
        Actor newParticipant = new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "new-user");
        IncomingDataObject participant = new IncomingDataObject(GraphEntityType.ACTOR, newParticipant);
        IncomingRelationship participation = new IncomingRelationship(newParticipant.getPlatformUid(),
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), interaction.getId(), GraphEntityType.INTERACTION,
                InteractionType.SURVEY.name(), GrassrootRelationship.Type.PARTICIPATES);

        IncomingGraphAction action = new IncomingGraphAction(TEST_ENTITY_PREFIX + "new-user", ActionType.CREATE_ENTITY,
                Collections.singletonList(participant), Collections.singletonList(participation), null);
        incomingActionProcessor.processIncomingAction(action).block();
    }

    @After
    public void cleanDb() {
        actorRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        eventRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        interactionRepository.deleteByIdContaining(TEST_ENTITY_PREFIX);
    }

    private void addActorViaIncoming(ActorType actorType, String platformId) {
        Actor testActor = new Actor(actorType, platformId);
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.ACTOR, testActor);
        IncomingGraphAction graphAction = new IncomingGraphAction(platformId, ActionType.CREATE_ENTITY,
                Collections.singletonList(dataObject), null, null);

        incomingActionProcessor.processIncomingAction(graphAction).block();
    }

    private void addEventViaIncoming(EventType eventType, String platformId) {
        Actor creator = new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "creator");
        addActorViaIncoming(creator.getActorType(), creator.getPlatformUid());

        Event testEvent = new Event(eventType, platformId, Instant.now().toEpochMilli());
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.EVENT, testEvent);
        IncomingRelationship relationship = new IncomingRelationship(creator.getPlatformUid(), creator.getEntityType(),
                creator.getActorType().name(), testEvent.getPlatformUid(), testEvent.getEntityType(),
                testEvent.getEventType().name(), GrassrootRelationship.Type.GENERATOR);
        IncomingGraphAction graphAction = new IncomingGraphAction(platformId, ActionType.CREATE_ENTITY,
                Collections.singletonList(dataObject), Collections.singletonList(relationship), null);

        incomingActionProcessor.processIncomingAction(graphAction).block();
    }

    private void addInteractionViaIncoming(InteractionType interactionType, String id) {
        Actor initiator = new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "initiator");
        addActorViaIncoming(initiator.getActorType(), initiator.getPlatformUid());

        Interaction testInteraction = new Interaction(interactionType, initiator);
        testInteraction.setId(id);
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.INTERACTION, testInteraction);
        IncomingRelationship relationship = new IncomingRelationship(initiator.getPlatformUid(), initiator.getEntityType(),
                initiator.getActorType().name(), testInteraction.getId(), testInteraction.getEntityType(),
                testInteraction.getInteractionType().name(), GrassrootRelationship.Type.GENERATOR);
        IncomingGraphAction graphAction = new IncomingGraphAction(id, ActionType.CREATE_ENTITY,
                Collections.singletonList(dataObject), Collections.singletonList(relationship), null);

        incomingActionProcessor.processIncomingAction(graphAction).block();
    }

}