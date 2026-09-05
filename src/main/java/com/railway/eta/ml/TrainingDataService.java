package com.railway.eta.ml;

import com.railway.eta.history.GpsHistory;
import com.railway.eta.history.GpsHistoryRepository;
import com.railway.eta.history.HistoricalDelayService;
import com.railway.eta.history.StationArrivalHistory;
import com.railway.eta.history.StationArrivalHistoryRepository;
import com.railway.eta.history.TrainRun;
import com.railway.eta.history.TrainRunRepository;
import com.railway.eta.route.RouteStation;
import com.railway.eta.route.RouteStationRepository;
import com.railway.eta.simulator.TrainSimulationState;
import com.railway.eta.train.Train;
import com.railway.eta.train.TrainRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.railway.eta.simulator.TrainSimulationState.DelayType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class TrainingDataService {

    private final TrainRunRepository trainRunRepository;
    private final GpsHistoryRepository gpsHistoryRepository;
    private final StationArrivalHistoryRepository stationArrivalHistoryRepository;
    private final TrainRepository trainRepository;
    private final RouteStationRepository routeStationRepository;
    private final HistoricalDelayService historicalDelayService;

    public TrainingDataService(
            TrainRunRepository trainRunRepository,
            GpsHistoryRepository gpsHistoryRepository,
            StationArrivalHistoryRepository stationArrivalHistoryRepository,
            TrainRepository trainRepository,
            RouteStationRepository routeStationRepository,
            HistoricalDelayService historicalDelayService
    ) {
        this.trainRunRepository = trainRunRepository;
        this.gpsHistoryRepository = gpsHistoryRepository;
        this.stationArrivalHistoryRepository =
                stationArrivalHistoryRepository;
        this.trainRepository = trainRepository;
        this.routeStationRepository =
                routeStationRepository;
        this.historicalDelayService =
                historicalDelayService;
    }

    /**
     * Generate station-level training data for one
     * completed train run.
     */
    @Transactional(readOnly = true)
    public List<TrainingData> generateForRun(Long runId) {

        // ----------------------------------------------------
        // 1. Find completed run
        // ----------------------------------------------------

        TrainRun run =
                trainRunRepository.findById(runId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Train run not found: "
                                                + runId
                                )
                        );

        if (!"COMPLETED".equals(run.getStatus())) {
            throw new RuntimeException(
                    "Training data can only be generated "
                            + "for completed runs"
            );
        }

        String trainNo =
                run.getTrainNo();

        // ----------------------------------------------------
        // 2. Get train
        // ----------------------------------------------------

        Train train =
                trainRepository
                        .findByTrainNo(trainNo)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Train not found: "
                                                + trainNo
                                )
                        );

        // ----------------------------------------------------
        // 3. Get ordered route stations
        // ----------------------------------------------------

        List<RouteStation> routeStations =
                routeStationRepository
                        .findByRouteIdOrderBySequenceNumberAsc(
                                train.getRoute().getId()
                        );

        if (routeStations.isEmpty()) {
            throw new RuntimeException(
                    "No route stations found for train "
                            + trainNo
            );
        }

        // ----------------------------------------------------
        // 4. Get GPS history for this run
        // ----------------------------------------------------

        List<GpsHistory> gpsHistory =
                gpsHistoryRepository
                        .findByRunIdOrderByTimestampAsc(
                                runId
                        );

        if (gpsHistory.isEmpty()) {
            throw new RuntimeException(
                    "No GPS history found for run "
                            + runId
            );
        }

        // ----------------------------------------------------
        // 5. Get actual station arrivals for this run
        // ----------------------------------------------------

        List<StationArrivalHistory> arrivals =
                stationArrivalHistoryRepository
                        .findByRunIdOrderByActualArrivalAsc(
                                runId
                        );

        if (arrivals.isEmpty()) {
            throw new RuntimeException(
                    "No station arrival history found "
                            + "for run " + runId
            );
        }

        // ----------------------------------------------------
        // 6. Create one training row per station arrival
        // ----------------------------------------------------

        List<TrainingData> trainingData =
                new ArrayList<>();

        for (StationArrivalHistory arrival : arrivals) {

            String stationCode =
                    arrival.getStationCode();

            RouteStation routeStation =
                    findRouteStation(
                            stationCode,
                            routeStations
                    );

            if (routeStation == null) {
                continue;
            }

            Instant previousArrivalTime =
                    findPreviousArrivalTime(arrivals, arrival);

            DelayType delayType =
                    findDelayTypeBetweenStations(
                            gpsHistory,
                            arrival.getActualArrival(),
                            previousArrivalTime
                    );

            GpsHistory gps =
                    findObservationGps(
                            gpsHistory,
                            arrival.getActualArrival(),
                            previousArrivalTime,
                            delayType
                    );

            if (gps == null) {
                continue;
            }

            // ------------------------------------------------
            // Current delay at the observation point
            // ------------------------------------------------

            double currentDelayMinutes =
                    calculateCurrentDelay(
                            gps,
                            routeStation,
                            routeStations
                    );

            // ------------------------------------------------
            // Historical average delay
            // ------------------------------------------------

            double historicalAverageDelayMinutes =
                    historicalDelayService
                            .getHistoricalAverageDelay(
                                    trainNo,
                                    stationCode,
                                    arrival.getActualArrival()
                            );

            // ------------------------------------------------
            // Distance to station
            // ------------------------------------------------

            double distanceToStationKm =
                    calculateDistance(
                            gps.getLatitude(),
                            gps.getLongitude(),
                            routeStation
                                    .getStation()
                                    .getLatitude(),
                            routeStation
                                    .getStation()
                                    .getLongitude()
                    );

            // ------------------------------------------------
            // Distance to destination
            // ------------------------------------------------

            double distanceToDestinationKm =
                    calculateDistanceToDestination(
                            gps,
                            routeStation,
                            routeStations
                    );

            // ------------------------------------------------
            // Schedule features
            // ------------------------------------------------

            double scheduledArrivalMinutes =
                    getScheduledArrivalMinutes(
                            routeStation
                    );

            int timeOfDay =
                    getTimeOfDay(
                            arrival.getActualArrival()
                    );

            int dayOfWeek =
                    arrival.getActualArrival()
                            .atZone(
                                    ZoneId.of(
                                            "Asia/Kolkata"
                                    )
                            )
                            .getDayOfWeek()
                            .getValue();

            // ------------------------------------------------
            // Create training row
            // ------------------------------------------------

            TrainingData data =
                    new TrainingData();

            data.setRunId(runId);

            data.setTrainNo(trainNo);

            data.setStationCode(stationCode);

            data.setTimestamp(
                    gps.getTimestamp()
            );

            data.setSpeedKmh(
                    gps.getSpeedKmh()
            );

            data.setDistanceToStationKm(
                    distanceToStationKm
            );

            data.setDistanceToDestinationKm(
                    distanceToDestinationKm
            );

            data.setCurrentDelayMinutes(
                    currentDelayMinutes
            );

            data.setHistoricalAverageDelayMinutes(
                    historicalAverageDelayMinutes
            );

            data.setScheduledArrivalMinutes(
                    scheduledArrivalMinutes
            );

            data.setTimeOfDay(
                    timeOfDay
            );

            data.setDayOfWeek(
                    dayOfWeek
            );
            data.setDelayType(delayType);

            // ------------------------------------------------
            // TARGET
            //
            // This is the actual delay when the train
            // reached this station.
            // ------------------------------------------------

            data.setActualDelayAtStationMinutes(
                    arrival.getDelayMinutes()
            );

            trainingData.add(data);
        }

        return trainingData;
    }

    // ========================================================
    // Find route station
    // ========================================================

    @Transactional(readOnly = true)
    public List<TrainingData> generateForAllCompletedRuns() {

        List<TrainRun> completedRuns =
                trainRunRepository
                        .findByStatusOrderByStartTimeAsc("COMPLETED");

        List<TrainingData> allTrainingData =
                new ArrayList<>();

        for (TrainRun run : completedRuns) {

            try {
                List<TrainingData> runData =
                        generateForRun(run.getId());

                allTrainingData.addAll(runData);

            } catch (RuntimeException e) {
                System.out.println(
                        "Skipping run " + run.getId()
                                + ": " + e.getMessage()
                );
            }
        }

        return balanceTrainingData(allTrainingData);
    }

    // ========================================================
    // Balance final dataset
    //
    // NONE = 50%, WEATHER = 25%, SIGNAL = 12.5%, SPEED = 12.5%
    // ========================================================

    private List<TrainingData> balanceTrainingData(
            List<TrainingData> allData
    ) {

        List<TrainingData> none = new ArrayList<>();
        List<TrainingData> weather = new ArrayList<>();
        List<TrainingData> signal = new ArrayList<>();
        List<TrainingData> speed = new ArrayList<>();

        for (TrainingData data : allData) {

            if (data.getDelayType() == null) {
                continue;
            }

            switch (data.getDelayType()) {
                case NONE -> none.add(data);
                case WEATHER -> weather.add(data);
                case SIGNAL -> signal.add(data);
                case SPEED -> speed.add(data);
            }
        }

        System.out.println(
                "[ML] Available station-level rows: "
                        + "NONE=" + none.size()
                        + ", WEATHER=" + weather.size()
                        + ", SIGNAL=" + signal.size()
                        + ", SPEED=" + speed.size()
        );

        java.util.Collections.shuffle(none);
        java.util.Collections.shuffle(weather);
        java.util.Collections.shuffle(signal);
        java.util.Collections.shuffle(speed);

        /*
         * If all four delay classes are available, preserve the
         * desired 4:2:1:1 distribution:
         *
         * NONE    = 50%
         * WEATHER = 25%
         * SIGNAL  = 12.5%
         * SPEED   = 12.5%
         */
        boolean hasAllFour =
                !none.isEmpty()
                        && !weather.isEmpty()
                        && !signal.isEmpty()
                        && !speed.isEmpty();

        if (hasAllFour) {

            int units = Math.min(
                    Math.min(none.size() / 4, weather.size() / 2),
                    Math.min(signal.size(), speed.size())
            );

            if (units > 0) {

                int noneCount = units * 4;
                int weatherCount = units * 2;
                int signalCount = units;
                int speedCount = units;

                List<TrainingData> balanced =
                        new ArrayList<>(
                                noneCount
                                        + weatherCount
                                        + signalCount
                                        + speedCount
                        );

                balanced.addAll(
                        none.subList(0, noneCount)
                );

                balanced.addAll(
                        weather.subList(0, weatherCount)
                );

                balanced.addAll(
                        signal.subList(0, signalCount)
                );

                balanced.addAll(
                        speed.subList(0, speedCount)
                );

                java.util.Collections.shuffle(balanced);

                System.out.println(
                        "[ML] Balanced dataset: "
                                + balanced.size()
                                + " rows | NONE=" + noneCount
                                + " | WEATHER=" + weatherCount
                                + " | SIGNAL=" + signalCount
                                + " | SPEED=" + speedCount
                );

                return balanced;
            }
        }

        /*
         * A train/run does NOT need to experience every delay type.
         *
         * If one or more classes are absent from the station-level
         * dataset, balance only the classes that actually exist.
         *
         * Example:
         * NONE=100, WEATHER=20, SIGNAL=0, SPEED=10
         *
         * Result:
         * NONE=10, WEATHER=10, SPEED=10
         *
         * We do not return [] just because SIGNAL is absent.
         */
        List<List<TrainingData>> availableClasses =
                new ArrayList<>();

        if (!none.isEmpty()) {
            availableClasses.add(none);
        }

        if (!weather.isEmpty()) {
            availableClasses.add(weather);
        }

        if (!signal.isEmpty()) {
            availableClasses.add(signal);
        }

        if (!speed.isEmpty()) {
            availableClasses.add(speed);
        }

        if (availableClasses.isEmpty()) {

            System.out.println(
                    "[ML] No station-level training rows available."
            );

            return new ArrayList<>();
        }

        int perClass = Integer.MAX_VALUE;

        for (List<TrainingData> classData : availableClasses) {
            perClass = Math.min(
                    perClass,
                    classData.size()
            );
        }

        List<TrainingData> balanced =
                new ArrayList<>(
                        perClass * availableClasses.size()
                );

        for (List<TrainingData> classData : availableClasses) {

            balanced.addAll(
                    classData.subList(0, perClass)
            );
        }

        java.util.Collections.shuffle(balanced);

        System.out.println(
                "[ML] Balanced dataset using available classes: "
                        + balanced.size()
                        + " rows | classes="
                        + availableClasses.size()
                        + " | rows/class="
                        + perClass
        );

        return balanced;
    }

    private RouteStation findRouteStation(
            String stationCode,
            List<RouteStation> routeStations
    ) {

        for (RouteStation routeStation : routeStations) {

            if (routeStation
                    .getStation()
                    .getCode()
                    .equals(stationCode)) {

                return routeStation;
            }
        }

        return null;
    }

    // ========================================================
    // Find latest GPS observation before station arrival
    // ========================================================

    private GpsHistory findGpsBeforeArrival(
            List<GpsHistory> gpsHistory,
            Instant arrivalTime
    ) {

        GpsHistory latest = null;

        for (GpsHistory gps : gpsHistory) {

            if (gps.getTimestamp()
                    .isAfter(arrivalTime)) {

                break;
            }

            latest = gps;
        }

        return latest;
    }

    // ========================================================
