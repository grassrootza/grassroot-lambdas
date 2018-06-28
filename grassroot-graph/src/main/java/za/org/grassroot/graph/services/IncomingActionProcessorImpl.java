package za.org.grassroot.graph.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.Interaction;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.dto.IncomingDataObject;
import za.org.grassroot.graph.dto.IncomingGraphAction;
import za.org.grassroot.graph.dto.IncomingRelationship;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;
import za.org.grassroot.graph.repository.InteractionRepository;

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

    private final ExistenceBroker existenceBroker;
    private final RelationshipBroker relationshipBroker;

    @Autowired
    public IncomingActionProcessorImpl(ActorRepository actorRepository, EventRepository eventRepository, InteractionRepository interactionRepository, ExistenceBroker existenceBroker, RelationshipBroker relationshipBroker) {
        this.actorRepository = actorRepository;
        this.eventRepository = eventRepository;
        this.interactionRepository = interactionRepository;
        this.existenceBroker = existenceBroker;
        this.relationshipBroker = relationshipBroker;
    }

    @Override
    public Mono<Boolean> processIncomingAction(IncomingGraphAction action) {
        return Mono.<Boolean>create(sink -> {
            log.info("Handling action, type: {}", action.getActionType());
            boolean succeeded = false;
            switch (action.getActionType()) {
                case CREATE_ENTITY:         succeeded = createEntities(action); break;
                case REMOVE_ENTITY:         succeeded = removeEntities(action); break;
                case CREATE_RELATIONSHIP:   succeeded = establishRelationships(action.getRelationships()); break;
                case REMOVE_RELATIONSHIP:   succeeded = removeRelationships(action.getRelationships()); break;
            }
            sink.success(succeeded);
        });
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
                case EVENT:
                    log.debug("Persisting new event entity: {}", dataObject.getGraphEntity());
                    eventRepository.save((Event) dataObject.getGraphEntity());
                    log.debug("New event entity persisted.");
                    break;
                case INTERACTION:   interactionRepository.save((Interaction) dataObject.getGraphEntity()); break;
            }
            return true;
        } catch (IllegalArgumentException|ClassCastException e) {
            log.error("error persisting graph entity: ", e);
            return false;
        }
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

    // to-do: transition existence check to existenceBroker.
    private boolean removeSingleEntity(IncomingDataObject dataObject) {
        PlatformEntityDTO entityDTO = new PlatformEntityDTO(dataObject.getGraphEntity().getPlatformUid(),
                dataObject.getEntityType(), null);
        if (!existenceBroker.doesEntityExistInGraph(entityDTO)) {
            log.error("Entity does not exist in graph");
            return false;
        }

        switch (dataObject.getEntityType()) {
            case ACTOR:         actorRepository.delete((Actor) dataObject.getGraphEntity()); break;
            case EVENT:         eventRepository.delete((Event) dataObject.getGraphEntity()); break;
            case INTERACTION:   interactionRepository.delete((Interaction) dataObject.getGraphEntity()); break;
        }
        return true;
    }

    // may need to do this in order, hence a list
    // Update: should not need to be in order with elimination of fifo and integration of existence broker?
    private boolean establishRelationships(List<IncomingRelationship> relationships) {
        log.info("Creating relationship: {}", relationships);
        return relationships.stream().map(this::createSingleRelationship).reduce(true, (a, b) -> a && b);
    }

    private boolean createSingleRelationship(IncomingRelationship relationship) {
        PlatformEntityDTO tailEntity = new PlatformEntityDTO(relationship.getTailEntityPlatformId(), relationship.getTailEntityType(), relationship.getTailEntitySubtype());
        PlatformEntityDTO headEntity = new PlatformEntityDTO(relationship.getHeadEntityPlatformId(), relationship.getHeadEntityType(), relationship.getHeadEntitySubtype());

        if (!existenceBroker.doesEntityExistInGraph(tailEntity))
            existenceBroker.addEntityToGraph(tailEntity);

        log.info("Completed existence check of tail");

        if (!existenceBroker.doesEntityExistInGraph(headEntity))
            existenceBroker.addEntityToGraph(headEntity);

        log.info("Completed existence check of head");

        switch (relationship.getRelationshipType()) {
            case GENERATOR: return relationshipBroker.setGeneration(tailEntity, headEntity);
            case PARTICIPATES: return relationshipBroker.addParticipation(tailEntity, headEntity);
            case OBSERVES: return relationshipBroker.addObserver(tailEntity, headEntity);
            default:
                log.error("Error! Badly formed instruction, not a known entity type");
                return false;
        }
    }

    // again, only relevant from single, central node
    private boolean removeRelationships(List<IncomingRelationship> relationships) {
        return relationships.stream().map(this::removeSingleRelationship).reduce(true, (a, b) -> a && b);
    }

    private boolean removeSingleRelationship(IncomingRelationship relationship) {
        GrassrootGraphEntity tailEntity = fetchGraphEntity(relationship.getTailEntityType(), relationship.getTailEntityPlatformId(), 0);
        GrassrootGraphEntity headEntity = fetchGraphEntity(relationship.getHeadEntityType(), relationship.getHeadEntityPlatformId(), 0);

        if (tailEntity == null || headEntity == null)
            return true; // by definition, execution succeeded

        switch (relationship.getRelationshipType()) {
            case PARTICIPATES:
                return relationshipBroker.removeParticipation(tailEntity, headEntity);
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
