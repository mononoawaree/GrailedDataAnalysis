package org.example.grailedmldemo.model;

import lombok.Data;

@Data
public class VestiaireDesignerRaw {
    private String id;
    private String name;
    private String slug;
    private String count;

    public VestiaireDesignerRaw() {}

    public VestiaireDesignerRaw(String id, String name, String slug, String count) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.count = count;
    }
}
