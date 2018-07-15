package za.org.grassroot.graph.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.enums.GrassrootRelationship;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.Interaction;
import za.org.grassroot.graph.dto.IncomingDataObject;
import za.org.grassroot.graph.dto.IncomingGraphAction;
import za.org.grassroot.graph.dto.IncomingRelationship;
import za.org.grassroot.graph.dto.IncomingAnnotation;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;
import za.org.grassroot.graph.repository.InteractionRepository;

import java.util.List;

@Service @Slf4j
public class IncomingActionProcessorImpl implements IncomingActionProcessor {

    private final ActorRepository actorRepository;
    private final EventRepository eventRepository;
    private final InteractionRepository interactionRepository;

    private final ExistenceBroker existenceBroker;
    private final RelationshipBroker relationshipBroker;
    private final AnnotationBroker annotationBroker;

    @Autowired
    public IncomingActionProcessorImpl(ActorRepository actorRepository, EventRepository eventRepository,
                                       InteractionRepository interactionRepository, ExistenceBroker existenceBroker,
                                       RelationshipBroker relationshipBroker, AnnotationBroker annotationBroker) {
        this.actorRepository = actorRepository;
        this.eventRepository = eventRepository;
        this.interactionRepository = interactionRepository;
        this.existenceBroker = existenceBroker;
        this.relationshipBroker = relationshipBroker;
        this.annotationBroker = annotationBroker;
    }

    @Override
    public Mono<Boolean> processIncomingAction(IncomingGraphAction action) {
        return Mono.<Boolean>create(sink -> {
            log.info("Handling action, type: {}", action.getActionType());
            boolean succeeded = false;
            switch (action.getActionType()) {
                case CREATE_ENTITY:         succeeded = createEntitiesAndRelationships(action); break;
                case REMOVE_ENTITY:         succeeded = removeEntities(action.getDataObjects()); break;
                case ANNOTATE_ENTITY:       succeeded = annotateEntities(action.getAnnotations()); break;
                case CREATE_RELATIONSHIP:   succeeded = establishRelationships(action.getRelationships()); break;
                case REMOVE_RELATIONSHIP:   succeeded = removeRelationships(action.getRelationships()); break;
                case ANNOTATE_RELATIONSHIP: succeeded = annotateRelationships(action.getAnnotations()); break;
                case REMOVE_ANNOTATION:     succeeded = removeAnnotations(action.getAnnotations()); break;
            }
            sink.success(succeeded);
        });
    }

    // note: although it allows for multiple entities at once, to preserve integrity, any such multiple entities must
    // be related to each other, i.e., have relationships among each other - the validation checks if this is not the case
    private boolean createEntitiesAndRelationships(IncomingGraphAction action) {
        log.info("Handling {} entities, {} relationships", action.getDataObjects().size(), action.getRelationships().size());
        return createEntities(action.getDataObjects()) && establishRelationships(action.getRelationships());
    }

    private boolean createEntities(List<IncomingDataObject> entities) {
        log.info("Creating {} entities", entities.size());
        return entities.stream().map(this::createSingleEntity).reduce(true, (a, b) -> a && b);
    }

    private boolean createSingleEntity(IncomingDataObject dataObject) {
        PlatformEntityDTO entityDTO = new PlatformEntityDTO(dataObject.getGraphEntity().getPlatformUid(),
                dataObject.getEntityType(), dataObject.getEntitySubtype());

        if (existenceBroker.doesEntityExistInGraph(entityDTO))
            return true; // by definition, execution succeeded, as we do not do any updating in here, because of too much potential fragility

        log.info("Data object did not exist, has entity type: {}, entity: {}", dataObject.getEntityType(), dataObject);
        return persistGraphEntity(dataObject.getGraphEntity());
    }

    // this will only remove entities that are related to the primary one (first in list);
    // doesn't need a relationship call as OGM will handle that for us.
    private boolean removeEntities(List<IncomingDataObject> entities) {
        log.info("Removing {} entities", entities.size());
        return entities.stream().map(this::removeSingleEntity).reduce(true, (a, b) -> a && b);
    }

    private boolean removeSingleEntity(IncomingDataObject dataObject) {
        PlatformEntityDTO entityDTO = new PlatformEntityDTO(dataObject.getGraphEntity().getPlatformUid(),
                dataObject.getEntityType(), dataObject.getEntitySubtype());

        if (!existenceBroker.doesEntityExistInGraph(entityDTO)) {
            log.error("Entity does not exist in graph");
            return false;
        }

        log.info("Entity {} exists, deleting now.", entityDTO);
        return deleteGraphEntity(dataObject.getGraphEntity());
    }

    private boolean establishRelationships(List<IncomingRelationship> relationships) {
        log.info("Creating {} relationships", relationships.size());
        return relationships.stream().map(this::createSingleRelationship).reduce(true, (a, b) -> a && b);
    }

