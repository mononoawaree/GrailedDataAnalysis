package org.example.grailedmldemo.model;

import lombok.Data;

@Data
public class Designer {
    private String id;
    private String name;
    private String slug;
    private String count;
    private String source;

    public Designer() {}

    public Designer(String id, String name, String slug, String count, String source) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.count = count;
        this.source = source;
    }
}