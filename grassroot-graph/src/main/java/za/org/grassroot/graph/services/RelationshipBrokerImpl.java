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

        if (participant == null || participatesIn == null) {
            log.error("Error, one or both of the entities does not exist in graph, relationship could not be created");
            return false;
        }

        switch (participatesIn.getEntityType()) {
            case ACTOR:         return addParticipantToActor(participant, (Actor) participatesIn);
            case EVENT:         return addParticipantToEvent(participant, (Event) participatesIn);
            case INTERACTION:   return addParticipantToInteraction(participant, (Interaction) participatesIn);
            default:            log.error("Unsupported entity type provided for target entity"); return false;
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

        if (participant == null || participatesIn == null) {
            log.error("Error, one or both of the entities does not exist in graph, relationship could not be removed");
            return false;
        }

        switch (participatesIn.getEntityType()) {
            case ACTOR:         return removeParticipantFromActor(participant, (Actor) participatesIn);
            case EVENT:         return removeParticipantFromEvent(participant, (Event) participatesIn);
            case INTERACTION:   return removeParticipantFromInteraction(participant, (Interaction) participatesIn);
            default:            log.error("Unsupported entity type provided for target entity"); return false;
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

        if (generator == null || generated == null) {
            log.error("Error, one or both of the entities does not exist in graph, relationship could not be created");
            return false;
        }

        switch (generated.getEntityType()) {
            case ACTOR:         return setGeneratorForActor(generator, (Actor) generated);
            case EVENT:         return setGeneratorForEvent(generator, (Event) generated);
            case INTERACTION:   return setGeneratorForInteraction(generator, (Interaction) generated);
            default:            log.error("Unsupported entity type provided for target entity"); return false;
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
            eventRepository.save(participant, 1);
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
            actorRepository.save(participant, 1);
            return true;
        }
        log.error("Only actors can participate in interactions");
        return false;
    }

    private boolean removeParticipantFromActor(GrassrootGraphEntity participantEntity, Actor actor) {
        if (participantEntity.isActor()) {
            ActorInActor relationship = ((Actor) participantEntity).getRelationshipWith(actor);
            session.delete(relationship);
            return true;
        } else if (participantEntity.isEvent()) {
            Event participant = (Event) participantEntity;
            participant.removeParticipatesInActor(actor);
            eventRepository.save(participant, 1);
            return true;
        }
        log.error("Interaction cannot participate in actor");
        return false;
    }

    private boolean removeParticipantFromEvent(GrassrootGraphEntity participantEntity, Event event) {
        if (participantEntity.isActor()) {
            ActorInEvent relationship = ((Actor) participantEntity).getRelationshipWith(event);
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
            actorRepository.save(participant, 1);
            return true;
        }
        log.error("Only actors can participate in interactions");
        return false;
    }

    private boolean setGeneratorForActor(GrassrootGraphEntity generatorEntity, Actor actor) {
        if (generatorEntity.isActor()) {
            actor.setCreatedByActor((Actor) generatorEntity);
            actorRepository.save(actor, 1);
            return true;
        }
        log.error("Only actors can generate actors");
        return false;
    }

    private boolean setGeneratorForEvent(GrassrootGraphEntity generatorEntity, Event event) {
        if (generatorEntity.isActor()) {
            event.setCreator(generatorEntity);
            eventRepository.save(event, 1);
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
            interactionRepository.save(interaction, 1);
            return true;
        }
        log.error("Only actors can generate interactions");
        return false;
    }

    private GrassrootGraphEntity fetchGraphEntity(GraphEntityType entityType, String Uid, int depth) {
        switch (entityType) {
            case ACTOR:         return actorRepository.findByPlatformUid(Uid, depth);
            case EVENT:         return eventRepository.findByPlatformUid(Uid, depth);
            case INTERACTION:   return interactionRepository.findById(Uid, depth).orElse(null);
            default:            return null;
        }
    }

}