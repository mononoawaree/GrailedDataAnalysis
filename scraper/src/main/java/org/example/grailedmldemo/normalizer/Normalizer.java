package org.example.grailedmldemo.normalizer;

import org.example.grailedmldemo.model.GrailedListingRaw;
import org.example.grailedmldemo.model.Listing;

//Generic interface for normalizing raw scraped data into a structured format suitable for storage and analysis.
public interface Normalizer<T> {
    Listing normalize(T rawData);
}
