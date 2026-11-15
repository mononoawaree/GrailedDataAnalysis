package org.example.grailedmldemo.service;

import okhttp3.OkHttpClient;
import org.example.grailedmldemo.exception.FetchException;
import org.example.grailedmldemo.exception.ParseException;
import org.example.grailedmldemo.fetcher.GrailedListingFetcher;
import org.example.grailedmldemo.model.GrailedListingRaw;
import org.example.grailedmldemo.model.PredictionInput;
import org.example.grailedmldemo.model.PredictionResponse;
import org.example.grailedmldemo.normalizer.GrailedListingNormalizer;
import org.example.grailedmldemo.parser.GrailedListingParser;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class PricePredictionService {
    private final InferenceService inferenceService;
    private final GrailedListingFetcher fetcher;
    private final GrailedListingParser parser;
    private final GrailedListingNormalizer normalizer;

    public PricePredictionService(InferenceService inferenceService, GrailedListingFetcher fetcher, GrailedListingParser parser, GrailedListingNormalizer normalizer) throws FetchException, IOException, ParseException {
        this.inferenceService = inferenceService;
        this.fetcher = fetcher;
        this.parser = parser;
        this.normalizer = normalizer;
    }
    public PredictionResponse runInference(String url) throws IOException, ParseException, FetchException {
        String json = fetcher.fetchOne(url);
        GrailedListingRaw grailedListingRaw = parser.parseOne(json);
        PredictionInput input = normalizer.normalizeForPrediction(grailedListingRaw);
        return inferenceService.predict(input);
    }
}
