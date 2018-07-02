package za.org.grassroot.graph.services;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.Interaction;
import za.org.grassroot.graph.domain.relationship.ActorInActor;
import za.org.grassroot.graph.domain.relationship.ActorInEvent;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;
import za.org.grassroot.graph.repository.InteractionRepository;

import java.time.Instant;

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
    public boolean addParticipation(PlatformEntityDTO participantDTO, PlatformEntityDTO participatesInDTO) {
        log.info("Wiring up participation addition");
        GrassrootGraphEntity participant = fetchGraphEntity(participantDTO.getEntityType(), participantDTO.getPlatformId(), 0);
        log.info("Got participant entity: {}", participant);
        GrassrootGraphEntity participatesIn = fetchGraphEntity(participatesInDTO.getEntityType(), participatesInDTO.getPlatformId(), 0);
        log.info("Got participates in entity: {}", participatesIn);

        switch (participatesIn.getEntityType()) {
            case ACTOR:         return addParticipantToActor(participant, (Actor) participatesIn);
            case EVENT:         return addParticipantToEvent(participant, (Event) participatesIn);
            case INTERACTION:   return addParticipantToInteraction(participant, (Interaction) participatesIn);
            default:            log.error("Unsupported entity type provided"); return false;
        }
    }

    @Override
    @Transactional
    public boolean removeParticipation(PlatformEntityDTO participantDTO, PlatformEntityDTO participatesInDTO) {
        log.info("Wiring up participation removal");
        GrassrootGraphEntity participant = fetchGraphEntity(participantDTO.getEntityType(), participantDTO.getPlatformId(), 0);
        log.info("Got participant entity: {}", participant);
        GrassrootGraphEntity participatesIn = fetchGraphEntity(participatesInDTO.getEntityType(), participatesInDTO.getPlatformId(), 0);
        log.info("Got participates in entity: {}", participatesIn);

        switch (participatesIn.getEntityType()) {
            case ACTOR:         return removeParticipantFromActor(participant, (Actor) participatesIn);
            case EVENT:         return removeParticipantFromEvent(participant, (Event) participatesIn);
            case INTERACTION:   return removeParticipantFromInteraction(participant, (Interaction) participatesIn);
            default:            log.error("Unsupported entity type provided"); return false;
        }
    }

    @Override
    @Transactional
    public boolean setGeneration(PlatformEntityDTO generatorDTO, PlatformEntityDTO generatedDTO) {
        log.info("Wiring up generation");
        GrassrootGraphEntity generator = fetchGraphEntity(generatorDTO.getEntityType(), generatorDTO.getPlatformId(), 0);
        log.info("Got generator entity: {}", generator);
        GrassrootGraphEntity generated = fetchGraphEntity(generatedDTO.getEntityType(), generatedDTO.getPlatformId(), 0);
        log.info("Got generated entity: {}", generated);

        switch (generated.getEntityType()) {
            case ACTOR:         return setGeneratorForActor(generator, (Actor) generated);
            case EVENT:         return setGeneratorForEvent(generator, (Event) generated);
            case INTERACTION:   return setGeneratorForInteraction(generator, (Interaction) generated);
            default:            log.error("Unsupported entity type provided"); return false;
        }
    }

    @Override
    public boolean addObserver(PlatformEntityDTO observer, PlatformEntityDTO observed) {
        return false;
    }

    private boolean addParticipantToActor(GrassrootGraphEntity participantEntity, Actor actor) {
        if (participantEntity.isActor()) {
            ActorInActor relationship = new ActorInActor((Actor) participantEntity, actor, Instant.now());
            session.save(relationship, 0);
            return true;
        } else if (participantEntity.isEvent()) {
            Event participant = (Event) participantEntity;
            participant.addParticipatesInActor(actor);
            eventRepository.save(participant, 0);
            return true;
        }
        log.error("An interaction cannot participate in an actor");
        return false;
    }

    private boolean addParticipantToEvent(GrassrootGraphEntity participantEntity, Event event) {
        if (participantEntity.isActor()) {
            ActorInEvent relationship = new ActorInEvent((Actor) participantEntity, event);
            session.save(relationship, 0);
            return true;
        }
        log.error("Only actors can participate in events");
        return false;
    }

    private boolean addParticipantToInteraction(GrassrootGraphEntity participantEntity, Interaction interaction) {
        if (participantEntity.isActor()) {
            Actor participant = (Actor) participantEntity;
            participant.addParticipatesInInteraction(interaction);
            actorRepository.save(participant, 0);
            return true;
        }
        log.error("Only actors can participate in interactions");
        return false;
    }

    private boolean removeParticipantFromActor(GrassrootGraphEntity participantEntity, Actor actor) {
        if (participantEntity.isActor()) {
            Actor participant = (Actor) participantEntity;
            ActorInActor relationship = participant.getParticipatesInActors().stream()
                    .filter(AinA -> AinA.getParticipatesIn().equals(actor)).findAny().get();
            session.delete(relationship);
            return true;
        } else if (participantEntity.isEvent()) {
            Event participant = (Event) participantEntity;
            participant.removeParticipatesInActor(actor);
            eventRepository.save(participant);
            return true;
        }
        log.error("Interaction cannot participate in actor");
        return false;
    }

    private boolean removeParticipantFromEvent(GrassrootGraphEntity participantEntity, Event event) {
        if (participantEntity.isActor()) {
            Actor participant = (Actor) participantEntity;
            ActorInEvent relationship = participant.getParticipatesInEvents().stream()
                    .filter(AinE -> AinE.getParticipatesIn().equals(event)).findAny().get();
            session.delete(relationship);
            return true;
        }
        log.error("Only actors can participate in events");
        return false;
    }

    private boolean removeParticipantFromInteraction(GrassrootGraphEntity participantEntity, Interaction interaction) {
        if (participantEntity.isActor()) {
            Actor participant = (Actor) participantEntity;
            participant.removeParticipationInInteraction(interaction);
            actorRepository.save(participant, 0);
            return true;
        }
        log.error("Only actors can participate in interactions");
        return false;
    }

    private boolean setGeneratorForActor(GrassrootGraphEntity generatorEntity, Actor actor) {
        if (generatorEntity.isActor()) {
            actor.setCreatedByActor((Actor) generatorEntity);
            actorRepository.save(actor);
            return true;
        }
        log.error("Only actors can generate actors");
        return false;
    }

    private boolean setGeneratorForEvent(GrassrootGraphEntity generatorEntity, Event event) {
        if (generatorEntity.isActor()) {
            event.setCreator(generatorEntity);
            eventRepository.save(event);
            return true;
        } else if (generatorEntity.isEvent()) {
            event.setCreator(generatorEntity);
            Event generator = (Event) generatorEntity;
            generator.addChildEvent(event);
            eventRepository.save(generator, 1);
            return true;
        }
        log.error("Interactions cannot generate events");
        return false;
    }

    private boolean setGeneratorForInteraction(GrassrootGraphEntity generatorEntity, Interaction interaction) {
        if (generatorEntity.isActor()) {
            interaction.setInitiator((Actor) generatorEntity);
            interactionRepository.save(interaction);
            return true;
        }
        log.error("Only actors can generate interactions");
        return false;
    }

    private GrassrootGraphEntity fetchGraphEntity(GraphEntityType entityType, String platformId, int depth) {
        switch (entityType) {
            case ACTOR:         return actorRepository.findByPlatformUid(platformId, depth);
            case EVENT:         return eventRepository.findByPlatformUid(platformId, depth);
            case INTERACTION:   return interactionRepository.findById(platformId, depth).get();
            default:            return null;
        }
    }

}