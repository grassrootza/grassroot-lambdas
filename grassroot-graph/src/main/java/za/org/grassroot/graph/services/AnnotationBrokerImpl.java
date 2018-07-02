package za.org.grassroot.graph.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.Interaction;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;
import za.org.grassroot.graph.repository.InteractionRepository;

@Service @Slf4j
public class AnnotationBrokerImpl implements AnnotationBroker {

    private final ActorRepository actorRepository;
    private final EventRepository eventRepository;
    private final InteractionRepository interactionRepository;

    public AnnotationBrokerImpl(ActorRepository actorRepository, EventRepository eventRepository,
                                InteractionRepository interactionRepository) {
        this.actorRepository = actorRepository;
        this.eventRepository = eventRepository;
        this.interactionRepository = interactionRepository;
    }

    @Override
    @Transactional
    public boolean annotateEntity(PlatformEntityDTO platformEntity, AnnotationInfoDTO annotationInfo) {
        log.info("Wiring up annotation");
        GrassrootGraphEntity entity = fetchGraphEntity(platformEntity.getEntityType(), platformEntity.getPlatformId(), 0);
        log.info("Got entity to annotate: {}", entity);

        switch (entity.getEntityType()) {
            case ACTOR:         return annotateActor((Actor) entity, annotationInfo);
            case EVENT:         return annotateEvent((Event) entity, annotationInfo);
            case INTERACTION:   log.error("Interaction annotation not currently supported"); return false;
            default:            log.error("Unsupported entity type provided"); return false;
        }
    }

    // movement should be annotated, but not yet incorporated in main platform.
    private boolean annotateActor(Actor actor, AnnotationInfoDTO annotationInfo) {
        if (actor.getActorType().equals("INDIVIDUAL") || actor.getActorType().equals("GROUP")) {
            actor.setDescription(annotationInfo.getDescription());
            actor.setTags(annotationInfo.getTags());
            actor.setLanguage(annotationInfo.getLanguage());
            actor.setLocation(annotationInfo.getLocation());
            actorRepository.save(actor, 0);
            return true;
        } else {
            log.error("Only individuals and groups can be annotated.");
            return false;
        }
    }

    private boolean annotateEvent(Event event, AnnotationInfoDTO annotationInfo) {
        if (event.getEventType().equals("SAFETY_ALERT")) {
            log.error("Safety events cannot be annotated");
            return false;
        } else {
            event.setDescription(annotationInfo.getDescription());
            event.setTags(annotationInfo.getTags());
            event.setLocation(annotationInfo.getLocation());
            eventRepository.save(event, 0);
            return true;
        }
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