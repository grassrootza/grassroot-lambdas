package za.org.grassroot.graph;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.session.Session;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.Interaction;
import za.org.grassroot.graph.domain.enums.ActorType;
import za.org.grassroot.graph.domain.enums.EventType;
import za.org.grassroot.graph.domain.enums.InteractionType;
import za.org.grassroot.graph.domain.relationship.ActorInActor;
import za.org.grassroot.graph.domain.relationship.ActorInEvent;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;
import za.org.grassroot.graph.repository.InteractionRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@RunWith(SpringRunner.class) @Slf4j
@SpringBootTest(properties = {"sqs.pull.enabled=false","sqs.push.enabled=false"})
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
	private Interaction testInteraction;

	@Test @Rollback @Transactional
	public void saveAndDeleteActor() {
		addActor();

		Actor actorFromDb = actorRepository.findByPlatformUid(testActor.getPlatformUid());
		assertThat(actorFromDb, is(notNullValue()));
		assertThat(actorFromDb, is(testActor));
		assertThat(actorFromDb.getCreationTime(), notNullValue());

		actorRepository.deleteByPlatformUid(testActor.getPlatformUid());
		Actor actorFromDb2 = actorRepository.findByPlatformUid(testActor.getPlatformUid());
		assertThat(actorFromDb2, is(nullValue()));
	}

	@Test @Rollback @Transactional
	public void saveAndDeleteEvent() {
		addEvent();

		Event eventFromDb = eventRepository.findByPlatformUid(testEvent.getPlatformUid());
		assertThat(eventFromDb, is(notNullValue()));
		assertThat(eventFromDb, is(testEvent));
		assertThat(eventFromDb.getCreator(), is(testActor));

		eventRepository.deleteByPlatformUid(testEvent.getPlatformUid());
		Event eventFromDB2 = eventRepository.findByPlatformUid(testEvent.getPlatformUid());
		assertThat(eventFromDB2, is(nullValue()));
	}

	@Test @Rollback @Transactional
	public void saveAndDeleteInteraction() {
		addInteraction();

		Interaction interactionFromDb = interactionRepository.findById(testInteraction.getId()).orElse(null);
		assertThat(interactionFromDb, is(notNullValue()));
		assertThat(interactionFromDb, is(testInteraction));
		assertThat(interactionFromDb.getInitiator(), is(testActor));

		interactionRepository.deleteById(testInteraction.getPlatformUid());
		Interaction interactionFromDB2 = interactionRepository.findById(testInteraction.getId()).orElse(null);
		assertThat(interactionFromDB2, is(nullValue()));
	}

	@Test @Rollback @Transactional
	public void saveAndDeleteActorActorRelationship() {
		addActor();

		List<Actor> participatingActors = IntStream.range(0, 10).mapToObj(index -> new Actor(ActorType.INDIVIDUAL,
				TEST_ENTITY_PREFIX + "participant-" + index)).collect(Collectors.toList());
		for (Actor actor : participatingActors) {
			ActorInActor relationship = new ActorInActor(actor, testActor, Instant.now());
			actorRepository.save(actor, 0);
			session.save(relationship, 0);
		}

		List<Actor> actorsFromDB = participatingActors.stream().map(actor ->
				actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
		assertThat(actorsFromDB.size(), is(10));

		boolean relationshipsPersisted = actorsFromDB.stream().map(actor ->
				!CollectionUtils.isEmpty(actor.getParticipatesInActors())).reduce(true, (a, b) -> a && b);
		assertThat(relationshipsPersisted, is(true));

		for (Actor actor : participatingActors) {
			ActorInActor relationship = actor.getParticipatesInActors().stream().filter(AinA ->
					AinA.getParticipatesIn().equals(testActor)).findAny().orElse(null);
			assertThat(relationship, is(notNullValue()));
			session.delete(relationship);
		}

		List<Actor> actorsFromDB2 = participatingActors.stream().map(actor ->
				actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
		assertThat(actorsFromDB2.size(), is(10));

		boolean relationshipsDeleted = actorsFromDB2.stream().map(actor ->
				CollectionUtils.isEmpty(actor.getParticipatesInActors())).reduce(true, (a, b) -> a && b);
		assertThat(relationshipsDeleted, is(true));
	}

	@Test @Rollback @Transactional
	public void saveAndDeleteActorEventRelationship() {
		addEvent();

		List<Actor> participatingActors = IntStream.range(0, 10).mapToObj(index -> new Actor(ActorType.INDIVIDUAL,
				TEST_ENTITY_PREFIX + "participant-" + index)).collect(Collectors.toList());
		for (Actor actor : participatingActors) {
			ActorInEvent relationship = new ActorInEvent(actor, testEvent);
			actorRepository.save(actor, 0);
			session.save(relationship, 0);
		}

		List<Actor> actorsFromDB = participatingActors.stream().map(actor ->
				actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
		assertThat(actorsFromDB.size(), is(10));

		boolean relationshipsPersisted = actorsFromDB.stream().map(actor ->
				!CollectionUtils.isEmpty(actor.getParticipatesInEvents())).reduce(true, (a, b) -> a && b);
		assertThat(relationshipsPersisted, is(true));

		for (Actor actor : participatingActors) {
			ActorInEvent relationship = actor.getParticipatesInEvents().stream().filter(AinE ->
					AinE.getParticipatesIn().equals(testEvent)).findAny().orElse(null);
			assertThat(relationship, is(notNullValue()));
			session.delete(relationship);
		}

		List<Actor> actorsFromDB2 = participatingActors.stream().map(actor ->
				actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
		assertThat(actorsFromDB2.size(), is(10));

		boolean relationshipsDeleted = actorsFromDB2.stream().map(actor ->
				CollectionUtils.isEmpty(actor.getParticipatesInEvents())).reduce(true, (a, b) -> a && b);
		assertThat(relationshipsDeleted, is(true));
	}

	@Test @Rollback @Transactional
	public void saveAndDeleteActorInteractionRelationship() {
		addInteraction();

		List<Actor> participatingActors = IntStream.range(0, 10).mapToObj(index -> new Actor(ActorType.INDIVIDUAL,
				TEST_ENTITY_PREFIX + "participant-" + index)).collect(Collectors.toList());
		for (Actor actor : participatingActors) {
			actor.addParticipatesInInteraction(testInteraction);
			actorRepository.save(actor, 1);
		}

		List<Actor> actorsFromDB = participatingActors.stream().map(actor ->
				actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
		assertThat(actorsFromDB.size(), is(10));

		boolean relationshipsPersisted = actorsFromDB.stream().map(actor ->
				!CollectionUtils.isEmpty(actor.getParticipatesInInteractions())).reduce(true, (a, b) -> a && b);
		assertThat(relationshipsPersisted, is(true));

		for (Actor actor : participatingActors) {
			actor.removeParticipationInInteraction(testInteraction);
			actorRepository.save(actor, 1);
		}

		List<Actor> actorsFromDB2 = participatingActors.stream().map(actor ->
				actorRepository.findByPlatformUid(actor.getPlatformUid())).collect(Collectors.toList());
		assertThat(actorsFromDB2.size(), is(10));

		boolean relationshipsDeleted = actorsFromDB2.stream().map(actor ->
				CollectionUtils.isEmpty(actor.getParticipatesInInteractions())).reduce(true, (a, b) -> a && b);
		assertThat(relationshipsDeleted, is(true));
	}

	@Test @Rollback @Transactional
	public void saveAndLoadEventRelationships() {
		addEvent();
		Actor group = actorRepository.save(new Actor(ActorType.GROUP, TEST_ENTITY_PREFIX + "graphParent"));
		Event childEvent = eventRepository.save(new Event(EventType.MEETING, TEST_ENTITY_PREFIX + "meeting", Instant.now().toEpochMilli()));
		Interaction childInteraction = interactionRepository.save(new Interaction(InteractionType.SURVEY, group));
		childInteraction.setId(TEST_ENTITY_PREFIX + "survey");

		testEvent.addParticipatesInActor(group);
		testEvent.addChildEvent(childEvent);
		testEvent.addChildInteraction(childInteraction);
		eventRepository.save(testEvent, 1);

		Event eventFromDB = eventRepository.findByPlatformUid(testEvent.getPlatformUid());
		assertThat(eventFromDB, notNullValue());
		assertThat(CollectionUtils.isEmpty(eventFromDB.getParticipatesIn()), is(false));
		assertThat(CollectionUtils.isEmpty(eventFromDB.getChildEvents()), is(false));
		assertThat(CollectionUtils.isEmpty(eventFromDB.getChildInteractions()), is(false));
	}

	@Test @Rollback @Transactional
	public void handlesMovements() {
		addActor();
		Actor movement = actorRepository.save(new Actor(ActorType.MOVEMENT, TEST_ENTITY_PREFIX + "movement"));
		Actor account = actorRepository.save(new Actor(ActorType.ACCOUNT, TEST_ENTITY_PREFIX + "account"));
		Actor group = actorRepository.save(new Actor(ActorType.GROUP, TEST_ENTITY_PREFIX + "group"));

		ActorInActor individualInMovement = new ActorInActor(testActor, movement, Instant.now());
		ActorInActor accountInMovement = new ActorInActor(account, movement, Instant.now());
		ActorInActor groupInMovement = new ActorInActor(group, movement, Instant.now());

		session.save(individualInMovement, 0);
		session.save(accountInMovement, 0);
		session.save(groupInMovement, 0);

		Actor movementFromDb = actorRepository.findByPlatformUid(movement.getPlatformUid());
		assertThat(movementFromDb, notNullValue());

		Collection<Actor> depthFind = actorRepository.findMovementParticipantsInDepth(movement.getPlatformUid());
		assertThat(depthFind.size(), is(3));

		Actor firstActor = actorRepository.findByPlatformUid(testActor.getPlatformUid());
		assertThat(CollectionUtils.isEmpty(firstActor.getParticipatesInActors()), is(false));
	}

	@After
	public void cleanDb() {
		actorRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
		eventRepository.deleteByPlatformUidContaining(TEST_ENTITY_PREFIX);
		interactionRepository.deleteByIdContaining(TEST_ENTITY_PREFIX);
	}

	private void addActor() {
		testActor = new Actor(ActorType.INDIVIDUAL, TEST_ENTITY_PREFIX + "actor");
		actorRepository.save(testActor);
	}

	private void addEvent() {
		addActor();
		testEvent = new Event(EventType.MEETING, TEST_ENTITY_PREFIX + "event", Instant.now().toEpochMilli());
		testEvent.setCreator(testActor);
		eventRepository.save(testEvent);
	}

	private void addInteraction() {
		addActor();
		testInteraction = new Interaction(InteractionType.SURVEY, testActor);
		testInteraction.setId(TEST_ENTITY_PREFIX + "interaction");
		interactionRepository.save(testInteraction);
	}

}