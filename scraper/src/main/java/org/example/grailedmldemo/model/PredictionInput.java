package org.example.grailedmldemo.model;

import lombok.Builder;
import lombok.Data;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

@Builder
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PredictionInput {

    private String title;
    private String department;
    private String category;
    private String categoryPath;
    private List<String> designerNames;

    private String color;
    private String condition;
    private String size;
    private String location;

    private String createdAt;        // ISO-8601 string
    private String coverPhotoUrl;    // becomes imageB64 after the refactor

    private int photoCount;
    private Integer measurementCount;
}