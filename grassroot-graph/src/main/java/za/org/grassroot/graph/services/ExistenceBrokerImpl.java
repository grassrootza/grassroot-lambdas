package za.org.grassroot.graph.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
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
import java.util.stream.Collectors;

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
    public boolean doesEntityExistInGraph(PlatformEntityDTO platformEntity) {
        switch (platformEntity.getEntityType()) {
            case ACTOR:         return actorRepository.countByPlatformUid(platformEntity.getPlatformId()) > 0;
            case EVENT:         return eventRepository.countByPlatformUid(platformEntity.getPlatformId()) > 0;
            case INTERACTION:   return interactionRepository.countById(platformEntity.getPlatformId()) > 0;
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean doesRelationshipEntityExist(PlatformEntityDTO tailEntity, PlatformEntityDTO headEntity,
                                               GrassrootRelationship.Type relationshipType) {
        switch (relationshipType) {
            case PARTICIPATES:  return doesParticipationExist(tailEntity, headEntity);
            case GENERATOR:     log.error("Generator relationship check not currently supported"); return false;
            case OBSERVES:      log.error("Observer relationship check not currently supported"); return false;
        }
        return false;
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
                if (platformEntity.getInteractionType() != null)
                    interaction.setInteractionType(platformEntity.getInteractionType());
                interactionRepository.save(interaction);
                return true;
        }
        return false;
    }

    private boolean doesParticipationExist(PlatformEntityDTO tailEntity, PlatformEntityDTO headEntity) {
        Actor participant = (Actor) fetchGraphEntity(tailEntity.getEntityType(), tailEntity.getPlatformId(), 0);
        GrassrootGraphEntity participatesIn = fetchGraphEntity(headEntity.getEntityType(), headEntity.getPlatformId(), 0);

        if (participant == null || participatesIn == null) {
            log.error("Error, one of the entities does not exist in graph, relationship could not be verified");
            return false;
        }

        switch (participatesIn.getEntityType()) {
            case ACTOR: return !CollectionUtils.isEmpty(participant.getParticipatesInActors().stream()
                    .filter(AinA -> AinA.getParticipatesIn().equals((Actor) participatesIn)).collect(Collectors.toSet()));
            case EVENT: return !CollectionUtils.isEmpty(participant.getParticipatesInEvents().stream()
                    .filter(AinE -> AinE.getParticipatesIn().equals((Event) participatesIn)).collect(Collectors.toSet()));
            default:    log.error("Existence check only supported for ActorInActor and ActorInEvent"); return false;
        }
    }

    private GrassrootGraphEntity fetchGraphEntity(GraphEntityType entityType, String Uid, int depth) {
        switch (entityType) {
            case ACTOR: return actorRepository.findByPlatformUid(Uid, depth);
            case EVENT: return eventRepository.findByPlatformUid(Uid, depth);
            default:    return null;
        }
    }

}