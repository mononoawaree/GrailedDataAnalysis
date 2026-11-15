package org.example.grailedmldemo.service;

import org.example.grailedmldemo.model.PredictionInput;
import org.example.grailedmldemo.model.PredictionResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class InferenceService {
    private final RestClient restClient;

    public InferenceService(RestClient restClient) {
        this.restClient = restClient;
    }

    public PredictionResponse predict(PredictionInput input) {
        PredictionResponse r = restClient.post()
                .uri("/predict")
                .body(input)
                .retrieve()
                .body(PredictionResponse.class);
        return r;
    }
}
