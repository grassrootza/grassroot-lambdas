package za.org.grassroot.graph.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;

@Service @Slf4j
public class RelationshipBrokerImpl implements RelationshipBroker {

    private final ActorRepository actorRepository;
    private final EventRepository eventRepository;

    public RelationshipBrokerImpl(ActorRepository actorRepository, EventRepository eventRepository) {
        this.actorRepository = actorRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    public boolean addParticipation(PlatformEntityDTO participant, PlatformEntityDTO participatesIn) {
        if (participant.isActor() && participatesIn.isActor()) {
            Actor participantActor = actorRepository.findByPlatformUid(participant.getPlatformId());
            log.info("participant actor: {}", participantActor);
            Actor participatesInActor = actorRepository.findByPlatformUid(participatesIn.getPlatformId());
            log.info("participates in actor: {}", participatesInActor);
            return addParticipantToActor(participantActor, participatesInActor);
        } else if (participant.isActor() && participatesIn.isEvent()) {
            Actor participantActor = actorRepository.findByPlatformUid(participant.getPlatformId());
            log.info("adding an actor to an event, actor: {}", participantActor);
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
        validateEntitiesExist(participant, participatesIn);
        participant.addParticipatesInActor(participatesIn);
        actorRepository.save(participant);
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

    /*
    private boolean addRelationshipToEvent(Event event, GrassrootGraphEntity headEntity, GrassrootRelationship.Type type) {
        if (GrassrootRelationship.Type.PARTICIPATES.equals(type)) {
            headEntity.addParticipatingEvent(event);
            return persistGraphEntity(headEntity);
        } else if (GrassrootRelationship.Type.GENERATOR.equals(type) && headEntity.isEvent()) {
            headEntity.addGenerator(event);
            return persistGraphEntity(headEntity);
        }
        throw new IllegalArgumentException("Illegal relationship & head entity combination in adding relationship to event");
    }

    private boolean addRelationshipToInteraction(Interaction interaction, GrassrootGraphEntity headEntity, GrassrootRelationship.Type type) {
        throw new IllegalArgumentException("At present interaction is exclusively a head entity, and cannot participate or generate any other entity");
    }

     */
}
