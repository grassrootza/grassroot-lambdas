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

import java.util.List;
import java.util.Map;

import static za.org.grassroot.graph.domain.enums.ActorType.GROUP;
import static za.org.grassroot.graph.domain.enums.ActorType.INDIVIDUAL;
import static za.org.grassroot.graph.domain.enums.EventType.SAFETY_ALERT;

@Service @Slf4j
public class AnnotationBrokerImpl implements AnnotationBroker {

    private final ActorRepository actorRepository;
    private final EventRepository eventRepository;

    public AnnotationBrokerImpl(ActorRepository actorRepository, EventRepository eventRepository) {
        this.actorRepository = actorRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    @Transactional
    public boolean annotateEntity(PlatformEntityDTO platformEntity, Map<String, String> properties, List<String> tags) {
        log.info("Wiring up annotation");
        GrassrootGraphEntity entity = fetchGraphEntity(platformEntity.getEntityType(), platformEntity.getPlatformId(), 0);
        log.info("Got entity to annotate: {}", entity);

        if (entity == null) {
            log.error("Error, sent a null entity to process");
            return false;
        }

        switch (entity.getEntityType()) {
            case ACTOR:         return annotateActor((Actor) entity, properties, tags);
            case EVENT:         return annotateEvent((Event) entity, properties, tags);
            case INTERACTION:   log.error("Interaction annotation not currently supported"); return false;
            default:            log.error("Unsupported entity type provided"); return false;
        }
    }

    // movement should be able to be annotated, but it is not yet incorporated in main platform.
    private boolean annotateActor(Actor actor, Map<String, String> properties, List<String> tags) {
        if (INDIVIDUAL.equals(actor.getActorType()) || GROUP.equals(actor.getActorType())) {
            actor.setProperties(properties);
            actor.setTags(tags);
            actorRepository.save(actor, 0);
            return true;
        } else {
            log.error("Only individuals and groups can be annotated (for now)");
            return false;
        }
    }

    private boolean annotateEvent(Event event, Map<String, String> properties, List<String> tags) {
        if (SAFETY_ALERT.equals(event.getEventType())) {
            log.error("Safety events cannot be annotated");
            return false;
        } else {
            event.setProperties(properties);
            event.setTags(tags);
            eventRepository.save(event, 0);
            return true;
        }
    }

    private GrassrootGraphEntity fetchGraphEntity(GraphEntityType entityType, String platformId, int depth) {
        switch (entityType) {
            case ACTOR:         return actorRepository.findByPlatformUid(platformId, depth);
            case EVENT:         return eventRepository.findByPlatformUid(platformId, depth);
            default:            return null;
        }
    }

}