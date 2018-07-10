package za.org.grassroot.graph.dto;

import lombok.*;
import za.org.grassroot.graph.domain.Actor;
import za.org.grassroot.graph.domain.Event;
import za.org.grassroot.graph.domain.Interaction;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.enums.GraphEntityType;

/*
Used for serialization and deserialization into and out of the kinesis stream -
just provides a type, so we know what to do, and then the object itself
 */
@Getter @Setter @AllArgsConstructor @NoArgsConstructor @ToString
public class IncomingDataObject {

    private GraphEntityType entityType;
    private GrassrootGraphEntity graphEntity;

    public boolean isActor() {
        return GraphEntityType.ACTOR.equals(entityType);
    }

    public String getEntitySubtype() {
        switch (entityType) {
            case ACTOR:         return ((Actor) graphEntity).getActorType().name();
            case EVENT:         return ((Event) graphEntity).getEventType().name();
            case INTERACTION:   return ((Interaction) graphEntity).getInteractionType().name();
        }
        return null;
    }

}