    private boolean createSingleRelationship(IncomingRelationship relationship) {
        PlatformEntityDTO tailEntity = new PlatformEntityDTO(relationship.getTailEntityPlatformId(),
                relationship.getTailEntityType(), relationship.getTailEntitySubtype());
        PlatformEntityDTO headEntity = new PlatformEntityDTO(relationship.getHeadEntityPlatformId(),
                relationship.getHeadEntityType(), relationship.getHeadEntitySubtype());

        if (!entitiesExist(tailEntity, headEntity)) {
            log.error("Entities did not previously exist in graph and could not be added, aborting");
            return false;
        }

        switch (relationship.getRelationshipType()) {
            case PARTICIPATES:  return relationshipBroker.addParticipation(tailEntity, headEntity);
            case GENERATOR:     return relationshipBroker.setGeneration(tailEntity, headEntity);
            case OBSERVES:      log.error("Observer relationship not yet implemented"); return false;
            default:            log.error("Unsupported relationship type provided"); return false;
        }
    }

    // again, only relevant from single, central node
    private boolean removeRelationships(List<IncomingRelationship> relationships) {
        log.info("Removing {} relationships", relationships.size());
        return relationships.stream().map(this::removeSingleRelationship).reduce(true, (a, b) -> a && b);
    }

    private boolean removeSingleRelationship(IncomingRelationship relationship) {
        PlatformEntityDTO tailEntity = new PlatformEntityDTO(relationship.getTailEntityPlatformId(),
                relationship.getTailEntityType(), relationship.getTailEntitySubtype());
        PlatformEntityDTO headEntity = new PlatformEntityDTO(relationship.getHeadEntityPlatformId(),
                relationship.getHeadEntityType(), relationship.getHeadEntitySubtype());

        if (!existenceBroker.doesEntityExistInGraph(tailEntity) || !existenceBroker.doesEntityExistInGraph(headEntity))
            return true;

        switch (relationship.getRelationshipType()) {
            case PARTICIPATES:  return relationshipBroker.removeParticipation(tailEntity, headEntity);
            case GENERATOR:     log.error("Error! Cannot remove generator relationship"); return false;
            case OBSERVES:      log.error("Observer relationship not yet implemented"); return false;
            default:            log.error("Unsupported relationship type provided"); return false;
        }
    }

    private boolean annotateEntities(List<IncomingAnnotation> annotations) {
        log.info("Applying entity annotations: {}", annotations);
        return annotations.stream().map(this::annotateSingleEntity).reduce(true, (a, b) -> a && b);
    }

    private boolean annotateSingleEntity(IncomingAnnotation annotation) {
        IncomingDataObject entity = annotation.getEntity();
        PlatformEntityDTO entityDTO = new PlatformEntityDTO(entity.getGraphEntity().getPlatformUid(),
                entity.getEntityType(), entity.getEntitySubtype());

        if (!existenceBroker.doesEntityExistInGraph(entityDTO)) {
            if (!existenceBroker.addEntityToGraph(entityDTO)) {
                log.error("Entity did not previously exist in graph and could not be added, aborting");
                return false;
            }
        }

        log.info("Verified entity exists, annotating entity to graph");
        return annotationBroker.annotateEntity(entityDTO, annotation.getProperties(), annotation.getTags());
    }

    private boolean annotateRelationships(List<IncomingAnnotation> annotations) {
        log.info("Applying relationship annotations: {}", annotations);
        return annotations.stream().map(this::annotateSingleRelationship).reduce(true, (a, b) -> a && b);
    }

    private boolean annotateSingleRelationship(IncomingAnnotation annotation) {
        IncomingRelationship relationship = annotation.getRelationship();
        PlatformEntityDTO tailEntity = new PlatformEntityDTO(relationship.getTailEntityPlatformId(),
                relationship.getTailEntityType(), relationship.getTailEntitySubtype());
        PlatformEntityDTO headEntity = new PlatformEntityDTO(relationship.getHeadEntityPlatformId(),
                relationship.getHeadEntityType(), relationship.getHeadEntitySubtype());

        if (!entitiesExist(tailEntity, headEntity)) {
            log.error("Entities did not previously exist in graph and could not be added, aborting");
            return false;
        }

        if (!existenceBroker.doesRelationshipEntityExist(tailEntity, headEntity, relationship.getRelationshipType())) {
            if (!isValidAnnotation(tailEntity, headEntity, relationship.getRelationshipType())) {
                log.error("Invalid relationship annotation, only supporting ActorInActor at the moment");
                return false;
            }

            if (!relationshipBroker.addParticipation(tailEntity, headEntity)) {
                log.error("Relationship entity did not previously exist in graph and could not be added, aborting");
                return false;
            }
        }

        log.info("Verified relationship exists, annotating relationship to graph");
        switch (relationship.getRelationshipType()) {
            case PARTICIPATES:  return annotationBroker.annotateParticipation(tailEntity, headEntity, annotation.getTags());
            default:            log.error("Error! Only supporting participation annotation at the moment."); return false;
        }
    }

