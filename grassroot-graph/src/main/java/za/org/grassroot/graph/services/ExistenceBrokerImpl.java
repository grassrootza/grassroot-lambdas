package za.org.grassroot.graph.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.repository.ActorRepository;
import za.org.grassroot.graph.repository.EventRepository;

@Service @Slf4j
public class ExistenceBrokerImpl implements ExistenceBroker {

    private static final int LARGE_TX_THRESHOLD = 100; // number of entities to try write at once

    private final ActorRepository actorRepository;
    private final EventRepository eventRepository;

    public ExistenceBrokerImpl(ActorRepository actorRepository, EventRepository eventRepository) {
        this.actorRepository = actorRepository;
        this.eventRepository = eventRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean doesEntityExistInGraph(PlatformEntityDTO platformEntity) {
        switch (platformEntity.getEntityType()) {
            case ACTOR: return actorRepository.countByPlatformUid(platformEntity.getPlatformId()) > 0;
            case EVENT: return eventRepository.countByPlatformUid(platformEntity.getPlatformId()) > 0;
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
        }
        return false;
    }


}
