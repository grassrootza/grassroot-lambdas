package za.org.grassroot.graph.services;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;
import za.org.grassroot.graph.domain.enums.ActorType;
import za.org.grassroot.graph.domain.enums.EventType;
import za.org.grassroot.graph.domain.enums.InteractionType;
import za.org.grassroot.graph.domain.enums.GraphEntityType;

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
        if (!StringUtils.isEmpty(givenSubType)) {
            if (isActor()) {
               try {
                   this.actorType = ActorType.valueOf(givenSubType);
               } catch (Exception e) {
                   this.actorType = ActorType.INDIVIDUAL; // todo : fix once straightened out on platform
               }
            }

            if (isEvent()) {
                this.eventType = EventType.valueOf(givenSubType);
            }

            if (isInteraction()) {
                this.interactionType = InteractionType.valueOf(givenSubType);
            }
        }
    }

    public boolean isActor() {
        return GraphEntityType.ACTOR.equals(entityType);
    }

    public boolean isEvent() {
        return GraphEntityType.EVENT.equals(entityType);
    }

    public boolean isInteraction() {
        return GraphEntityType.INTERACTION.equals(entityType);
    }
}
