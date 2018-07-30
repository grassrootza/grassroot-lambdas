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
import static za.org.grassroot.graph.TestUtils.wrapActorAction;
import static za.org.grassroot.graph.TestUtils.wrapEventAction;
import static za.org.grassroot.graph.TestUtils.wrapInteractionAction;

@RunWith(SpringRunner.class) @Slf4j
@SpringBootTest(properties = {"sqs.pull.enabled=false","sqs.push.enabled=false"})
public class RelationshipTests {

    @Autowired IncomingActionProcessor incomingActionProcessor;

    @Autowired ActorRepository actorRepository;
    @Autowired EventRepository eventRepository;
    @Autowired InteractionRepository interactionRepository;

    @Test
    @Rollback
    public void addAndRemoveValidGenerationRelationships() {
        dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "generatorActor", ActionType.CREATE_ENTITY);
        dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "generatorEvent", ActionType.CREATE_ENTITY);
        dispatchActor(ActorType.GROUP, TEST_ENTITY_PREFIX + "group", ActionType.CREATE_ENTITY);
        dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        dispatchInteraction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);
        dispatchEvent(EventType.VOTE, TEST_ENTITY_PREFIX + "vote", ActionType.CREATE_ENTITY);

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
        verifyGeneratorRelationshipsExist();

        // try to (invalidly) remove generation relationships
        action.setActionType(ActionType.REMOVE_RELATIONSHIP);
        boolean removedRelationships = incomingActionProcessor.processIncomingAction(action).block();
        assertThat(removedRelationships, is(false));
        verifyGeneratorRelationshipsExist();
    }

    @Test
    @Rollback
    public void addAndRemoveInvalidGenerationRelationships() {
        dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "person", ActionType.CREATE_ENTITY);
        dispatchInteraction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);

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

        action.setActionType(ActionType.REMOVE_RELATIONSHIP);
        boolean removedRelationships = incomingActionProcessor.processIncomingAction(action).block();
        assertThat(removedRelationships, is(false));
    }

    @Test
    @Rollback
    public void addAndRemoveValidParticipationRelationships() {
        dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "participantActor", ActionType.CREATE_ENTITY);
        dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "participantEvent", ActionType.CREATE_ENTITY);
        dispatchActor(ActorType.GROUP, TEST_ENTITY_PREFIX + "group", ActionType.CREATE_ENTITY);
        dispatchEvent(EventType.VOTE, TEST_ENTITY_PREFIX + "vote", ActionType.CREATE_ENTITY);
        dispatchInteraction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);

        IncomingGraphAction action = new IncomingGraphAction(TEST_ENTITY_PREFIX + "participantActor",
                ActionType.CREATE_RELATIONSHIP, null, new ArrayList<>(), null);
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "participantActor",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), TEST_ENTITY_PREFIX + "group",
                GraphEntityType.ACTOR, ActorType.GROUP.name(), GrassrootRelationship.Type.PARTICIPATES));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "participantActor",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), TEST_ENTITY_PREFIX + "vote",
                GraphEntityType.EVENT, EventType.VOTE.name(), GrassrootRelationship.Type.PARTICIPATES));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "participantActor",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), TEST_ENTITY_PREFIX + "survey",
                GraphEntityType.INTERACTION, InteractionType.SURVEY.name(), GrassrootRelationship.Type.PARTICIPATES));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "participantEvent",
                GraphEntityType.EVENT, EventType.MEETING.name(), TEST_ENTITY_PREFIX + "group",
                GraphEntityType.ACTOR, ActorType.GROUP.name(), GrassrootRelationship.Type.PARTICIPATES));
        incomingActionProcessor.processIncomingAction(action).block();
        verifyParticipationRelationshipsExist(true);

        // unlike in generation, removal is valid here.
        action.setActionType(ActionType.REMOVE_RELATIONSHIP);
        boolean removedRelationships = incomingActionProcessor.processIncomingAction(action).block();
        assertThat(removedRelationships, is(true));
        verifyParticipationRelationshipsExist(false);
    }

    @Test
    @Rollback
    public void addAndRemoveInvalidParticipationRelationships() {
        dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "person", ActionType.CREATE_ENTITY);
        dispatchInteraction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);

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

        action.setActionType(ActionType.REMOVE_RELATIONSHIP);
        boolean removedRelationships = incomingActionProcessor.processIncomingAction(action).block();
        assertThat(removedRelationships, is(false));
    }

    @Test
    @Rollback
    public void checkDuplicationWhenSaveNewRelationship() {
        log.info("Creating event that participates in 25 actors.");
        dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        List<Actor> groups = IntStream.range(0, 25).mapToObj(index ->
                new Actor(ActorType.GROUP, TEST_ENTITY_PREFIX + "group-" + index)).collect(Collectors.toList());

        List<IncomingDataObject> dataObjects = groups.stream().map(g ->
                new IncomingDataObject(GraphEntityType.ACTOR, g)).collect(Collectors.toList());
        List<IncomingRelationship> relationships = groups.stream().map(g ->
                new IncomingRelationship(TEST_ENTITY_PREFIX + "meeting", GraphEntityType.EVENT,
                        EventType.MEETING.name(), g.getPlatformUid(), g.getEntityType(), g.getActorType().name(),
                        GrassrootRelationship.Type.PARTICIPATES)).collect(Collectors.toList());
        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "meeting",
                ActionType.CREATE_ENTITY, dataObjects, relationships, null)).block();

        Event meetingFromDB = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        assertThat(CollectionUtils.isEmpty(meetingFromDB.getParticipatesIn()), is(false));
        assertThat(meetingFromDB.getParticipatesIn().size(), is(25));

        log.info("Now adding one more participation, verify existing relationships are not persisted again");
        dispatchActor(ActorType.GROUP, TEST_ENTITY_PREFIX + "new-group", ActionType.CREATE_ENTITY);
        IncomingRelationship participation = new IncomingRelationship(TEST_ENTITY_PREFIX + "meeting",
                GraphEntityType.EVENT, EventType.MEETING.name(),TEST_ENTITY_PREFIX + "new-group",
                GraphEntityType.ACTOR, ActorType.GROUP.name(), GrassrootRelationship.Type.PARTICIPATES);
        incomingActionProcessor.processIncomingAction(new IncomingGraphAction(TEST_ENTITY_PREFIX + "meeting",
                ActionType.CREATE_RELATIONSHIP, null, Collections.singletonList(participation), null)).block();
    }

    @Test
    @Rollback
    public void checkRelationshipRemovedWhenEntityDeleted() {
        dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "person", ActionType.CREATE_ENTITY);
        dispatchInteraction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.CREATE_ENTITY);

        IncomingGraphAction action = new IncomingGraphAction(TEST_ENTITY_PREFIX + "person",
                ActionType.CREATE_RELATIONSHIP, null, new ArrayList<>(), null);
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "person",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), TEST_ENTITY_PREFIX + "meeting",
                GraphEntityType.EVENT, EventType.MEETING.name(), GrassrootRelationship.Type.PARTICIPATES));
        action.addRelationship(new IncomingRelationship(TEST_ENTITY_PREFIX + "person",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), TEST_ENTITY_PREFIX + "survey",
                GraphEntityType.INTERACTION, InteractionType.SURVEY.name(), GrassrootRelationship.Type.PARTICIPATES));
        incomingActionProcessor.processIncomingAction(action).block();

        Actor personFromDB = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "person");
        assertThat(CollectionUtils.isEmpty(personFromDB.getParticipatesInEvents()), is(false));
        assertThat(CollectionUtils.isEmpty(personFromDB.getParticipatesInInteractions()), is(false));

        dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.REMOVE_ENTITY);
        dispatchInteraction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey", ActionType.REMOVE_ENTITY);
        personFromDB = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "person");
        assertThat(CollectionUtils.isEmpty(personFromDB.getParticipatesInEvents()), is(true));
        assertThat(CollectionUtils.isEmpty(personFromDB.getParticipatesInInteractions()), is(true));
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

    private void verifyGeneratorRelationshipsExist() {
        Actor groupFromDB = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "group");
        Event meetingFromDB = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "meeting");
        Interaction surveyFromDB = interactionRepository.findById(TEST_ENTITY_PREFIX + "survey").orElse(null);
        Event voteFromDB = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "vote");
        assertThat(groupFromDB.getCreatedByActor(), is(notNullValue()));
        assertThat(meetingFromDB.getCreator(), is(notNullValue()));
        assertThat(surveyFromDB.getInitiator(), is(notNullValue()));
        assertThat(voteFromDB.getCreator(), is(notNullValue()));
    }

    private void verifyParticipationRelationshipsExist(boolean exist) {
        Actor actorFromDB = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "participantActor");
        Event eventFromDB = eventRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "participantEvent");
        assertThat(!CollectionUtils.isEmpty(actorFromDB.getParticipatesInActors()), is(exist));
        assertThat(!CollectionUtils.isEmpty(actorFromDB.getParticipatesInEvents()), is(exist));
        assertThat(!CollectionUtils.isEmpty(actorFromDB.getParticipatesInInteractions()), is(exist));
        assertThat(!CollectionUtils.isEmpty(eventFromDB.getParticipatesIn()), is(exist));
    }

    @After
    public void cleanDb() {
        actorRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        eventRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        interactionRepository.deleteAll();
    }

}