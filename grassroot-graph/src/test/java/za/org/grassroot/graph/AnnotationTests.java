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
import za.org.grassroot.graph.domain.relationship.ActorInActor;
import za.org.grassroot.graph.dto.*;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;
import za.org.grassroot.graph.repository.InteractionRepository;
import za.org.grassroot.graph.services.IncomingActionProcessor;

import java.time.Instant;
import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static za.org.grassroot.graph.GraphApplicationTests.TEST_ENTITY_PREFIX;
import static za.org.grassroot.graph.TestUtils.wrapActorAction;
import static za.org.grassroot.graph.TestUtils.wrapEventAction;
import static za.org.grassroot.graph.TestUtils.wrapInteractionAction;

@RunWith(SpringRunner.class) @Slf4j
@SpringBootTest(properties = {"sqs.pull.enabled=false","sqs.push.enabled=false"})
public class AnnotationTests {

    @Autowired IncomingActionProcessor incomingActionProcessor;

    @Autowired ActorRepository actorRepository;
    @Autowired EventRepository eventRepository;
    @Autowired InteractionRepository interactionRepository;

    @Test
    @Rollback
    public void annotateAndDeannotateActor() {
        Map<String, String> properties = new HashMap<>();
        properties.put(IncomingAnnotation.language, "test-language");
        Set<String> tags = new HashSet<>();
        tags.add("test-tags");

        IncomingDataObject user = dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "individual");
        dispatchAnnotation(user, null, properties, tags, null, ActionType.ANNOTATE_ENTITY);
        Actor userFromDB = actorRepository.findByPlatformUid(user.getGraphEntity().getPlatformUid());

        assertThat(userFromDB.getStdProps(), notNullValue());
        assertThat(userFromDB.getStdProps().size(), is(1));
        assertThat(userFromDB.getStdProps().get(IncomingAnnotation.language), is("test-language"));
        assertThat(userFromDB.getStdTags(), notNullValue());
        assertThat(userFromDB.getStdTags().length, is(1));
        assertThat(userFromDB.getStdTags()[0], is("test-tags"));

