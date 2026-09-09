package com.railway.eta.history;

import java.time.Instant;

public record StationArrivalHistoryResponse(
        String stationCode,
        Instant scheduledArrival,
        Instant actualArrival,
        double delayMinutes
) {}