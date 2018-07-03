package za.org.grassroot.graph.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import za.org.grassroot.graph.domain.enums.GraphEntityType;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor @ToString
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "entityType")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonSubTypes({
        @JsonSubTypes.Type(value = Actor.class, name = "ACTOR"),
        @JsonSubTypes.Type(value = Event.class, name = "EVENT"),
        @JsonSubTypes.Type(value = Interaction.class, name = "INTERACTION")
})
public abstract class GrassrootGraphEntity {

    protected GraphEntityType entityType;

    public abstract String getPlatformUid();

    public abstract Instant getCreationTime();
    public abstract void setCreationTime(Instant instant);

    // some utility methods
    public boolean isActor() { return GraphEntityType.ACTOR.equals(entityType); }
    public boolean isEvent() { return GraphEntityType.EVENT.equals(entityType); }
    public boolean isInteraction() { return GraphEntityType.INTERACTION.equals(entityType); }
}