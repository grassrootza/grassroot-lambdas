package za.org.grassroot.graph.services;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.relationship.ActorInActor;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;

import java.time.Instant;

@Service @Slf4j
public class RelationshipBrokerImpl implements RelationshipBroker {

    private final ActorRepository actorRepository;
    private final EventRepository eventRepository;

    private final Session session;

    public RelationshipBrokerImpl(ActorRepository actorRepository, EventRepository eventRepository, Session session) {
        this.actorRepository = actorRepository;
        this.eventRepository = eventRepository;
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
            Actor participantActor = actorRepository.findByPlatformUid(participant.getPlatformId());
            log.info("Adding an actor to an event, actor: {}", participantActor);
            Event event = eventRepository.findByPlatformUid(participatesIn.getPlatformId());
            return addParticipantToEvent(participantActor, event);
        }
        return false;
    }

    @Override
    public boolean setGeneration(PlatformEntityDTO generator, PlatformEntityDTO generated) {
        return false;
    }

    @Override
    public boolean addObserver(PlatformEntityDTO observer, PlatformEntityDTO observed) {
        return false;
    }

    private boolean addParticipantToActor(Actor participant, Actor participatesIn) {
        log.info("Final step, adding to collection and persisting");
        validateEntitiesExist(participant, participatesIn);
//        participant.addParticipatesInActor(participatesIn);
        ActorInActor relationship = new ActorInActor(participant, participatesIn, Instant.now());
        session.save(relationship, 0);
        log.info("Actor persisted, returning");
        return true;
    }

    private boolean addParticipantToEvent(Actor participant, Event event) {
        validateEntitiesExist(participant, event);
        participant.addParticipatesInEvent(event);
        actorRepository.save(participant);
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

    private boolean addGeneratingEventToEvent(Event generator, Event generated) {
        validateEntitiesExist(generator, generated);
        generator.getChildEvents().add(generator); // todo : make not suck
        return true;
    }

    private void validateEntitiesExist(GrassrootGraphEntity tailEntity, GrassrootGraphEntity headEntity) {
        if (tailEntity == null)
            throw new IllegalArgumentException("Error! Relationship broker assumes entities exist, but tail entity does not");
        if (headEntity == null)
            throw new IllegalArgumentException("Error! Relationship broker assumes entities exist, but head entity does not");
    }
}
