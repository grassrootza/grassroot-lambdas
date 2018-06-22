package za.org.grassroot.graph.services;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.Interaction;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.relationship.ActorInActor;
import za.org.grassroot.graph.domain.relationship.ActorInEvent;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;
import za.org.grassroot.graph.repository.InteractionRepository;

import java.time.Instant;
import java.util.stream.Collectors;

@Service @Slf4j
public class RelationshipBrokerImpl implements RelationshipBroker {

    private final ActorRepository actorRepository;
    private final EventRepository eventRepository;
    private final InteractionRepository interactionRepository;

    private final Session session;

    public RelationshipBrokerImpl(ActorRepository actorRepository, EventRepository eventRepository, InteractionRepository interactionRepository, Session session) {
        this.actorRepository = actorRepository;
        this.eventRepository = eventRepository;
        this.interactionRepository = interactionRepository;
        this.session = session;
    }

    @Override
    @Transactional
    public boolean addParticipation(PlatformEntityDTO participant, PlatformEntityDTO participatesIn) {
        if (participant.isActor() && participatesIn.isActor()) {
            log.info("Wiring up actor participation");
            Actor participantActor = actorRepository.findByPlatformUid(participant.getPlatformId(), 0);
            log.info("Got participant entity: {}", participantActor);
            Actor participatesInActor = actorRepository.findByPlatformUid(participatesIn.getPlatformId(), 0);
            log.info("Got participates in entity: {}", participatesInActor);
            return addParticipantToActor(participantActor, participatesInActor);
        } else if (participant.isActor() && participatesIn.isEvent()) {
            Actor participantActor = actorRepository.findByPlatformUid(participant.getPlatformId(), 0);
            Event event = eventRepository.findByPlatformUid(participatesIn.getPlatformId(), 0);
            return addParticipantToEvent(participantActor, event);
        } else if (participant.isEvent() && participatesIn.isActor()) {
            Event participantEvent = eventRepository.findByPlatformUid(participant.getPlatformId(), 0);
            Actor actor = actorRepository.findByPlatformUid(participatesIn.getPlatformId(), 0);
            return addEventToActor(participantEvent, actor);
        }
        return false;
    }

    @Override
    @Transactional
    public boolean removeParticipation(GrassrootGraphEntity participant, GrassrootGraphEntity participatesIn) {
        if (participant.isActor() && participatesIn.isActor()) {
            Actor participantActor = actorRepository.findByPlatformUid(participant.getPlatformId(), 0);
            Actor participatesInActor = actorRepository.findByPlatformUid(participatesIn.getPlatformId(), 0);
            return removeParticipantToActor(participantActor, participatesInActor);
        } else if (participant.isActor() && participatesIn.isEvent()) {
            Actor participantActor = actorRepository.findByPlatformUid(participant.getPlatformId(), 0);
            Event event = eventRepository.findByPlatformUid(participatesIn.getPlatformId(), 0);
            return removeParticipantToEvent(participantActor, event);
        } else if (participant.isEvent() && participatesIn.isActor()) {
            Event participantEvent = eventRepository.findByPlatformUid(participant.getPlatformId(), 0);
            Actor actor = actorRepository.findByPlatformUid(participatesIn.getPlatformId(), 0);
            return removeEventToActor(participantEvent, actor);
        }
        return false;
    }

