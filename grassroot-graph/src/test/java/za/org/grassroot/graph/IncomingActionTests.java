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
import za.org.grassroot.graph.domain.enums.ActorType;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.enums.GrassrootRelationship;
import za.org.grassroot.graph.domain.repository.ActorRepository;
import za.org.grassroot.graph.domain.repository.EventRepository;
import za.org.grassroot.graph.domain.repository.InteractionRepository;
import za.org.grassroot.graph.dto.ActionType;
import za.org.grassroot.graph.dto.IncomingDataObject;
import za.org.grassroot.graph.dto.IncomingGraphAction;
import za.org.grassroot.graph.dto.IncomingRelationship;
import za.org.grassroot.graph.services.IncomingActionProcessor;

import java.util.Collections;

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

    @After
    public void cleanDb() {
        log.info("Cleaning up DB");
//        eventRepository.deleteAll();
//        interactionRepository.deleteAll();
//        actorRepository.deleteAll();
    }

}
