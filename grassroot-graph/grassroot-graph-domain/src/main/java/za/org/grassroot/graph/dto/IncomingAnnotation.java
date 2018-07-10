package za.org.grassroot.graph.dto;

import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter @Setter @AllArgsConstructor @NoArgsConstructor @ToString
public class IncomingAnnotation {

    // keys for properties map
    public static final String language = "LANGUAGE";
    public static final String province = "PROVINCE";
    public static final String latitude = "LATITUDE";
    public static final String longitude = "LONGITUDE";

    private IncomingDataObject entity;
    private IncomingRelationship relationship;

    private Map<String, String> properties;
    private List<String> tags;
    private Set<String> keysToRemove;

}
