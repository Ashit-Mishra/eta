package com.railway.eta.ml;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MlPredictionRequest(

        @JsonProperty("speed_kmh")
        double speedKmh,

        @JsonProperty("distance_to_station_km")
        double distanceToStationKm,

        @JsonProperty("distance_to_destination_km")
        double distanceToDestinationKm,

        @JsonProperty("current_delay_minutes")
        double currentDelayMinutes,

        @JsonProperty("historical_average_delay_minutes")
        double historicalAverageDelayMinutes,

        @JsonProperty("scheduled_arrival_minutes")
        double scheduledArrivalMinutes,

        @JsonProperty("time_of_day")
        int timeOfDay,

        @JsonProperty("day_of_week")
        int dayOfWeek,

        @JsonProperty("station_code")
        String stationCode,

        @JsonProperty("delay_type")
        String delayType
) {}