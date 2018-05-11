package za.org.grassroot.graph.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.Interaction;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.enums.GrassrootRelationship;
import za.org.grassroot.graph.domain.repository.ActorRepository;
import za.org.grassroot.graph.domain.repository.EventRepository;
import za.org.grassroot.graph.domain.repository.InteractionRepository;
import za.org.grassroot.graph.dto.IncomingDataObject;
import za.org.grassroot.graph.dto.IncomingGraphAction;
import za.org.grassroot.graph.dto.IncomingRelationship;

import java.util.List;

@Service @Slf4j
public class IncomingActionProcessorImpl implements IncomingActionProcessor {

    private final ActorRepository actorRepository;
    private final EventRepository eventRepository;
    private final InteractionRepository interactionRepository;

    @Autowired
    public IncomingActionProcessorImpl(ActorRepository actorRepository, EventRepository eventRepository, InteractionRepository interactionRepository) {
        this.actorRepository = actorRepository;
        this.eventRepository = eventRepository;
        this.interactionRepository = interactionRepository;
    }

    @Override
    public boolean processIncomingAction(IncomingGraphAction action) {
        switch (action.getActionType()) {
            case CREATE_ENTITY:         return createOrUpdateEntities(action);
            case ALTER_ENTITY:          return createOrUpdateEntities(action);
            case REMOVE_ENTITY:         return removeEntities(action);
            case CREATE_RELATIONSHIP:   return establishRelationships(action.getRelationships());
            case ALTER_RELATIONSHIP:    return false; // not implemented yet
            case REMOVE_RELATIONSHIP:   return removeRelationships(action.getRelationships());
        }
        return false;
    }

    // note: although it allows for multiple entities at once, to preserve integrity, any such multiple entities must
    // be related to each other, i.e., have relationships among each other - the validation checks if this is not the case
    private boolean createOrUpdateEntities(IncomingGraphAction action) {
        boolean executionSucceeded = false;

        // first, create or update the entities, including possibly multiple (e.g., if packet is group creation)
        if (action.getDataObjects() != null) {
            executionSucceeded = action.getDataObjects().stream()
                    .map(this::createOrUpdateSingleEntity).reduce(true, (a, b) -> a && b); // can do as allMatch but find that a bit opaque

        }
        // second, wire up any relationships - if we need to (OGM may do this for us - keep eye on it)
        if (action.getRelationships() != null) {
            executionSucceeded = executionSucceeded && action.getRelationships().stream()
                    .map(this::createSingleRelationship).reduce(true, (a, b) -> a && b);
        }

        // then, return true if everything went to plan
        return executionSucceeded;
    }

    private boolean createOrUpdateSingleEntity(IncomingDataObject dataObject) {
        try {
            switch (dataObject.getEntityType()) {
                case ACTOR:         actorRepository.save((Actor) dataObject.getGraphEntity()); break;
                case EVENT:         eventRepository.save((Event) dataObject.getGraphEntity()); break;
                case INTERACTION:   interactionRepository.save((Interaction) dataObject.getGraphEntity()); break;
            }
            return true;
        } catch (IllegalArgumentException|ClassCastException e) {
            log.error("error persisting graph entity: ", e);
            return false;
        }
    }

    // this will only remove entities that are related to the primary one (first in list); doesn't need a relationship call
    // as OGM will handle that for us
    private boolean removeEntities(IncomingGraphAction action) {
        return action.getDataObjects().stream().map(this::removeSingleEntity).reduce(true, (a, b) -> a && b);
    }

    private boolean removeSingleEntity(IncomingDataObject dataObject) {
        try {
            switch (dataObject.getEntityType()) {
                case ACTOR:         actorRepository.delete((Actor) dataObject.getGraphEntity()); break;
                case EVENT:         eventRepository.delete((Event) dataObject.getGraphEntity()); break;
                case INTERACTION:   interactionRepository.delete((Interaction) dataObject.getGraphEntity()); break;
            }
            return true;
        } catch (IllegalArgumentException e) {
            log.error("error removing graph entity: ", e);
            return false;
        }
    }

    // may need to do this in order, hence a list
    private boolean establishRelationships(List<IncomingRelationship> relationships) {
        return relationships.stream().map(this::createSingleRelationship).reduce(true, (a, b) -> a && b);
    }