// Find previous station arrival time
// ========================================================

    private Instant findPreviousArrivalTime(
            List<StationArrivalHistory> arrivals,
            StationArrivalHistory currentArrival
    ) {

        Instant previousArrivalTime = null;

        for (StationArrivalHistory arrival : arrivals) {

            if (arrival == currentArrival) {
                break;
            }

            previousArrivalTime = arrival.getActualArrival();
        }

        return previousArrivalTime;
    }


// ========================================================
// Find GPS observation matching the detected delay type
// ========================================================

    private GpsHistory findObservationGps(
            List<GpsHistory> gpsHistory,
            Instant currentArrivalTime,
            Instant previousArrivalTime,
            DelayType delayType
    ) {

        GpsHistory latestBeforeArrival = null;
        GpsHistory latestMatchingDelay = null;

        for (GpsHistory gps : gpsHistory) {

            Instant timestamp = gps.getTimestamp();

            if (timestamp.isAfter(currentArrivalTime)) {
                break;
            }

            if (previousArrivalTime != null
                    && timestamp.isBefore(previousArrivalTime)) {
                continue;
            }

            latestBeforeArrival = gps;

            TrainSimulationState.DelayType gpsDelayType =
                    gps.getDelayType();

            if (gpsDelayType != null
                    && gpsDelayType == delayType) {
                latestMatchingDelay = gps;
            }
        }

        // Delay rows use an observation from the actual delay event.
        // NONE rows use the latest observation before arrival.
        if (delayType != DelayType.NONE
                && latestMatchingDelay != null) {
            return latestMatchingDelay;
        }

        return latestBeforeArrival;
    }

    // ========================================================
    // Find delay type that occurred between stations
    // ========================================================

    private DelayType findDelayTypeBetweenStations(
            List<GpsHistory> gpsHistory,
            Instant currentArrivalTime,
            Instant previousArrivalTime
    ) {

        DelayType detectedDelay = DelayType.NONE;

        for (GpsHistory gps : gpsHistory) {

            Instant timestamp = gps.getTimestamp();

            // Ignore GPS points after current station arrival
            if (timestamp.isAfter(currentArrivalTime)) {
                break;
            }

            // Ignore GPS points before previous station arrival
            if (previousArrivalTime != null
                    && timestamp.isBefore(previousArrivalTime)) {
                continue;
            }

            TrainSimulationState.DelayType delayType = gps.getDelayType();

            // Old GPS records may have NULL delayType.
            if (delayType == null) {
                continue;
            }

            // Priority:
            // WEATHER > SIGNAL > SPEED > NONE

            if (delayType == DelayType.WEATHER) {
                detectedDelay = DelayType.WEATHER;
            }
            else if (delayType == TrainSimulationState.DelayType.SIGNAL
                    && detectedDelay != TrainSimulationState.DelayType.WEATHER) {

                detectedDelay = TrainSimulationState.DelayType.SIGNAL;
            }
            else if (delayType == TrainSimulationState.DelayType.SPEED
                    && detectedDelay == TrainSimulationState.DelayType.NONE) {

                detectedDelay = TrainSimulationState.DelayType.SPEED;
            }
        }

        return detectedDelay;
    }

    // ========================================================
    // Calculate current delay
    // ========================================================

    private double calculateCurrentDelay(
            GpsHistory gps,
            RouteStation targetStation,
            List<RouteStation> routeStations
    ) {

        LocalTime scheduledArrival =
                targetStation.getArrivalTime();

        if (scheduledArrival == null) {
            return 0.0;
        }

        if (gps.getSpeedKmh() <= 0) {
            return 0.0;
        }

        double distanceKm =
                calculateDistance(
                        gps.getLatitude(),
                        gps.getLongitude(),
                        targetStation
                                .getStation()
                                .getLatitude(),
                        targetStation
                                .getStation()
                                .getLongitude()
                );

        double hours =
                distanceKm / gps.getSpeedKmh();

        long travelSeconds =
                (long) (hours * 3600);

        Instant predictedArrival =
                gps.getTimestamp()
                        .plusSeconds(
                                travelSeconds
                        );

        Instant scheduledInstant =
                createScheduledArrival(
                        gps.getTimestamp(),
                        targetStation
                );

        long delaySeconds =
                Duration.between(
                        scheduledInstant,
                        predictedArrival
                ).getSeconds();

        return delaySeconds / 60.0;
    }

    // ========================================================
    // Distance to destination
    // ========================================================

    private double calculateDistanceToDestination(
            GpsHistory gps,
            RouteStation currentStation,
            List<RouteStation> routeStations
    ) {

        int currentIndex = -1;

        for (int i = 0; i < routeStations.size(); i++) {

            if (routeStations
                    .get(i)
                    .getStation()
                    .getCode()
                    .equals(
                            currentStation
                                    .getStation()
                                    .getCode()
                    )) {

                currentIndex = i;
                break;
            }
        }

        if (currentIndex == -1) {
            return 0.0;
        }

        double totalDistance = 0.0;

        // GPS → current/target station
        totalDistance +=
                calculateDistance(
                        gps.getLatitude(),
                        gps.getLongitude(),
                        currentStation
                                .getStation()
                                .getLatitude(),
                        currentStation
                                .getStation()
                                .getLongitude()
                );

        // Current station → destination
        for (
                int i = currentIndex;
                i < routeStations.size() - 1;
                i++
        ) {

            RouteStation from =
                    routeStations.get(i);

            RouteStation to =
                    routeStations.get(i + 1);

            totalDistance +=
                    calculateDistance(
                            from.getStation().getLatitude(),
                            from.getStation().getLongitude(),
                            to.getStation().getLatitude(),
                            to.getStation().getLongitude()
                    );
        }

        return totalDistance;
    }

    // ========================================================
    // Scheduled arrival → minutes from midnight
    // ========================================================

    private double getScheduledArrivalMinutes(
            RouteStation routeStation
    ) {

        if (routeStation.getArrivalTime() == null) {
            return 0.0;
        }

        return routeStation
                .getArrivalTime()
                .toSecondOfDay()
                / 60.0;
    }

    // ========================================================
    // Time of day
    // ========================================================

    private int getTimeOfDay(
            Instant timestamp
    ) {

        return timestamp
                .atZone(
                        ZoneId.of("Asia/Kolkata")
                )
                .getHour();
    }

    // ========================================================
    // Scheduled arrival Instant
    // ========================================================

    private Instant createScheduledArrival(
            Instant simulationTimestamp,
            RouteStation routeStation
    ) {

        if (routeStation.getArrivalTime() == null) {
            return simulationTimestamp;
        }

        var simulationDate =
                simulationTimestamp
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate();

        int scheduleDay =
                routeStation.getDay() == null
                        ? 1
                        : routeStation.getDay();

        var scheduledDate =
                simulationDate.plusDays(
                        scheduleDay - 1L
                );

        return routeStation
                .getArrivalTime()
                .atDate(scheduledDate)
                .atZone(
                        ZoneId.of("Asia/Kolkata")
                )
                .toInstant();
    }

    // ========================================================
    // Haversine distance
    // ========================================================

    private double calculateDistance(
            double latitude1,
            double longitude1,
            double latitude2,
            double longitude2
    ) {

        final double EARTH_RADIUS_KM =
                6371.0;

        double lat1 =
                Math.toRadians(latitude1);

        double lat2 =
                Math.toRadians(latitude2);

        double deltaLat =
                Math.toRadians(
                        latitude2 - latitude1
                );

        double deltaLon =
                Math.toRadians(
                        longitude2 - longitude1
                );

        double a =
                Math.sin(deltaLat / 2)
                        * Math.sin(deltaLat / 2)
                        +
                        Math.cos(lat1)
                                * Math.cos(lat2)
                                * Math.sin(deltaLon / 2)
                                * Math.sin(deltaLon / 2);

        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return EARTH_RADIUS_KM * c;
    }
}