package za.org.grassroot.graph.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    public boolean doesEntityExistInGraph(PlatformEntityDTO platformEntity) {
        switch (platformEntity.getEntityType()) {
            case ACTOR: return actorRepository.countByPlatformUid(platformEntity.getPlatformId()) > 0;
            case EVENT: return eventRepository.countByPlatformUid(platformEntity.getPlatformId()) > 0;
            case INTERACTION: return interactionRepository.countById(platformEntity.getPlatformId()) > 0;
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
                if (platformEntity.getActorType() != null)
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

}