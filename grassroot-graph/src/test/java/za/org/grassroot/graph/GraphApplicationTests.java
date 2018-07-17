package za.org.grassroot.graph;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.session.Session;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.Interaction;
import za.org.grassroot.graph.domain.enums.ActorType;
import za.org.grassroot.graph.domain.enums.EventType;
import za.org.grassroot.graph.domain.relationship.ActorInActor;
import za.org.grassroot.graph.domain.relationship.ActorInEvent;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;
import za.org.grassroot.graph.repository.InteractionRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@RunWith(SpringRunner.class) @Slf4j
@SpringBootTest
public class GraphApplicationTests {

    // integration testing is failing on a dependency conflict (NoClassDef in jetty for the in-memory neo4j),
    // hence using a workaround where all test entities have this pattern, so we can clean them at the end

    protected static final String TEST_ENTITY_PREFIX = "testing-entity-";

	@Autowired ActorRepository actorRepository;
	@Autowired EventRepository eventRepository;
	@Autowired InteractionRepository interactionRepository;
	@Autowired Session session;

	private Actor testActor;
	private Event testEvent;

	@Before
	public void generalSetUp() {
        Optional<Actor> actorCheckDb = testActor == null || StringUtils.isEmpty(testActor.getId())
				? Optional.empty() : actorRepository.findById(testActor.getId());
		if (!actorCheckDb.isPresent()) {
			testActor = new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "actor");
		}
	}

	private void eventSetUp() {
		testEvent = new Event(EventType.MEETING, TEST_ENTITY_PREFIX + "event", Instant.now().toEpochMilli());
		testEvent.setCreator(testActor);
		actorRepository.save(testActor);
		eventRepository.save(testEvent);
	}

	@Test @Rollback
	public void actorSavesAndLoads() {
		generalSetUp();
		actorRepository.save(testActor);

		Optional<Actor> actorFromDb = actorRepository.findById(testActor.getId());
		assertThat(actorFromDb.isPresent(), is(true));
		assertThat(actorFromDb.get(), is(testActor));
		assertThat(actorFromDb.get().getCreationTime(), notNullValue());

		cleanDb();
	}

	@Test @Rollback
	public void eventSavesAndLoadsWithCreator() {
		generalSetUp();
		eventSetUp();

		Optional<Event> eventFromDb = eventRepository.findById(testEvent.getId());
		assertThat(eventFromDb.isPresent(), is(true));

		Event event = eventFromDb.get();
		assertThat(event.getCreator(), is(testActor));

		Optional<Actor> actorFromDb = actorRepository.findById(testActor.getId());
		assertThat(actorFromDb.isPresent(), is(true));

		cleanDb();
	}

	@Test @Rollback @Transactional
	public void savesAndLoadsParticipants() {
		generalSetUp();
		eventSetUp();

		Event eventFromDb = eventRepository.findById(testEvent.getId()).get();
		Actor testActor2 = actorRepository.save(new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "-individual"));
		ActorInEvent relationship = new ActorInEvent(testActor2, eventFromDb);
		session.save(relationship, 0);

		Event checkEvent = eventRepository.findById(testEvent.getId()).get();
		Actor checkActor = actorRepository.findById(testActor2.getId()).get();
		assertThat(checkEvent.getCreator(), is(testActor));
		assertThat(checkActor.getParticipatesInEvents(), notNullValue());
		assertThat(checkActor.getParticipatesInEvents().size(), is(1));
		assertThat(checkActor.getParticipatesInEvents(), contains(relationship));

		cleanDb();
	}

	@Test @Rollback
	public void savesAndLoadsInteraction() {
		generalSetUp();
		actorRepository.save(testActor);

		Interaction testInteraction = new Interaction(testActor);
		testInteraction.setId(TEST_ENTITY_PREFIX + "interaction");
		interactionRepository.save(testInteraction);

		Optional<Interaction> interactionFromDb = interactionRepository.findById(testInteraction.getId());
		assertThat(interactionFromDb.isPresent(), is(true));
		assertThat(interactionFromDb.get(), is(testInteraction));

		cleanDb();
	}

	@Test @Rollback @Transactional
	public void handlesMovements() {
		generalSetUp();

		Actor movement = new Actor(ActorType.MOVEMENT, TEST_ENTITY_PREFIX + "movement-" + Instant.now().toEpochMilli());
		actorRepository.save(movement);

		actorRepository.save(testActor);
		ActorInActor actorInMovement = new ActorInActor(testActor, movement, Instant.now());
		session.save(actorInMovement, 0);

		Actor group1 = actorRepository.save(new Actor(ActorType.GROUP, TEST_ENTITY_PREFIX + "-group1"));
		ActorInActor group1InMovement = new ActorInActor(group1, movement, Instant.now());
		session.save(group1InMovement, 0);

		Actor user2 = actorRepository.save(new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "-user2"));
		ActorInActor individualInMovement = new ActorInActor(user2, movement, Instant.now());
		session.save(individualInMovement, 0);

		Actor user3 = actorRepository.save(new Actor(ActorType.ACCOUNT, TEST_ENTITY_PREFIX + "-account"));
		ActorInActor accountInMovement = new ActorInActor(user3, movement, Instant.now());
		session.save(accountInMovement, 0);

		Actor group2 = actorRepository.save(new Actor(ActorType.GROUP, TEST_ENTITY_PREFIX + "-group2"));
		ActorInActor group2InMovement = new ActorInActor(group2, movement, Instant.now());
		session.save(group2InMovement, 0);

		Actor movementFromDb = actorRepository.findById(movement.getId()).get();
		assertThat(movementFromDb, notNullValue());

		Collection<Actor> depthFind = actorRepository.findMovementParticipantsInDepth(movement.getPlatformUid());
		assertThat(depthFind.size(), is(5));

		Optional<Actor> firstActor = actorRepository.findById(testActor.getId());
		assertThat(firstActor.isPresent(), is(true));
		assertThat(firstActor.get().getParticipatesInActors().size(), is(1));

        cleanDb();
	}

	@After
	public void cleanDb() {
		actorRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
		eventRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
		interactionRepository.deleteByIdContaining(TEST_ENTITY_PREFIX);
	}

}
