package za.org.grassroot.graph.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.graph.domain.enums.GrassrootRelationship;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.Interaction;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;
import za.org.grassroot.graph.repository.InteractionRepository;

import java.time.Instant;

@Service @Slf4j
public class ExistenceBrokerImpl implements ExistenceBroker {
    
    private final ActorRepository actorRepository;
    private final EventRepository eventRepository;
    private final InteractionRepository interactionRepository;

    public ExistenceBrokerImpl(ActorRepository actorRepository, EventRepository eventRepository, InteractionRepository interactionRepository) {
        this.actorRepository = actorRepository;
        this.eventRepository = eventRepository;
        this.interactionRepository = interactionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean entityExists(PlatformEntityDTO platformEntity) {
        log.info("Checking existence of entity with id {}", platformEntity.getPlatformId());
        switch (platformEntity.getEntityType()) {
            case ACTOR:         return actorRepository.countByPlatformUid(platformEntity.getPlatformId()) > 0;
            case EVENT:         return eventRepository.countByPlatformUid(platformEntity.getPlatformId()) > 0;
            case INTERACTION:   return interactionRepository.countById(platformEntity.getPlatformId()) > 0;
            default:            log.error("Error! Unsupported entity type provided."); return false;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean relationshipExists(PlatformEntityDTO tailEntity, PlatformEntityDTO headEntity,
                                      GrassrootRelationship.Type relationshipType) {
        log.info("Checking existence of relationship between {} and {}", tailEntity.getPlatformId(), headEntity.getPlatformId());
        switch (relationshipType) {
            case PARTICIPATES:  return doesParticipationExist(tailEntity, headEntity);
            case GENERATOR:     return doesGenerationExist(tailEntity, headEntity);
            case OBSERVES:      log.error("Observer relationship not yet implemented"); return false;
            default:            log.error("Error! Unsupported relationship type provided."); return false;
        }
    }

    @Override
    @Transactional
    public boolean addEntityToGraph(PlatformEntityDTO platformEntity) {
        log.info("Adding entity to graph: {}", platformEntity);
        switch (platformEntity.getEntityType()) {
            case ACTOR:
                Actor actor = new Actor();
                actor.setPlatformUid(platformEntity.getPlatformId());
                if (platformEntity.getActorType() != null)
                    actor.setActorType(platformEntity.getActorType());
                actorRepository.save(actor);
                return true;
            case EVENT:
                Event event = new Event();
                event.setPlatformUid(platformEntity.getPlatformId());
                event.setEventStartTimeEpochMilli(Instant.now().toEpochMilli());
                if (platformEntity.getEventType() != null)
                    event.setEventType(platformEntity.getEventType());
                eventRepository.save(event);
                return true;
            case INTERACTION:
                Interaction interaction = new Interaction();
                interaction.setId(platformEntity.getPlatformId()); // change to platformUid once implemented in main
                if (platformEntity.getInteractionType() != null)
                    interaction.setInteractionType(platformEntity.getInteractionType());
                interactionRepository.save(interaction);
                return true;
        }
        return false;
    }

    private boolean doesParticipationExist(PlatformEntityDTO tailEntity, PlatformEntityDTO headEntity) {
        GrassrootGraphEntity participant = fetchGraphEntity(tailEntity.getEntityType(), tailEntity.getPlatformId(), 0);
        GrassrootGraphEntity participatesIn = fetchGraphEntity(headEntity.getEntityType(), headEntity.getPlatformId(), 0);

        if (participant == null || participatesIn == null) {
            log.error("Error, one of the entities does not exist in graph, participation could not be checked");
            return false;
        }

        switch (participant.getEntityType()) {
            case ACTOR:         return checkActorParticipation((Actor) participant, participatesIn);
            case EVENT:         return checkEventParticipation((Event) participant, participatesIn);
            case INTERACTION:   log.error("Error! Interactions cannot participate in other entities"); return false;
            default:            log.error("Error! Participant has unsupported entity type"); return false;
        }
    }

    private boolean doesGenerationExist(PlatformEntityDTO tailEntity, PlatformEntityDTO headEntity) {
        GrassrootGraphEntity generator = fetchGraphEntity(tailEntity.getEntityType(), tailEntity.getPlatformId(), 0);
        GrassrootGraphEntity generated = fetchGraphEntity(headEntity.getEntityType(), headEntity.getPlatformId(), 0);

        if (generator == null || generated == null) {
            log.error("Error! One of the entities does not exist in graph, generation could not be checked");
            return false;
        }

        switch (generated.getEntityType()) {
            case ACTOR:         return generator.equals(((Actor) generated).getCreatedByActor());
            case EVENT:         return generator.equals(((Event) generated).getCreator());
            case INTERACTION:   return generator.equals(((Interaction) generated).getInitiator());
            default:            log.error("Error! Participant has unsupported entity type"); return false;
        }
    }

    private boolean checkActorParticipation(Actor participant, GrassrootGraphEntity participatesIn) {
        switch (participatesIn.getEntityType()) {
            case ACTOR:         return participant.getRelationshipWith((Actor) participatesIn) != null;
            case EVENT:         return participant.getRelationshipWith((Event) participatesIn) != null;
            case INTERACTION:   return participant.isParticipantIn((Interaction) participatesIn);
            default:            log.error("Error! Target has unsupported entity type"); return false;
        }
    }

    private boolean checkEventParticipation(Event participant, GrassrootGraphEntity participatesIn) {
        switch (participatesIn.getEntityType()) {
            case ACTOR:         return participant.isParticipantIn((Actor) participatesIn);
            default:            log.error("Error! Event can only participate in actor"); return false;
        }
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