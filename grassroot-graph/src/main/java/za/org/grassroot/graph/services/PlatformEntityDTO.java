package za.org.grassroot.graph.services;

import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.graph.domain.enums.ActorType;
import za.org.grassroot.graph.domain.enums.EventType;
import za.org.grassroot.graph.domain.enums.InteractionType;
import za.org.grassroot.graph.domain.enums.GraphEntityType;
import za.org.grassroot.graph.domain.GraphStringUtils;

@Getter @Setter
public class PlatformEntityDTO {

    private String platformId;
    private GraphEntityType entityType;

    private ActorType actorType;
    private EventType eventType;
    private InteractionType interactionType;

    public PlatformEntityDTO(String platformId, GraphEntityType entityType, String subType) {
        this.platformId = platformId;
        this.entityType = entityType;
        this.setSubTypeIfPresent(subType);
    }

    private void setSubTypeIfPresent(String givenSubType) {
        if (!GraphStringUtils.isEmpty(givenSubType)) {
            switch (entityType) {
                case ACTOR:         this.actorType = ActorType.valueOf(givenSubType); break;
                case EVENT:         this.eventType = EventType.valueOf(givenSubType); break;
                case INTERACTION:   this.interactionType = InteractionType.valueOf(givenSubType); break;
            }
        }
    }

    public boolean isActor() {
        return GraphEntityType.ACTOR.equals(entityType);
    }

    public boolean isEvent() {
        return GraphEntityType.EVENT.equals(entityType);
    }

}