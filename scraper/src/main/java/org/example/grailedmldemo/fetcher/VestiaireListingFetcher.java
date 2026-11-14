package org.example.grailedmldemo.fetcher;

import okhttp3.*;
import org.example.grailedmldemo.exception.FetchException;

import java.io.IOException;

public class VestiaireListingFetcher  {
    private final String baseUrl;
    OkHttpClient client = new OkHttpClient();


    public VestiaireListingFetcher(String baseUrl, OkHttpClient client) {
        this.baseUrl = baseUrl;
        this.client = client;
    }

    public String fetch(String designerName, long offset) throws FetchException, IOException {
        String fields = "[\"name\", \"brand\", \"model\", \"description\", \"country\", \"price\", \"discount\", \"link\", \"sold\", \"likes\", \"seller\", \"pictures\", \"colors\", \"size\", \"createdAt\", \"dutyFree\", \"universe\", \"priceRange\", \"condition\", \"editorPicks\", \"watchMechanism\", \"isOfficialStore\", \"materialLvl0\", \"materialLvl1\", \"model1\", \"categoryLvl1\", \"categoryLvl2\", \"universeId\", \"editorPicks\", \"shouldBeGone\"]";
        String body = "{\"pagination\": {\"offset\":" + offset + ",\"limit\": 60},"
                + "\"fields\": " + fields
                + ",\"q\": \"" + designerName + "\","
                + "\"sortBy\": \"recency\","
                + "\"filters\": {\"sold\": true},"
                + "\"locale\": {\"country\": \"US\", \"currency\": \"USD\", \"language\": \"us\"},"
                + "\"mySizes\": null}";
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), body);
        Request request = new Request.Builder()
                .url(baseUrl)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(requestBody)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new FetchException("Failed: " + response.code());
            }
            return response.body().string();
        }
    }
}
