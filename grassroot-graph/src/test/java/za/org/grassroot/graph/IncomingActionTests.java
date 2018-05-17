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
import za.org.grassroot.graph.domain.enums.ActorType;
import za.org.grassroot.graph.domain.enums.EventType;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.enums.GrassrootRelationship;
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


@RunWith(SpringRunner.class) @Slf4j
@SpringBootTest(properties = {"sqs.enabled=false"})
public class IncomingActionTests {

    @Autowired private IncomingActionProcessor incomingActionProcessor;

    @Autowired private ActorRepository actorRepository;
    @Autowired private EventRepository eventRepository;
    @Autowired private InteractionRepository interactionRepository;

    private void addActorViaIncoming(ActorType actorType, String platformId) {
        Actor testActor = new Actor(actorType);
        testActor.setPlatformUid(platformId);
        IncomingDataObject dataObject = new IncomingDataObject(GraphEntityType.ACTOR, testActor);
        IncomingGraphAction graphAction = new IncomingGraphAction(platformId, ActionType.CREATE_ENTITY,
                Collections.singletonList(dataObject), null);

        incomingActionProcessor.processIncomingAction(graphAction);
    }

    @Test
    @Rollback
    public void addsAnActorToDb() {
        addActorViaIncoming(ActorType.INDIVIDUAL, "testing-entity");
        Actor actorCheckDb = actorRepository.findByPlatformUid("testing-entity");
        assertThat(actorCheckDb, notNullValue());
    }

    @Test
    @Rollback
    public void addsAnActorAndGroup() {
        actorRepository.deleteAll();

        addActorViaIncoming(ActorType.GROUP, "testing-group");
        addActorViaIncoming(ActorType.INDIVIDUAL, "testing-person");

        assertThat(actorRepository.count(), is(2L));
    }

    @Test
    @Rollback
    public void addsActorGroupAndRelationship() {
        actorRepository.deleteAll();

        addActorViaIncoming(ActorType.GROUP, "testing-group");
        addActorViaIncoming(ActorType.INDIVIDUAL, "testing-person");

        IncomingRelationship relationship = new IncomingRelationship("testing-person", GraphEntityType.ACTOR,
                "testing-group", GraphEntityType.ACTOR, GrassrootRelationship.Type.PARTICIPATES);

        incomingActionProcessor.processIncomingAction(new IncomingGraphAction("testing-person", ActionType.CREATE_RELATIONSHIP,
                null, Collections.singletonList(relationship)));

        assertThat(actorRepository.count(), is(2L));
    }

    @Test
    @Rollback
    public void addsTaskAndRelationship() {
        log.info("adding a task to Grassroot Graph ... ");
        List<IncomingDataObject> graphDataObjects = new ArrayList<>();

        Event graphEvent = new Event(EventType.MEETING, "test-meeting", Instant.now().toEpochMilli());

        Actor creatingUser = new Actor(ActorType.INDIVIDUAL, "test-creating-user");
        graphEvent.setCreator(creatingUser);

        List<Actor> participatingActors = IntStream.range(0, 10)
                .mapToObj(index -> new Actor(ActorType.INDIVIDUAL, "test-participant-" + index)).collect(Collectors.toList());
        graphEvent.setParticipants(participatingActors);

        Actor graphParent = new Actor(ActorType.GROUP, "test-parent-group");
        graphEvent.setParticipatesIn(Collections.singletonList(graphParent));

        // note: neo4j on other end _should_ take care of these relationships, but to check (and test ...)
        graphDataObjects.add(new IncomingDataObject(GraphEntityType.ACTOR, creatingUser));
        graphDataObjects.addAll(participatingActors.stream().map(a -> new IncomingDataObject(GraphEntityType.ACTOR, a)).collect(Collectors.toList()));
        graphDataObjects.add(new IncomingDataObject(GraphEntityType.ACTOR, graphParent));
        graphDataObjects.add(new IncomingDataObject(GraphEntityType.EVENT, graphEvent));

        IncomingGraphAction graphAction = new IncomingGraphAction("test-meeting", ActionType.CREATE_ENTITY,
                graphDataObjects, null);
        incomingActionProcessor.processIncomingAction(graphAction);

        assertThat(actorRepository.count(), is(12L));

        Event eventFromDb = eventRepository.findByPlatformUid("test-meeting");
        assertThat(eventFromDb, notNullValue());
        List<Actor> participants = eventFromDb.getParticipants();
        assertThat(participants, notNullValue());
        assertThat(participants.size(), is(10));
        GrassrootGraphEntity generator = eventFromDb.getCreator();
        assertThat(generator, notNullValue());
        assertThat(generator.getEntityType(), is(GraphEntityType.ACTOR));
        assertThat(generator.getPlatformUid(), is("test-creating-user"));
        List<Actor> parents = eventFromDb.getParticipatesIn();
        assertThat(parents, notNullValue());
        assertThat(parents.size(), is(1));
        assertThat(parents.get(0).getPlatformUid(), is("test-parent-group"));

        cleanDb();
    }

    @After
    public void cleanDb() {
        log.info("Cleaning up DB");
        eventRepository.deleteAll();
        interactionRepository.deleteAll();
        actorRepository.deleteAll();
    }

}