    private boolean removeAnnotations(List<IncomingAnnotation> annotations) {
        log.info("Removing annotations: {}", annotations);
        return annotations.stream().map(this::removeSingleAnnotation).reduce(true, (a, b) -> a && b);
    }

    private boolean removeSingleAnnotation(IncomingAnnotation annotation) {
        if (annotation.getEntity() == null && annotation.getRelationship() == null) {
            log.error("Error! Annotation does not contain entity or relationship, aborting");
            return false;
        }
        if (annotation.getEntity() != null && annotation.getRelationship() != null) {
            log.error("Error! One annotation cannot serve for both a relationship and entity"); // because tags overlap.
            return false;
        }
        if (annotation.getEntity() != null) return removeEntityAnnotation(annotation);
        else return removeRelationshipAnnotation(annotation);
    }

    private boolean removeEntityAnnotation(IncomingAnnotation annotation) {
        IncomingDataObject entity = annotation.getEntity();
        PlatformEntityDTO entityDTO = new PlatformEntityDTO(entity.getGraphEntity().getPlatformUid(),
                entity.getEntityType(), entity.getEntitySubtype());

        if (!existenceBroker.doesEntityExistInGraph(entityDTO))
            return true;

        log.info("Verified entity exists, removing entity annotation from graph");
        return annotationBroker.removeEntityAnnotation(entityDTO, annotation.getKeysToRemove(), annotation.getTags());
    }

    private boolean removeRelationshipAnnotation(IncomingAnnotation annotation) {
        IncomingRelationship relationship = annotation.getRelationship();
        PlatformEntityDTO tailEntity = new PlatformEntityDTO(relationship.getTailEntityPlatformId(),
                relationship.getTailEntityType(), relationship.getTailEntitySubtype());
        PlatformEntityDTO headEntity = new PlatformEntityDTO(relationship.getHeadEntityPlatformId(),
                relationship.getHeadEntityType(), relationship.getHeadEntitySubtype());

        if (!existenceBroker.doesEntityExistInGraph(tailEntity) || !existenceBroker.doesEntityExistInGraph(headEntity) ||
                !existenceBroker.doesRelationshipEntityExist(tailEntity, headEntity, relationship.getRelationshipType()))
            return true;

        log.info("Verified relationship exists, removing relationship annotation from graph");
        switch (relationship.getRelationshipType()) {
            case PARTICIPATES:  return annotationBroker.removeParticipationAnnotation(tailEntity, headEntity, annotation.getTags());
            default:            log.error("Error! Only supporting participation annotation at the moment."); return false;
        }
    }

    private boolean persistGraphEntity(GrassrootGraphEntity graphEntity) {
        try {
            switch (graphEntity.getEntityType()) {
                case ACTOR:         actorRepository.save((Actor) graphEntity, 0); break;
                case EVENT:         eventRepository.save((Event) graphEntity, 0); break;
                case INTERACTION:   interactionRepository.save((Interaction) graphEntity, 0); break;
            }
            return true;
        } catch (IllegalArgumentException|ClassCastException e) {
            log.error("Could not persist entity in graph", e);
            return false;
        }
    }

    private boolean deleteGraphEntity(GrassrootGraphEntity graphEntity) {
        try {
            switch (graphEntity.getEntityType()) {
                case ACTOR:         actorRepository.delete((Actor) graphEntity); break;
                case EVENT:         eventRepository.delete((Event) graphEntity); break;
                case INTERACTION:   interactionRepository.delete((Interaction) graphEntity); break;
            }
            return true;
        } catch (IllegalArgumentException|ClassCastException e) {
            log.error("Could not delete entity from graph", e);
            return false;
        }
    }

    private boolean entitiesExist(PlatformEntityDTO tailEntity, PlatformEntityDTO headEntity) {
        boolean entitiesExist = true;

        if (!existenceBroker.doesEntityExistInGraph(tailEntity))
            entitiesExist = existenceBroker.addEntityToGraph(tailEntity);

        if (!existenceBroker.doesEntityExistInGraph(headEntity))
            entitiesExist = entitiesExist && existenceBroker.addEntityToGraph(headEntity);

        return entitiesExist;
    }

    // update validity conditions if range of relationship entities annotated expands (only supports ActorInActor now).
    private boolean isValidAnnotation(PlatformEntityDTO tailEntity, PlatformEntityDTO headEntity,
                                      GrassrootRelationship.Type relationshipType) {
        return (relationshipType == GrassrootRelationship.Type.PARTICIPATES && tailEntity.isActor() && headEntity.isActor());
    }

}