    @Override
    @Transactional
    public boolean setGeneration(PlatformEntityDTO generator, PlatformEntityDTO generated) {
        if (generator.isActor() && generated.isActor()) {
            Actor generatorActor = actorRepository.findByPlatformUid(generator.getPlatformId(), 0);
            Actor generatedActor = actorRepository.findByPlatformUid(generated.getPlatformId(), 0);
            return addGeneratingActorToActor(generatorActor, generatedActor);
        } else if (generator.isActor() && generated.isEvent()) {
            Actor actor = actorRepository.findByPlatformUid(generator.getPlatformId(), 0);
            Event event = eventRepository.findByPlatformUid(generated.getPlatformId(), 0);
            return addGeneratingActorToEvent(actor, event);
        } else if (generator.isActor() && generated.isInteraction()) {
            Actor actor = actorRepository.findByPlatformUid(generator.getPlatformId(), 0);
            Interaction interaction = interactionRepository.findByPlatformUid(generated.getPlatformId(), 0);
            return addGeneratingActorToInteraction(actor, interaction);
        } else if (generator.isEvent() && generated.isEvent()) {
            Event generatorEvent = eventRepository.findByPlatformUid(generator.getPlatformId(), 0);
            Event generatedEvent = eventRepository.findByPlatformUid(generated.getPlatformId(), 0);
            return addGeneratingEventToEvent(generatorEvent, generatedEvent);
        }
        return false;
    }

    @Override
    public boolean addObserver(PlatformEntityDTO observer, PlatformEntityDTO observed) {
        return false;
    }

    private boolean addParticipantToActor(Actor participant, Actor participatesIn) {
        validateEntitiesExist(participant, participatesIn);
        ActorInActor relationship = new ActorInActor(participant, participatesIn, Instant.now());
        session.save(relationship, 0);
        return true;
    }

    private boolean addParticipantToEvent(Actor participant, Event event) {
        validateEntitiesExist(participant, event);
        ActorInEvent relationship = new ActorInEvent(participant, event, Instant.now());
        session.save(relationship, 0);
        return true;
    }

    private boolean addEventToActor(Event participant, Actor actor) {
        validateEntitiesExist(participant, actor);
        participant.addParticipatesInActor(actor);
        eventRepository.save(participant);
        return true;
    }

    private boolean removeParticipantToActor(Actor participantActor, Actor participatesIn) {
        validateEntitiesExist(participant, participatesIn);
        ActorInActor relationship = participantActor.getParticipatesInActors().stream()
                .filter(AinA -> AinA.getParticipatesIn().equals(participatesIn)).findAny().get();
        session.delete(relationship, 0);
        return true;
    }

    private boolean removeParticipantToEvent(Actor participantActor, Event event) {
        validateEntitiesExist(participant, event);
        ActorInEvent relationship = participantActor.getParticipatesInEvents().stream()
                .filter(AinE -> AinE.getParticipatesIn().equals(event)).findAny().get();
        session.delete(relationship, 0);
        return true;
    }

    private boolean removeEventToActor(Event participant, Actor actor) {
        validateEntitiesExist(participant, actor);
        participant.removeParticipatesInActor(actor);
        eventRepository.save(participant);
        return true;
    }

    private boolean addGeneratingActorToActor(Actor generator, Actor generated) {
        validateEntitiesExist(generator, generated);
        generated.setCreatedByActor(generator);
        actorRepository.save(generated);
        return true;
    }

    private boolean addGeneratingActorToEvent(Actor generator, Event generated) {
        validateEntitiesExist(generator, generated);
        generated.setCreator(generator);
        eventRepository.save(generated);
        return true;
    }

    private boolean addGeneratingActorToInteraction(Actor generator, Interaction generated) {
        validateEntitiesExist(generator, generated);
        generated.setInitiator(generator);
        interactionRepository.save(generated);
        return true;
    }

    private boolean addGeneratingEventToEvent(Event generator, Event generated) {
        validateEntitiesExist(generator, generated);
        generator.addChildEvent(generated); // todo : make not suck
        generated.setCreator(generator); // include in addChildEvent?
        eventRepository.save(generated);
        return true;
    }

    private void validateEntitiesExist(GrassrootGraphEntity tailEntity, GrassrootGraphEntity headEntity) {
        if (tailEntity == null)
            throw new IllegalArgumentException("Error! Relationship broker assumes entities exist, but tail entity does not");
        if (headEntity == null)
            throw new IllegalArgumentException("Error! Relationship broker assumes entities exist, but head entity does not");
    }
}
