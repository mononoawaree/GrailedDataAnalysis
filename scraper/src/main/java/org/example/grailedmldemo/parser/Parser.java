package org.example.grailedmldemo.parser;

import org.example.grailedmldemo.exception.ParseException;
import org.example.grailedmldemo.model.GrailedListingRaw;

import java.util.List;

//Generic interface for parsing raw HTML content into structured data objects.
//Implementations will handle specific page types (e.g., listing pages, product detail pages) and extract relevant information for downstream processing.
public interface Parser {
    List<?> parse(String rawJson) throws ParseException;
}
