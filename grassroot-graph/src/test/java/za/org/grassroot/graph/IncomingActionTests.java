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

    private void addActorViaIncoming(ActorType actorType, String platformId) {
        Actor testActor = new Actor(actorType, platformId);
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.ACTOR, testActor);
        IncomingGraphAction graphAction = new IncomingGraphAction(platformId, ActionType.CREATE_ENTITY,
                Collections.singletonList(dataObject), null, null);

        incomingActionProcessor.processIncomingAction(graphAction).block();
    }

    @Test
    @Rollback
    public void addsAnActorToDb() {
        addActorViaIncoming(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "actor");
        Actor actorCheckDb = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "actor");
        assertThat(actorCheckDb, notNullValue());
    }

    @Test
    @Rollback
    public void addsAnActorAndGroup() {
        addActorViaIncoming(ActorType.GROUP, TEST_ENTITY_PREFIX + "group");
        addActorViaIncoming(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "person");

        assertThat(actorRepository.count(), greaterThanOrEqualTo(2L));
    }

    @Test
    @Rollback
    public void addsActorGroupAndRelationship() {
        addActorViaIncoming(ActorType.GROUP, TEST_ENTITY_PREFIX + "group");
        addActorViaIncoming(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "person");

        IncomingRelationship relationship = new IncomingRelationship(TEST_ENTITY_PREFIX + "person",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), TEST_ENTITY_PREFIX + "group",
                GraphEntityType.ACTOR, ActorType.GROUP.name(), GrassrootRelationship.Type.PARTICIPATES);

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "person",
                ActionType.CREATE_RELATIONSHIP, null, Collections.singletonList(relationship), null)).block();

        assertThat(actorRepository.count(), greaterThanOrEqualTo(2L));
        Actor checkActor = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "person");
        assertThat(checkActor.getParticipatesInActors(), notNullValue());
        assertThat(checkActor.getParticipatesInActors().size(), is(1));
    }

    @Test
    @Rollback
    public void addsTaskAndRelationships() {
        List<IncomingDataObject> graphDataObjects = new ArrayList<>();
        List<IncomingRelationship> graphRelationships = new ArrayList<>();

        Event graphEvent = new Event(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", Instant.now().toEpochMilli());
        Actor graphParent = new Actor(ActorType.GROUP, TEST_ENTITY_PREFIX + "parent-group");
        Actor creatingUser = new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "creating-user");
        List<Actor> participatingActors = IntStream.range(0, 10).mapToObj(index ->
                new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "participant-" + index)).collect(Collectors.toList());

        graphDataObjects.addAll(participatingActors.stream().map(a ->
                new IncomingDataObject(GraphEntityType.ACTOR, a)).collect(Collectors.toList()));
        graphDataObjects.add(new IncomingDataObject(GraphEntityType.EVENT, graphEvent));
        graphDataObjects.add(new IncomingDataObject(GraphEntityType.ACTOR, graphParent));
        graphDataObjects.add(new IncomingDataObject(GraphEntityType.ACTOR, creatingUser));

        graphRelationships.addAll(participatingActors.stream().map(actor ->
                new IncomingRelationship(actor.getPlatformUid(), GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                        graphEvent.getPlatformUid(), GraphEntityType.EVENT, EventType.MEETING.name(),
                        GrassrootRelationship.Type.PARTICIPATES)).collect(Collectors.toList()));
        graphRelationships.add(new IncomingRelationship(graphEvent.getPlatformUid(), GraphEntityType.EVENT,
                EventType.MEETING.name(), graphParent.getPlatformUid(), GraphEntityType.ACTOR, ActorType.GROUP.name(),
                GrassrootRelationship.Type.PARTICIPATES));
        graphRelationships.add(new IncomingRelationship(creatingUser.getPlatformUid(), GraphEntityType.ACTOR,
                ActorType.INDIVIDUAL.name(), graphEvent.getPlatformUid(), GraphEntityType.EVENT, EventType.MEETING.name(),
                GrassrootRelationship.Type.GENERATOR));

        IncomingGraphAction graphAction = new IncomingGraphAction(TEST_ENTITY_PREFIX + "creating-user",
                ActionType.CREATE_ENTITY, graphDataObjects, graphRelationships, null);
        incomingActionProcessor.processIncomingAction(graphAction).block();

        assertThat(actorRepository.count(), greaterThanOrEqualTo(12L));
        Event eventFromDb = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        assertThat(eventFromDb, notNullValue());

        Actor participant = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "participant-1");
        assertThat(participant, notNullValue());
        assertThat(participant.getParticipatesInEvents().size(), is(1));

        GrassrootGraphEntity generator = eventFromDb.getCreator();
        assertThat(generator, notNullValue());
        assertThat(generator.getEntityType(), is(GraphEntityType.ACTOR));
        assertThat(generator.getPlatformUid(), is(TEST_ENTITY_PREFIX + "creating-user"));

        Set<Actor> parents = eventFromDb.getParticipatesIn();
        assertThat(parents, notNullValue());
        assertThat(parents.size(), is(1));
        assertThat(parents.iterator().next().getPlatformUid(), is(TEST_ENTITY_PREFIX + "parent-group"));

        cleanDb();
    }

    @Test
    @Rollback
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

        cleanDb();
    }

    @After
    public void cleanDb() {
        actorRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        eventRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        interactionRepository.deleteByIdContaining(TEST_ENTITY_PREFIX);
    }

}
