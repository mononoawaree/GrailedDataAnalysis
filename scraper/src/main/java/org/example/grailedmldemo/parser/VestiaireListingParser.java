package org.example.grailedmldemo.parser;

import com.google.gson.*;
import org.example.grailedmldemo.exception.ParseException;
import org.example.grailedmldemo.model.VestiaireListingRaw;

import java.util.ArrayList;
import java.util.List;

public class VestiaireListingParser implements Parser {
    Gson gson = new Gson();
    // Extracts the "hits" (info) array from the Algolia response
    // Maps each hit to a ListingRaw object
    @Override
    public List<VestiaireListingRaw> parse(String rawJson) throws ParseException {
        List<VestiaireListingRaw> vestiaireListingRaws = new ArrayList<>();
        JsonObject jsonObject = JsonParser.parseString(rawJson).getAsJsonObject();
        JsonArray items = jsonObject.getAsJsonArray("items");
        for (JsonElement item : items) {
            VestiaireListingRaw listing = gson.fromJson(item, VestiaireListingRaw.class);
            vestiaireListingRaws.add(listing);
        }
        return vestiaireListingRaws;
    }
}
