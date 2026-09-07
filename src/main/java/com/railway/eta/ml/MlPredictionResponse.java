package com.railway.eta.ml;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MlPredictionResponse(

        @JsonProperty("predicted_delay_minutes")
        double predictedDelayMinutes,

        @JsonProperty("model_used")
        String modelUsed
) {}