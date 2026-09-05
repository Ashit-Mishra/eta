package com.railway.eta.ml;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/ml")
public class TrainingDataCsvController {

    private final TrainingDataService trainingDataService;

    public TrainingDataCsvController(
            TrainingDataService trainingDataService
    ) {
        this.trainingDataService = trainingDataService;
    }

    @GetMapping(
            value = "/training-data/csv",
            produces = "text/csv"
    )
    public ResponseEntity<byte[]> exportCsv() {

        List<TrainingData> data =
                trainingDataService
                        .generateForAllCompletedRuns()
                        .stream()
                        .filter(row -> row.getDelayType() != null)
                        .toList();

        StringBuilder csv = new StringBuilder();

        // Header
        csv.append(
                "run_id," +
                        "train_no," +
                        "station_code," +
                        "timestamp," +
                        "speed_kmh," +
                        "distance_to_station_km," +
                        "distance_to_destination_km," +
                        "current_delay_minutes," +
                        "historical_average_delay_minutes," +
                        "scheduled_arrival_minutes," +
                        "time_of_day," +
                        "day_of_week," +
                        "delay_type," +
                        "actual_delay_at_station_minutes\n"
        );

        // Rows
        for (TrainingData row : data) {

            csv.append(row.getRunId()).append(",");
            csv.append(escape(row.getTrainNo())).append(",");
            csv.append(escape(row.getStationCode())).append(",");
            csv.append(row.getTimestamp()).append(",");
            csv.append(row.getSpeedKmh()).append(",");
            csv.append(row.getDistanceToStationKm()).append(",");
            csv.append(row.getDistanceToDestinationKm()).append(",");
            csv.append(row.getCurrentDelayMinutes()).append(",");
            csv.append(row.getHistoricalAverageDelayMinutes()).append(",");
            csv.append(row.getScheduledArrivalMinutes()).append(",");
            csv.append(row.getTimeOfDay()).append(",");
            csv.append(row.getDayOfWeek()).append(",");
            csv.append(row.getDelayType()).append(",");
            csv.append(row.getActualDelayAtStationMinutes()).append("\n");
        }

        byte[] bytes =
                csv.toString()
                        .getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=train_eta_dataset.csv"
                )
                .contentType(
                        MediaType.parseMediaType("text/csv")
                )
                .body(bytes);
    }

    private String escape(String value) {

        if (value == null) {
            return "";
        }

        if (value.contains(",") ||
                value.contains("\"") ||
                value.contains("\n")) {

            return "\"" +
                    value.replace("\"", "\"\"") +
                    "\"";
        }

        return value;
    }
}