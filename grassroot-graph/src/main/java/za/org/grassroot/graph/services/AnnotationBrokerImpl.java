package za.org.grassroot.graph.services;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.ogm.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.graph.domain.enums.GrassrootRelationship;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.relationship.ActorInActor;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;
import static za.org.grassroot.graph.domain.enums.ActorType.GROUP;
import static za.org.grassroot.graph.domain.enums.ActorType.INDIVIDUAL;
import static za.org.grassroot.graph.domain.enums.EventType.SAFETY_ALERT;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service @Slf4j
public class AnnotationBrokerImpl implements AnnotationBroker {

    private final ActorRepository actorRepository;
    private final EventRepository eventRepository;

    private final Session session;

    public AnnotationBrokerImpl(ActorRepository actorRepository, EventRepository eventRepository, Session session) {
        this.actorRepository = actorRepository;
        this.eventRepository = eventRepository;
        this.session = session;
    }

    @Override
    @Transactional
    public boolean annotateEntity(PlatformEntityDTO platformEntity, Map<String, String> properties, List<String> tags) {
        log.info("Wiring up entity annotation");
        GrassrootGraphEntity entity = fetchGraphEntity(platformEntity.getEntityType(), platformEntity.getPlatformId(), 0);
        log.info("Got entity: {}", entity);

        if (entity == null) {
            log.error("Error! Entity does not exist in graph, could not be annotated");
            return false;
        }

        switch (entity.getEntityType()) {
            case ACTOR:         return annotateActor((Actor) entity, properties, tags);
            case EVENT:         return annotateEvent((Event) entity, properties, tags);
            case INTERACTION:   log.error("Error! Annotations not supported for interaction entities"); return false;
            default:            log.error("Error! Unsupported entity type provided"); return false;
        }
    }

    @Override
    @Transactional
    public boolean removeEntityAnnotation(PlatformEntityDTO platformEntity, Set<String> keysToRemove, List<String> tagsToRemove) {
        log.info("Wiring up removing entity annotation");
        GrassrootGraphEntity entity = fetchGraphEntity(platformEntity.getEntityType(), platformEntity.getPlatformId(), 0);
        log.info("Got entity: {}", entity);

        if (entity == null) {
            log.error("Error! Entity does not exist in graph, could not be annotated");
            return false;
        }

        switch (entity.getEntityType()) {
            case ACTOR:         return removeActorAnnotation((Actor) entity, keysToRemove, tagsToRemove);
            case EVENT:         return removeEventAnnotation((Event) entity, keysToRemove, tagsToRemove);
            case INTERACTION:   log.error("Error! Annotations not supported for interaction entities"); return false;
            default:            log.error("Error! Unsupported entity type provided"); return false;
        }
    }

    @Override
    @Transactional
    public boolean annotateParticipation(PlatformEntityDTO tailEntity, PlatformEntityDTO headEntity, List<String> tags) {
        log.info("Wiring up participation annotation");
        GrassrootGraphEntity participant = fetchGraphEntity(tailEntity.getEntityType(), tailEntity.getPlatformId(), 0);
        log.info("Got tail entity: {}", participant);
        GrassrootGraphEntity participatesIn = fetchGraphEntity(headEntity.getEntityType(), headEntity.getPlatformId(), 0);
        log.info("Got head entity: {}", participatesIn);

        if (participant == null || participatesIn == null) {
            log.error("Error! One or both entities do not exist in graph, relationship could not be annotated");
            return false;
        }

        if (GraphEntityType.ACTOR.equals(participant.getEntityType()) && GraphEntityType.ACTOR.equals(participatesIn.getEntityType())) {
            return annotateActorInActor((Actor) participant, (Actor) participatesIn, tags);
        } else {
            log.error("Annotation only supported for actorInActor relationship entities (for now)");
            return false;
        }
    }

    @Override
    @Transactional
    public boolean removeParticipationAnnotation(PlatformEntityDTO tailEntity, PlatformEntityDTO headEntity, List<String> tagsToRemove) {
        log.info("Wiring up removing participation annotation");
        GrassrootGraphEntity participant = fetchGraphEntity(tailEntity.getEntityType(), tailEntity.getPlatformId(), 0);
        log.info("Got tail entity: {}", participant);
        GrassrootGraphEntity participatesIn = fetchGraphEntity(headEntity.getEntityType(), headEntity.getPlatformId(), 0);
        log.info("Got head entity: {}", participatesIn);

        if (participant == null || participatesIn == null) {
            log.error("Error! One or both entities do not exist in graph, relationship could not be annotated");
            return false;
        }

        if (GraphEntityType.ACTOR.equals(participant.getEntityType()) && GraphEntityType.ACTOR.equals(participatesIn.getEntityType())) {
            return removeActorInActorAnnotation((Actor) participant, (Actor) participatesIn, tagsToRemove);
        } else {
            log.error("Annotation only supported for actorInActor relationship entities (for now)");
            return false;
        }
    }

    // movement should be able to be annotated, but it is not yet incorporated in main platform.
    private boolean annotateActor(Actor actor, Map<String, String> properties, List<String> tags) {
        if (INDIVIDUAL.equals(actor.getActorType()) || GROUP.equals(actor.getActorType())) {
            actor.addProperties(properties);
            actor.addTags(tags);
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
            event.addProperties(properties);
            event.addTags(tags);
            eventRepository.save(event, 0);
            return true;
        }
    }

    private boolean removeActorAnnotation(Actor actor, Set<String> keysToRemove, List<String> tagsToRemove) {
        if (INDIVIDUAL.equals(actor.getActorType()) || GROUP.equals(actor.getActorType())) {
            actor.removeProperties(keysToRemove);
            actor.removeTags(tagsToRemove);
            actorRepository.save(actor, 0);
            return true;
        } else {
            log.error("Only individuals and groups can be annotated (for now)");
            return false;
        }
    }

    private boolean removeEventAnnotation(Event event, Set<String> keysToRemove, List<String> tagsToRemove) {
        if (SAFETY_ALERT.equals(event.getEventType())) {
            log.error("Safety events cannot be annotated");
            return false;
        } else {
            event.removeProperties(keysToRemove);
            event.removeTags(tagsToRemove);
            eventRepository.save(event, 0);
            return true;
        }
    }

    private boolean annotateActorInActor(Actor participant, Actor participatesIn, List<String> tags) {
        ActorInActor relationship = participant.getParticipatesInActors().stream()
                .filter(AinA -> AinA.getParticipatesIn().equals(participatesIn)).findAny().get();
        if (relationship == null) {
            log.error("No ActorInActor relationship entity found between {} and {}, aborting", participant, participatesIn);
            return false;
        } else {
            relationship.addTags(tags);
            session.save(relationship, 0);
            return true;
        }
    }

    private boolean removeActorInActorAnnotation(Actor participant, Actor participatesIn, List<String> tagsToRemove) {
        ActorInActor relationship = participant.getParticipatesInActors().stream()
                .filter(AinA -> AinA.getParticipatesIn().equals(participatesIn)).findAny().get();
        if (relationship == null) {
            log.error("No ActorInActor relationship entity found between {} and {}, aborting", participant, participatesIn);
            return false;
        } else {
            relationship.removeTags(tagsToRemove);
            session.save(relationship, 0);
            return true;
        }
    }

    private GrassrootGraphEntity fetchGraphEntity(GraphEntityType entityType, String Uid, int depth) {
        switch (entityType) {
            case ACTOR:         return actorRepository.findByPlatformUid(Uid, depth);
            case EVENT:         return eventRepository.findByPlatformUid(Uid, depth);
            default:            return null;
        }
    }

}