package za.org.grassroot.graph.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import za.org.grassroot.graph.domain.enums.GraphEntityType;

import java.time.Instant;

@NodeEntity
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

    @Property
    protected Instant creationTime; // creation time _in graph_ (not necessarily on platform)

    // UID of entity on main platform, both to fetch properties as needed, and for traceability; no entity on main
    // can be multiple entities on graph, hence the unique index (and lookup on this property will be used _a lot_)
    @Property @Index(unique = true) protected String platformUid;

    // some utility methods
    public boolean isActor() { return GraphEntityType.ACTOR.equals(entityType); }
    public boolean isEvent() { return GraphEntityType.EVENT.equals(entityType); }

}