    // now, this is the heart of the beast. complex and ugly because of profusion of relationship types
    // by far the most elegant solution _would have been_ RelationshipEntities with generics
    // _but_ it doesn't seem like the OGM supports that yet. So would have had massive entity profusion.
    // thus decided best to consolidate messiness in single entity, inside here. definitely change if OGM implements generics.
    private boolean createSingleRelationship(IncomingRelationship relationship) {
        GrassrootGraphEntity headEntity = fetchGraphEntity(relationship.getHeadEntityType(), relationship.getHeadEntityPlatformId());
        GrassrootGraphEntity tailEntity = fetchGraphEntity(relationship.getTailEntityType(), relationship.getTailEntityPlatformId());

        if (headEntity == null || tailEntity == null) {
            log.error("received a relationship that has invalid head or tail");
            return false;
        }

        // remember head = destination, tail = origin
        try {
            switch (tailEntity.getEntityType()) {
                case ACTOR:         return addRelationshipToActor((Actor) tailEntity, headEntity, relationship.getRelationshipType());
                case EVENT:         return addRelationshipToEvent((Event) tailEntity, headEntity, relationship.getRelationshipType());
                case INTERACTION:   return addRelationshipToInteraction((Interaction) tailEntity, headEntity, relationship.getRelationshipType());
            }
        } catch (IllegalArgumentException|ClassCastException e) {
            log.error("Error creating relationship, with error: ", e);
            log.error("Relationship causing error: {}", relationship);
            return false;
        }

        log.error("received unsupported type on head entity");
        return true;
    }

    private boolean addRelationshipToActor(Actor actor, GrassrootGraphEntity headEntity, GrassrootRelationship.Type type) {
        if (GrassrootRelationship.Type.PARTICIPATES.equals(type)) {
            headEntity.addParticipatingActor(actor);
            return persistGraphEntity(headEntity);
        } else if (GrassrootRelationship.Type.GENERATOR.equals(type)) {
            headEntity.addGenerator(actor);
            return persistGraphEntity(headEntity);
        }

        throw new IllegalArgumentException("Illegal relationship & head entity combination in adding relationship to actor");
    }

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

    // again, only relevant from single, central node
    private boolean removeRelationships(List<IncomingRelationship> relationships) {
        return relationships.stream().map(this::removeSingleRelationship).reduce(true, (a, b) -> a && b);
    }

    private boolean removeSingleRelationship(IncomingRelationship relationship) {
        GrassrootGraphEntity headEntity = fetchGraphEntity(relationship.getHeadEntityType(), relationship.getHeadEntityPlatformId());
        GrassrootGraphEntity tailEntity = fetchGraphEntity(relationship.getTailEntityType(), relationship.getTailEntityPlatformId());

        if (headEntity == null || tailEntity == null) {
            log.error("received a relationship that has invalid head or tail");
            return false;
        }

        switch (relationship.getRelationshipType()) {
            case PARTICIPATES:
                headEntity.removeParticipant(tailEntity);
                return persistGraphEntity(tailEntity);
            case GENERATOR:
                throw new IllegalArgumentException("Error! Cannot remove generator relationship");
            case OBSERVES:
                throw new IllegalArgumentException("Observer relationship not yet implemented");
            default:
                throw new IllegalArgumentException("Unsupported relationship type provided");
        }
    }

    private GrassrootGraphEntity fetchGraphEntity(GraphEntityType entityType, String platformId) {
        switch (entityType) {
            case ACTOR:         return actorRepository.findByPlatformUid(platformId);
            case EVENT:         return eventRepository.findByPlatformUid(platformId);
            case INTERACTION:   return interactionRepository.findByPlatformUid(platformId);
        }
        return null;
    }

    private boolean persistGraphEntity(GrassrootGraphEntity graphEntity) {
        try {
            switch (graphEntity.getEntityType()) {
                case ACTOR:         actorRepository.save((Actor) graphEntity); break;
                case EVENT:         eventRepository.save((Event) graphEntity); break;
                case INTERACTION:   interactionRepository.save((Interaction) graphEntity); break;
            }
            return true;
        } catch (IllegalArgumentException|ClassCastException e) {
            log.error("Could not persist entity in graph", e);
            return false;
        }
    }


}
