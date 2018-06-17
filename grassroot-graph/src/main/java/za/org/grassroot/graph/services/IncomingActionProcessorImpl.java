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
import za.org.grassroot.graph.dto.IncomingDataObject;
import za.org.grassroot.graph.dto.IncomingGraphAction;
import za.org.grassroot.graph.dto.IncomingRelationship;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;
import za.org.grassroot.graph.repository.InteractionRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
        log.info("handling action, type: {}", action.getActionType());
        switch (action.getActionType()) {
            case CREATE_ENTITY:         return createEntities(action);
            case REMOVE_ENTITY:         return removeEntities(action);
            case CREATE_RELATIONSHIP:   return establishRelationships(action.getRelationships());
            case REMOVE_RELATIONSHIP:   return removeRelationships(action.getRelationships());
        }
        return false;
    }

    // note: although it allows for multiple entities at once, to preserve integrity, any such multiple entities must
    // be related to each other, i.e., have relationships among each other - the validation checks if this is not the case
    private boolean createEntities(IncomingGraphAction action) {
        boolean executionSucceeded = false;

        // first, create or update the master entities, including possibly multiple (e.g., if packet is group creation)
        if (action.getDataObjects() != null) {
            log.info("Graph action has data objects, processing {} objects", action.getDataObjects().size());
            executionSucceeded = createIncomingDataObjects(new HashSet<>(action.getDataObjects()));
            log.info("After data object handling, succeeded: {}", executionSucceeded);
        }

        // second, wire up any relationships - if we need to (OGM may do this for us - keep eye on it)
        if (action.getRelationships() != null) {
            executionSucceeded = executionSucceeded && action.getRelationships().stream()
                    .map(this::createSingleRelationship).reduce(true, (a, b) -> a && b);
        }

        // then, return true if everything went to plan
        return executionSucceeded;
    }

    private boolean createIncomingDataObjects(Set<IncomingDataObject> dataObjects) {
        Set<IncomingDataObject> incomingActors = dataObjects.stream()
                .filter(IncomingDataObject::isActor).collect(Collectors.toSet());
        Set<Actor> individuals = incomingActors.stream().map(IncomingDataObject::getGraphEntity)
                .map(entity -> (Actor) entity).collect(Collectors.toSet());
        Set<Actor> storedIndividuals = storeActorsNotInGraph(individuals);
        log.info("stored {} actors onto the graph", storedIndividuals.size());

        // non user and non group (going to be smaller set)

        dataObjects.removeAll(incomingActors);
        log.info("and now have {} remaining objects", incomingActors);
        return dataObjects.stream()
                .map(this::createMasterEntity).reduce(true, (a, b) -> a && b); // can do as allMatch but find that a bit opaque
    }

    private boolean createMasterEntity(IncomingDataObject dataObject) {
        if (entityExists(dataObject.getGraphEntity()))
            return true; // by definition, execution succeeded, as we do not do any updating in here, because of too much potential fragility

        log.info("Data object did not exist, has entity type: {}, entity: {}", dataObject.getEntityType(), dataObject);
        try {
            switch (dataObject.getEntityType()) {
                case ACTOR:         actorRepository.save((Actor) dataObject.getGraphEntity()); break;
                case EVENT:         eventRepository.save(replaceRelationshipEntities((Event) dataObject.getGraphEntity()), 0); break;
                case INTERACTION:   interactionRepository.save((Interaction) dataObject.getGraphEntity()); break;
            }
            return true;
        } catch (IllegalArgumentException|ClassCastException e) {
            log.error("error persisting graph entity: ", e);
            return false;
        }
    }

    private Event replaceRelationshipEntities(Event event) {
        event.setParticipatesIn(transformToGraphActors(event.getParticipatesIn()));
        event.setCreator(replaceWithGraphEntityIfPresent(event.getCreator()));
        event.setParticipants(transformToGraphActors(event.getParticipants()));
        return event;
    }

    // todo : replace entity collections with sets instead of lists to clean this all up (and better matches data structure too)
    private List<Actor> transformToGraphActors(List<Actor> actors) {
        return actors == null ? new ArrayList<>() :
                new ArrayList<>(storeActorsNotInGraph(new HashSet<>(actors)));
    }

    private GrassrootGraphEntity replaceWithGraphEntityIfPresent(GrassrootGraphEntity graphEntity) {
        switch (graphEntity.getEntityType()) {
            case ACTOR: return replaceWithGraphActorIfPresent((Actor) graphEntity);
            case EVENT: return replaceWithGraphEventIfPresent((Event) graphEntity);
            case INTERACTION: return replaceWithGraphInteractionIfPresent((Interaction) graphEntity);
            default: throw new IllegalArgumentException("Unsupported entity type in graph entity swap");
        }
    }

    private Actor replaceWithGraphActorIfPresent(Actor actor) {
        Actor actorInGraph = actorRepository.findByPlatformUid(actor.getPlatformUid());
        return actorInGraph != null ? actorInGraph : actor;
    }

    private Event replaceWithGraphEventIfPresent(Event event) {
        Event eventInGraph = eventRepository.findByPlatformUid(event.getPlatformUid());
        return eventInGraph != null ? eventInGraph : event;
    }

    private Interaction replaceWithGraphInteractionIfPresent(Interaction interaction) {
        Interaction intInGraph = interactionRepository.findByPlatformUid(interaction.getPlatformUid());
        return intInGraph != null ? intInGraph : interaction;
    }


    private boolean entityExists(GrassrootGraphEntity graphEntity) {
        switch (graphEntity.getEntityType()) {
            case ACTOR: return actorRepository.findByPlatformUid(graphEntity.getPlatformUid()) != null;
            case EVENT: return eventRepository.findByPlatformUid(graphEntity.getPlatformUid()) != null;
            case INTERACTION: return interactionRepository.findByPlatformUid(graphEntity.getPlatformUid()) != null;
            default: throw new IllegalArgumentException("Unknown graph entity type in create entity action");
        }
    }

    private Set<Actor> storeActorsNotInGraph(Set<Actor> incomingActors) {
        Set<String> platformIds = incomingActors.stream().map(Actor::getPlatformUid).collect(Collectors.toSet());
        log.info("extracted platform IDs: {}", platformIds);
        Set<Actor> inGraphActors = new HashSet<>(actorRepository.findByPlatformUidIn(platformIds));
        Set<Actor> notInGraphActors = new HashSet<>(incomingActors);
        notInGraphActors.removeAll(inGraphActors);
        log.info("Handling actor storage, incoming: {}, in graph: {}, not in graph: {}",
                incomingActors.size(), inGraphActors.size(), notInGraphActors.size());
        Set<Actor> newlyInGraphActors = saveActorsToGraph(notInGraphActors);
        log.info("And now have {} actors newly in graph", newlyInGraphActors.size());
        inGraphActors.addAll(newlyInGraphActors);
        return inGraphActors;
    }

    private Set<Actor> saveActorsToGraph(Set<Actor> actorsNotInGraph) {
        if (actorsNotInGraph.size() < 10)
            return StreamSupport.stream(actorRepository.saveAll(actorsNotInGraph)
                    .spliterator(), false).collect(Collectors.toSet());

        log.info("large number of actors, splitting TX ...");
        return actorsNotInGraph.stream().map(actor -> actorRepository.save(actor, 0)).collect(Collectors.toSet());
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
        log.info("creating realtionships: {}", relationships);
        return relationships.stream().map(this::createSingleRelationship).reduce(true, (a, b) -> a && b);
    }

    // now, this is the heart of the beast. complex and ugly because of profusion of relationship types
    // by far the most elegant solution _would have been_ RelationshipEntities with generics
    // _but_ it doesn't seem like the OGM supports that yet. So would have had massive entity profusion.
    // thus decided best to consolidate messiness in single entity, inside here. definitely change if OGM implements generics.
    private boolean createSingleRelationship(IncomingRelationship relationship) {
        log.info("inside creating relationship, fetching head entity ...");
        GrassrootGraphEntity headEntity = fetchGraphEntity(relationship.getHeadEntityType(), relationship.getHeadEntityPlatformId(), 0);
        log.info("fetched head entity: {}", headEntity);

        log.info("fetching tail entity");
        GrassrootGraphEntity tailEntity = fetchGraphEntity(relationship.getTailEntityType(), relationship.getTailEntityPlatformId(), 0);
        log.info("fetched tail entity: {}", tailEntity);

        if (headEntity == null || tailEntity == null) {
            log.error("Received a relationship that has invalid head or tail, exiting");
            return true;
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
            Set<Actor> participants = headEntity.getParticipatingActors();

            if (participants.contains(actor)) {
                log.info("Already a participant, exiting");
                return true;
            }

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
        GrassrootGraphEntity headEntity = fetchGraphEntity(relationship.getHeadEntityType(), relationship.getHeadEntityPlatformId(), 0);
        GrassrootGraphEntity tailEntity = fetchGraphEntity(relationship.getTailEntityType(), relationship.getTailEntityPlatformId(), 0);

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

    private GrassrootGraphEntity fetchGraphEntity(GraphEntityType entityType, String platformId, int depth) {
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