        dispatchAnnotation(user, null, null, tags, properties.keySet(), ActionType.REMOVE_ANNOTATION);
        Actor userFromDB2 = actorRepository.findByPlatformUid(user.getGraphEntity().getPlatformUid());
        assertThat(userFromDB2.getStdProps().get(IncomingAnnotation.language), is(""));
        assertThat(userFromDB2.getStdTags().length, is(0));
    }

    @Test
    @Rollback
    public void annotateAndDeannotateEvent() {
        Map<String, String> properties = new HashMap<>();
        properties.put(IncomingAnnotation.description, "test-description");
        Set<String> tags = new HashSet<>();
        tags.add("test-tags");

        IncomingDataObject meeting = dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting");
        dispatchAnnotation(meeting, null, properties, tags, null, ActionType.ANNOTATE_ENTITY);
        Event meetingFromDB = eventRepository.findByPlatformUid(meeting.getGraphEntity().getPlatformUid());

        assertThat(meetingFromDB.getStdProps(), notNullValue());
        assertThat(meetingFromDB.getStdProps().size(), is(1));
        assertThat(meetingFromDB.getStdProps().get(IncomingAnnotation.description), is("test-description"));
        assertThat(meetingFromDB.getStdTags(), notNullValue());
        assertThat(meetingFromDB.getStdTags().length, is(1));
        assertThat(meetingFromDB.getStdTags()[0], is("test-tags"));

        dispatchAnnotation(meeting, null, null, tags, properties.keySet(), ActionType.REMOVE_ANNOTATION);
        Event meetingFromDB2 = eventRepository.findByPlatformUid(meeting.getGraphEntity().getPlatformUid());
        assertThat(meetingFromDB2.getStdProps().get(IncomingAnnotation.description), is(""));
        assertThat(meetingFromDB2.getStdTags().length, is(0));
    }

    @Test
    @Rollback
    public void attemptUnsupportedInteractionAnnotation() {
        Map<String, String> properties = new HashMap<>();
        properties.put(IncomingAnnotation.description, "test-description");
        Set<String> tags = new HashSet<>();
        tags.add("test-tags");

        IncomingDataObject survey = dispatchInteraction(InteractionType.SURVEY, TEST_ENTITY_PREFIX + "survey");
        dispatchAnnotation(survey, null, properties, tags, null, ActionType.ANNOTATE_ENTITY);
        dispatchAnnotation(survey, null, properties, tags, null, ActionType.REMOVE_ANNOTATION);
    }

    @Test
    @Rollback
    public void annotateAndDeannotateActorActorRelationship() {
        Set<String> tags = new HashSet<>();
        tags.add("test-tags");

        IncomingDataObject user = dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "individual");
        IncomingDataObject group = dispatchActor(ActorType.GROUP, TEST_ENTITY_PREFIX + "group");
        IncomingRelationship participation = dispatchParticipation(user.getGraphEntity().getPlatformUid(),
                user.getEntityType(), user.getEntitySubtype(), group.getGraphEntity().getPlatformUid(),
                group.getEntityType(), group.getEntitySubtype());
        dispatchAnnotation(null, participation, null, tags, null, ActionType.ANNOTATE_RELATIONSHIP);

        Actor userFromDB = actorRepository.findByPlatformUid(user.getGraphEntity().getPlatformUid());
        ActorInActor relationship = userFromDB.getRelationshipWith((Actor) group.getGraphEntity());
        assertThat(relationship, notNullValue());
        assertThat(relationship.getStdTags(), notNullValue());
        assertThat(relationship.getStdTags().length, is(1));
        assertThat(relationship.getStdTags()[0], is("test-tags"));

        dispatchAnnotation(null, participation, null, tags, null, ActionType.REMOVE_ANNOTATION);
        Actor userFromDB2 = actorRepository.findByPlatformUid(user.getGraphEntity().getPlatformUid());
        ActorInActor relationship2 = userFromDB2.getRelationshipWith((Actor) group.getGraphEntity());
        assertThat(relationship2.getStdTags().length, is(0));
    }

    @Test
    @Rollback
    public void attemptUnsupportedParticipationAnnotation() {
        Set<String> tags = new HashSet<>();
        tags.add("test-tags");

        IncomingDataObject person = dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "person");
        IncomingDataObject meeting = dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting");
        IncomingRelationship actorEventParticipation = dispatchParticipation(person.getGraphEntity().getPlatformUid(),
                person.getEntityType(), person.getEntitySubtype(), meeting.getGraphEntity().getPlatformUid(),
                meeting.getEntityType(), meeting.getEntitySubtype());

        dispatchAnnotation(null, actorEventParticipation, null, tags, null, ActionType.ANNOTATE_RELATIONSHIP);
        dispatchAnnotation(null, actorEventParticipation, null, tags, null, ActionType.REMOVE_ANNOTATION);

    }

    @Test
    @Rollback
    public void attemptUnsupportedGenerationAnnotation() {
        Set<String> tags = new HashSet<>();
        tags.add("test-tags");

        IncomingDataObject person = dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "person");
        IncomingDataObject meeting = dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting");
        IncomingRelationship actorActorGeneration = dispatchGeneration(person.getGraphEntity().getPlatformUid(),
                person.getEntityType(), person.getEntitySubtype(), meeting.getGraphEntity().getPlatformUid(),
                meeting.getEntityType(), meeting.getEntitySubtype());

        dispatchAnnotation(null, actorActorGeneration, null, tags, null, ActionType.ANNOTATE_RELATIONSHIP);
        dispatchAnnotation(null, actorActorGeneration, null, tags, null, ActionType.REMOVE_ANNOTATION);
    }

    private IncomingDataObject dispatchActor(ActorType actorType, String platformId) {
        incomingActionProcessor.processIncomingAction(wrapActorAction(actorType, platformId, ActionType.CREATE_ENTITY));
        return new IncomingDataObject(GraphEntityType.ACTOR, new Actor(actorType, platformId));
    }

    private IncomingDataObject dispatchEvent(EventType eventType, String platformId) {
        incomingActionProcessor.processIncomingAction(wrapEventAction(eventType, platformId, ActionType.CREATE_ENTITY));
        return new IncomingDataObject(GraphEntityType.EVENT, new Event(eventType, platformId, Instant.now().toEpochMilli()));
    }

    private IncomingDataObject dispatchInteraction(InteractionType interactionType, String id) {
        incomingActionProcessor.processIncomingAction(wrapInteractionAction(interactionType, id, ActionType.CREATE_ENTITY));
        Interaction interaction = new Interaction();
        interaction.setInteractionType(interactionType);
        interaction.setId(id);
        return new IncomingDataObject(GraphEntityType.INTERACTION, interaction);
    }

    private IncomingRelationship dispatchParticipation(String participantUid, GraphEntityType participantType, String participantSubtype,
                                                           String targetUid, GraphEntityType targetType, String targetSubtype) {
        IncomingRelationship participation = new IncomingRelationship(participantUid, participantType, participantSubtype,
                targetUid, targetType, targetSubtype, GrassrootRelationship.Type.PARTICIPATES);
        IncomingGraphAction action = new IncomingGraphAction(participantUid, ActionType.CREATE_RELATIONSHIP,
                null, Collections.singletonList(participation), null);
        incomingActionProcessor.processIncomingAction(action).block();
        return participation;
    }

    private IncomingRelationship dispatchGeneration(String generatorUid, GraphEntityType generatorType, String generatorSubtype,
                                                    String generatedUid, GraphEntityType generatedType, String generatedSubtype) {
        IncomingRelationship generation = new IncomingRelationship(generatorUid, generatorType, generatorSubtype,
                generatedUid, generatedType, generatedSubtype, GrassrootRelationship.Type.GENERATOR);
        IncomingGraphAction action = new IncomingGraphAction(generatorUid, ActionType.CREATE_RELATIONSHIP,
                null, Collections.singletonList(generation), null);
        incomingActionProcessor.processIncomingAction(action).block();
        return generation;
    }

    private void dispatchAnnotation(IncomingDataObject dataObject, IncomingRelationship relationship,
                                    Map<String, String> properties, Set<String> tags, Set<String> toRemove, ActionType actionType) {
        IncomingAnnotation annotation = new IncomingAnnotation(dataObject, relationship, properties, tags, toRemove);
        IncomingGraphAction action = new IncomingGraphAction("", actionType,
                null, null, Collections.singletonList(annotation));
        incomingActionProcessor.processIncomingAction(action).block();
    }

    @After
    public void cleanDb() {
        actorRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        eventRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        interactionRepository.deleteByIdContaining(TEST_ENTITY_PREFIX);
    }

}