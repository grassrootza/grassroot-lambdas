package za.org.grassroot.graph;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StringUtils;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.Interaction;
import za.org.grassroot.graph.domain.enums.ActorType;
import za.org.grassroot.graph.domain.enums.EventType;
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
		log.info("actor from DB: {}", actorFromDb);
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

		Actor actor = actorFromDb.get();
		assertThat(actor.getCreatedEvents().size(), is(1));
		assertThat(actor.getCreatedEvents(), contains(event));

		cleanDb();
	}

	@Test @Rollback
	public void savesAndLoadsParticipants() {
		generalSetUp();
		eventSetUp();

		Event eventFromDb1 = eventRepository.findById(testEvent.getId()).get();

		Actor testActor2 = actorRepository.save(new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "-individual"));
		eventFromDb1.addParticipatingActor(testActor2);

		eventRepository.save(eventFromDb1);

		Event eventFromDb2 = eventRepository.findById(testEvent.getId()).get();
		assertThat(eventFromDb2.getCreator(), is(testActor));
		assertThat(eventFromDb2.getParticipants(), notNullValue());
		assertThat(eventFromDb2.getParticipants().size(), is(1));
		assertThat(eventFromDb2.getParticipants(), contains(testActor2));

		cleanDb();
	}

	@Test @Rollback
	public void savesAndLoadsInteraction() {
		generalSetUp();
		actorRepository.save(testActor);

		Actor testActor2 = new Actor(ActorType.AUTOMATON, TEST_ENTITY_PREFIX + "-automaton");
		actorRepository.save(testActor2);

		Interaction testInteraction = new Interaction(testActor, testActor2);
		testInteraction.setPlatformUid(TEST_ENTITY_PREFIX + "interaction");
		interactionRepository.save(testInteraction);

		Optional<Interaction> interactionFromDb = interactionRepository.findById(testInteraction.getId());
		assertThat(interactionFromDb.isPresent(), is(true));
		assertThat(interactionFromDb.get(), is(testInteraction));

        actorRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
	}

	@Test @Rollback
	public void handlesMovements() {
		generalSetUp();

		Actor movement = new Actor(ActorType.MOVEMENT, TEST_ENTITY_PREFIX + "movement-" + Instant.now().toEpochMilli());
		actorRepository.save(movement);

		testActor.addParticipatesInActor(movement, false);
		actorRepository.save(testActor);

		Actor group1 = new Actor(ActorType.GROUP, TEST_ENTITY_PREFIX + "-group1");
		group1.setCreatedByActor(testActor);
		group1.addParticipatesInActor(movement, false);
		actorRepository.save(group1);

		Actor user2 = new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "-user2");
		user2.addParticipatesInActor(group1, false);
		actorRepository.save(user2);

		Actor user3 = new Actor(ActorType.ACCOUNT, TEST_ENTITY_PREFIX + "-account");
		user3.addParticipatesInActor(group1, false);
		actorRepository.save(user3);

		Actor group2 = new Actor(ActorType.GROUP, TEST_ENTITY_PREFIX + "-group2");
		group2.setCreatedByActor(testActor);
		group2.addParticipatesInActor(movement, false);
		actorRepository.save(group2);

		Actor movementFromDb = actorRepository.findById(movement.getId()).get();
		assertThat(movementFromDb, notNullValue());
		assertThat(movementFromDb.getParticipants(), notNullValue());
		assertThat(movementFromDb.getParticipants().size(), is(3));

		log.info("movement participants: {}", movementFromDb.getParticipants());

		assertThat(movementFromDb.getParticipants().contains(testActor), is(true));
        assertThat(movementFromDb.getParticipants().contains(group1), is(true));
        assertThat(movementFromDb.getParticipants().contains(group2), is(true));

		Collection<Actor> depthFind = actorRepository.findMovementParticipantsInDepth(movement.getPlatformUid());
		assertThat(depthFind.size(), is(5));

        cleanDb();
	}

	@After
	public void cleanDb() {
		actorRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
		eventRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
		interactionRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
	}

}
