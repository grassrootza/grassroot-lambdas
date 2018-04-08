package za.org.grassroot.graph.dto;

import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.graph.domain.GrassrootGraphEntity;
import za.org.grassroot.graph.domain.enums.GraphEntityType;

/*
Used for serialization and deserialization into and out of the kinesis stream - just provides a type, so we know what to do,
and then the object itself
 */
@Getter @Setter
public class IncomingDataObject {

    public GraphEntityType entityType;
    public GrassrootGraphEntity graphEntity;

}
