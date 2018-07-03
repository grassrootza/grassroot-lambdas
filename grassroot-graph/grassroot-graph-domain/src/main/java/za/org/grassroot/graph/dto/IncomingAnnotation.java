package za.org.grassroot.graph.dto;

import lombok.*;
import za.org.grassroot.graph.domain.enums.GraphEntityType;

import java.util.List;
import java.util.Map;

@Getter @Setter @AllArgsConstructor @NoArgsConstructor @ToString
public class IncomingAnnotation {

    public static final String language = "LANGUAGE";
    public static final String province = "PROVINCE";
    public static final String town = "TOWN";
    public static final String latitude = "LATITUDE";
    public static final String longitude = "LONGITUDE";
    public static final String description = "DESCRIPTION";

    private String platformId;

    private GraphEntityType entityType;

    // stores properties corresponding to constants above, which act as keys.
    private Map<String, String> properties;

    // stores topics and tags derived from processing of entity descriptions.
    private List<String> tags;

}
