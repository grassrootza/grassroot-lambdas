package za.org.grassroot.graph.services;

import lombok.*;

@Getter @Setter @AllArgsConstructor @ToString
public class AnnotationInfoDTO {

    private String description;
    private String[] tags;
    private String language;
    private String location;

}