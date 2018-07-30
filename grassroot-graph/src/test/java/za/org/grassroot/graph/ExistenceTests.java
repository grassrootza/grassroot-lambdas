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
import za.org.grassroot.graph.domain.enums.*;
import za.org.grassroot.graph.dto.*;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;
import za.org.grassroot.graph.repository.InteractionRepository;
import za.org.grassroot.graph.services.IncomingActionProcessor;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static za.org.grassroot.graph.GraphApplicationTests.TEST_ENTITY_PREFIX;
import static za.org.grassroot.graph.TestUtils.wrapActorAction;
import static za.org.grassroot.graph.TestUtils.wrapEventAction;

@RunWith(SpringRunner.class) @Slf4j
@SpringBootTest(properties = {"sqs.pull.enabled=false","sqs.push.enabled=false"})
public class ExistenceTests {

    @Autowired IncomingActionProcessor incomingActionProcessor;

    @Autowired ActorRepository actorRepository;
    @Autowired EventRepository eventRepository;
    @Autowired InteractionRepository interactionRepository;

    @Test
    @Rollback
    public void addEntityThatAlreadyExists() {
        dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "user", ActionType.CREATE_ENTITY);
        dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "user", ActionType.CREATE_ENTITY);
        assertThat(actorRepository.count(), is(1L));
    }

    @Test
    @Rollback
    public void removeEntityThatDoesNotExist() {
        dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.CREATE_ENTITY);
        dispatchEvent(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", ActionType.REMOVE_ENTITY);
        boolean removalSuccessful = dispatchActor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "meeting", ActionType.REMOVE_ENTITY);
        assertThat(removalSuccessful, is(false));
    }

    @Test
    @Rollback
    public void annotateEntityThatDoesNotExist() {
        Map<String, String> properties = new HashMap<>();
        properties.put(IncomingAnnotation.description, "test-description");
        Set<String> tags = new HashSet<>();
        tags.add("test-tags");

        IncomingDataObject actor = new IncomingDataObject(GraphEntityType.ACTOR,
                new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "user"));
        boolean annotationSuccessful = dispatchAnnotation(actor, null, properties,
                tags, null, ActionType.ANNOTATE_ENTITY);
        assertThat(annotationSuccessful, is(true));
    }

    @Test
    @Rollback
    public void addRelationshipThatAlreadyExists() {
        dispatchParticipation(TEST_ENTITY_PREFIX + "user", GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                TEST_ENTITY_PREFIX + "meeting", GraphEntityType.EVENT, EventType.MEETING.name(), ActionType.CREATE_RELATIONSHIP);
        assertThat(actorRepository.count(), is(1L));
        assertThat(eventRepository.count(), is(1L));

        dispatchParticipation(TEST_ENTITY_PREFIX + "user", GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                TEST_ENTITY_PREFIX + "meeting", GraphEntityType.EVENT, EventType.MEETING.name(), ActionType.CREATE_RELATIONSHIP);
        Actor user = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "user");
        assertThat(user.getParticipatesInEvents(), is(notNullValue()));
        assertThat(user.getParticipatesInEvents().size(), is(1));
    }

    @Test
    @Rollback
    public void removeRelationshipThatDoesNotExist() {
        dispatchParticipation(TEST_ENTITY_PREFIX + "user", GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                TEST_ENTITY_PREFIX + "meeting", GraphEntityType.EVENT, EventType.MEETING.name(), ActionType.CREATE_RELATIONSHIP);
        assertThat(actorRepository.count(), is(1L));
        assertThat(eventRepository.count(), is(1L));

        dispatchParticipation(TEST_ENTITY_PREFIX + "user", GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(),
                TEST_ENTITY_PREFIX + "meeting", GraphEntityType.EVENT, EventType.MEETING.name(), ActionType.REMOVE_RELATIONSHIP);
        Actor user = actorRepository.findByPlatformUid(TEST_ENTITY_PREFIX + "user");
        assertThat(CollectionUtils.isEmpty(user.getParticipatesInEvents()), is(true));

        boolean removalSuccessful = dispatchParticipation(TEST_ENTITY_PREFIX + "user",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), TEST_ENTITY_PREFIX + "meeting",
                GraphEntityType.EVENT, EventType.MEETING.name(), ActionType.REMOVE_RELATIONSHIP);
        assertThat(removalSuccessful, is(false));
    }

    @Test
    @Rollback
    public void annotateRelationshipThatDoesNotExist() {
        Set<String> tags = new HashSet<>();
        tags.add("test-tags");

        IncomingRelationship participation = new IncomingRelationship(TEST_ENTITY_PREFIX + "user",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), TEST_ENTITY_PREFIX + "group",
                GraphEntityType.ACTOR, ActorType.GROUP.name(), GrassrootRelationship.Type.PARTICIPATES);
        boolean annotationSuccessful = dispatchAnnotation(null, participation, null,
                tags, null, ActionType.ANNOTATE_RELATIONSHIP);
        assertThat(annotationSuccessful, is(true));
    }

    @Test
    @Rollback
    public void deannotateNonexistentEntityAndRelationship() {
        Set<String> tags = new HashSet<>();
        tags.add("test-tags");

        IncomingDataObject actor = new IncomingDataObject(GraphEntityType.ACTOR,
                new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "user"));
        boolean entityAnnotationRemoval = dispatchAnnotation(actor, null, null,
                tags, null, ActionType.REMOVE_ANNOTATION);
        assertThat(entityAnnotationRemoval, is(false));

        IncomingRelationship participation = new IncomingRelationship(TEST_ENTITY_PREFIX + "user",
                GraphEntityType.ACTOR, ActorType.INDIVIDUAL.name(), TEST_ENTITY_PREFIX + "group",
                GraphEntityType.ACTOR, ActorType.GROUP.name(), GrassrootRelationship.Type.PARTICIPATES);
        boolean relationshipAnnotationRemoval = dispatchAnnotation(null, participation, null,
                tags, null, ActionType.REMOVE_ANNOTATION);
        assertThat(relationshipAnnotationRemoval, is(false));
    }

    private boolean dispatchActor(ActorType actorType, String platformId, ActionType actionType) {
        return incomingActionProcessor.processIncomingAction(wrapActorAction(actorType, platformId, actionType)).block();
    }

    private boolean dispatchEvent(EventType eventType, String platformId, ActionType actionType) {
        return incomingActionProcessor.processIncomingAction(wrapEventAction(eventType, platformId, actionType)).block();
    }

    private boolean dispatchParticipation(String participantUid, GraphEntityType participantType, String participantSubtype,
                                          String targetUid, GraphEntityType targetType, String targetSubtype, ActionType actionType) {
        IncomingRelationship participation = new IncomingRelationship(participantUid, participantType, participantSubtype,
                targetUid, targetType, targetSubtype, GrassrootRelationship.Type.PARTICIPATES);
        IncomingGraphAction action = new IncomingGraphAction(participantUid, actionType,
                null, Collections.singletonList(participation), null);
        return incomingActionProcessor.processIncomingAction(action).block();
    }

    private boolean dispatchAnnotation(IncomingDataObject dataObject, IncomingRelationship relationship,
                                    Map<String, String> properties, Set<String> tags, Set<String> toRemove, ActionType actionType) {
        IncomingAnnotation annotation = new IncomingAnnotation(dataObject, relationship, properties, tags, toRemove);
        IncomingGraphAction action = new IncomingGraphAction("", actionType,
                null, null, Collections.singletonList(annotation));
        return incomingActionProcessor.processIncomingAction(action).block();
    }

    @After
    public void cleanDb() {
        actorRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        eventRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
        interactionRepository.deleteAll();
    }

}