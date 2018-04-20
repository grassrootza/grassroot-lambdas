package za.org.grassroot.graph.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.Property;
import za.org.grassroot.graph.domain.enums.GraphEntityType;

import java.time.Instant;

@Getter @Setter @NoArgsConstructor @ToString
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "entityType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Actor.class, name = "ACTOR"),
        @JsonSubTypes.Type(value = Event.class, name = "EVENT"),
        @JsonSubTypes.Type(value = Interaction.class, name = "INTERACTION")
})
public abstract class GrassrootGraphEntity {

    protected GraphEntityType entityType;

    @Property
    protected Instant creationTime; // creation time _in graph_ (not necessarily on platform)

    // UID of entity on main platform (to enable audit / traceability later)
    @Property protected String platformUid;

}
