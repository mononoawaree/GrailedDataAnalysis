package org.example.grailedmldemo.parser;

import com.google.gson.*;
import org.example.grailedmldemo.exception.ParseException;
import org.example.grailedmldemo.model.GrailedListingRaw;

import java.util.ArrayList;
import java.util.List;

//Using gson for JSON parsing of Grailed listing pages to extract relevant data fields (title, price, brand, etc.)
public class GrailedListingParser implements Parser {
    Gson gson = new Gson();
    // Extracts the "hits" (info) array from the Algolia response
    // Maps each hit to a ListingRaw object
    @Override
    public List<GrailedListingRaw> parse(String rawJson) throws ParseException {
        List<GrailedListingRaw> grailedListingRaws = new ArrayList<>();
        JsonObject jsonObject = JsonParser.parseString(rawJson).getAsJsonObject();
        JsonArray results = jsonObject.getAsJsonArray("results");
        JsonObject firstResult = results.get(0).getAsJsonObject();
        JsonArray hits = firstResult.getAsJsonArray("hits");
        for (JsonElement hit : hits) {
            GrailedListingRaw listing = gson.fromJson(hit, GrailedListingRaw.class);
            grailedListingRaws.add(listing);
        }
        return grailedListingRaws;
    }
}
