import json
import sys
from pathlib import Path
from datetime import datetime
from collections import defaultdict

import psycopg2


# ============================================================
# CONFIGURATION
# ============================================================

DB_HOST = "localhost"
DB_PORT = 5432
DB_NAME = "railway_eta"
DB_USER = "postgres"
DB_PASSWORD = "123Password@#"

DATA_DIR = Path(__file__).resolve().parent.parent / "data"

STATIONS_FILE = DATA_DIR / "stations.json"
TRAINS_FILE = DATA_DIR / "trains.json"
SCHEDULES_FILE = DATA_DIR / "schedules.json"


# ============================================================
# DATABASE
# ============================================================

def get_connection():
    return psycopg2.connect(
        host=DB_HOST,
        port=DB_PORT,
        database=DB_NAME,
        user=DB_USER,
        password=DB_PASSWORD
    )


# ============================================================
# HELPERS
# ============================================================

def load_json(path):
    print(f"Loading {path.name}...")

    with open(path, "r", encoding="utf-8") as file:
        return json.load(file)


def parse_time(value):
    if value is None or value == "None":
        return None

    return value


def get_now():
    return datetime.now().astimezone()


# ============================================================
# LOAD DATA
# ============================================================

def load_data():
    stations_data = load_json(STATIONS_FILE)
    trains_data = load_json(TRAINS_FILE)
    schedules_data = load_json(SCHEDULES_FILE)

    return stations_data, trains_data, schedules_data


# ============================================================
# STATIONS
# ============================================================

def build_station_lookup(stations_data):

    lookup = {}

    for feature in stations_data.get("features", []):

        properties = feature.get("properties", {})
        geometry = feature.get("geometry")

        code = properties.get("code")

        if not code:
            continue

        latitude = None
        longitude = None

        if geometry and geometry.get("type") == "Point":

            coordinates = geometry.get("coordinates")

            if coordinates and len(coordinates) >= 2:
                longitude = coordinates[0]
                latitude = coordinates[1]

        lookup[code] = {
            "code": code,
            "name": properties.get("name"),
            "latitude": latitude,
            "longitude": longitude
        }

    return lookup


# ============================================================
# TRAINS
# ============================================================

def build_train_lookup(trains_data):

    lookup = {}

    for feature in trains_data.get("features", []):

        properties = feature.get("properties", {})

        train_number = properties.get("number")

        if not train_number:
            continue

        train_number = str(train_number)

        lookup[train_number] = feature

    return lookup


# ============================================================
# SCHEDULES
# ============================================================

def build_schedule_lookup(schedules_data):

    lookup = defaultdict(list)

    for record in schedules_data:

        train_number = record.get("train_number")

        if not train_number:
            continue

        train_number = str(train_number)

        lookup[train_number].append(record)

    return lookup


# ============================================================
# GEOMETRY
# ============================================================

def extract_line_string_geometry(train_feature):

    geometry = train_feature.get("geometry")

    if not geometry:
        raise RuntimeError("Train has no geometry")

    if geometry.get("type") != "LineString":
        raise RuntimeError(
            f"Expected LineString geometry, got "
            f"{geometry.get('type')}"
        )

    coordinates = geometry.get("coordinates")

    if not coordinates or len(coordinates) < 2:
        raise RuntimeError(
            "Train LineString contains fewer than 2 coordinates"
        )

    return json.dumps(
        geometry,
        separators=(",", ":")
    )


# ============================================================
# IMPORT ONE TRAIN
# ============================================================

def import_train(
    cursor,
    train_number,
    train_data,
    schedule,
    station_lookup
):

    train_properties = train_data.get("properties", {})

    train_name = train_properties.get(
        "name",
        f"Train {train_number}"
    )

    # --------------------------------------------------------
    # Geometry
    # --------------------------------------------------------

    geometry_json = extract_line_string_geometry(train_data)

    coordinates = train_data["geometry"]["coordinates"]

    # --------------------------------------------------------
    # Verify schedule stations
    # --------------------------------------------------------

    missing_stations = []

    for stop in schedule:

        station_code = stop.get("station_code")

        if not station_code:
            missing_stations.append("(missing code)")
            continue

        if station_code not in station_lookup:
            missing_stations.append(station_code)

    if missing_stations:

        raise RuntimeError(
            "Missing stations: "
            + ", ".join(missing_stations)
        )

    # --------------------------------------------------------
    # 1. IMPORT / UPDATE STATIONS
    # --------------------------------------------------------

    station_ids = {}

    for stop in schedule:

        station_code = stop["station_code"]

        station = station_lookup[station_code]

        cursor.execute(
            """
            INSERT INTO stations
                (
                    code,
                    name,
                    latitude,
                    longitude,
                    created_at
                )
            VALUES
                (%s, %s, %s, %s, %s)

            ON CONFLICT (code)
            DO UPDATE SET
                name = EXCLUDED.name,
                latitude = EXCLUDED.latitude,
                longitude = EXCLUDED.longitude

            RETURNING id;
            """,
            (
                station["code"],
                station["name"],
                station["latitude"],
                station["longitude"],
                get_now()
            )
        )

        station_ids[station_code] = cursor.fetchone()[0]

    # --------------------------------------------------------
    # 2. CREATE / UPDATE ROUTE
    # --------------------------------------------------------

    route_code = train_number

    cursor.execute(
        """
        INSERT INTO routes
            (
                route_code,
                name,
                geometry_json,
                created_at
            )
        VALUES
            (%s, %s, %s, %s)

        ON CONFLICT (route_code)
        DO UPDATE SET
            name = EXCLUDED.name,
            geometry_json = EXCLUDED.geometry_json

        RETURNING id;
        """,
        (
            route_code,
            train_name,
            geometry_json,
            get_now()
        )
    )

    route_id = cursor.fetchone()[0]

    # --------------------------------------------------------
    # 3. CREATE / UPDATE TRAIN
    # --------------------------------------------------------

    cursor.execute(
        """
        INSERT INTO trains
            (
                train_no,
                name,
                route_id,
                active,
                created_at
            )
        VALUES
            (%s, %s, %s, TRUE, %s)

        ON CONFLICT (train_no)
        DO UPDATE SET
            name = EXCLUDED.name,
            route_id = EXCLUDED.route_id

        RETURNING id;
        """,
        (
            train_number,
            train_name,
            route_id,
            get_now()
        )
    )

    train_id = cursor.fetchone()[0]

    # --------------------------------------------------------
    # 4. REMOVE OLD ROUTE STATIONS
    # --------------------------------------------------------

    cursor.execute(
        """
        DELETE FROM route_stations
        WHERE route_id = %s;
        """,
        (route_id,)
    )

    # --------------------------------------------------------
    # 5. CREATE ROUTE STATIONS
    # --------------------------------------------------------

    for sequence, stop in enumerate(schedule, start=1):

        station_code = stop["station_code"]

        station_id = station_ids[station_code]

        arrival = parse_time(
            stop.get("arrival")
        )

        departure = parse_time(
            stop.get("departure")
        )

        day = stop.get("day")

        cursor.execute(
            """
            INSERT INTO route_stations
                (
                    route_id,
                    station_id,
                    sequence_number,
                    arrival_time,
                    departure_time,
                    day
                )
            VALUES
                (%s, %s, %s, %s, %s, %s);
            """,
            (
                route_id,
                station_id,
                sequence,
                arrival,
                departure,
                day
            )
        )

    return {
        "train_id": train_id,
        "route_id": route_id,
        "station_count": len(station_ids),
        "schedule_count": len(schedule),
        "geometry_points": len(coordinates)
    }


# ============================================================
# IMPORT ALL TRAINS
# ============================================================

def import_all():

    print()
    print("=" * 70)
    print("RAILWAY DATA IMPORT")
    print("=" * 70)
    print()

    # --------------------------------------------------------
    # LOAD EVERYTHING ONCE
    # --------------------------------------------------------

    stations_data, trains_data, schedules_data = load_data()

    print()
    print("Building lookups...")

    station_lookup = build_station_lookup(
        stations_data
    )

    train_lookup = build_train_lookup(
        trains_data
    )

    schedule_lookup = build_schedule_lookup(
        schedules_data
    )

    print(
        f"Stations available : {len(station_lookup)}"
    )

    print(
        f"Trains available   : {len(train_lookup)}"
    )

    print(
        f"Schedule trains    : {len(schedule_lookup)}"
    )

    print()

    # --------------------------------------------------------
    # CONNECT
    # --------------------------------------------------------

    connection = get_connection()

    imported = 0
    skipped = 0
    total_geometry = 0

    skipped_trains = []

    try:

        cursor = connection.cursor()

        total_trains = len(train_lookup)

        print("=" * 70)
        print(f"IMPORTING {total_trains} TRAINS")
        print("=" * 70)
        print()

        # ----------------------------------------------------
        # PROCESS EVERY TRAIN
        # ----------------------------------------------------

        for index, (train_number, train_data) in enumerate(
            train_lookup.items(),
            start=1
        ):

            print(
                f"[{index}/{total_trains}] "
                f"Train {train_number}",
                end=" "
            )

            try:

                schedule = schedule_lookup.get(
                    train_number,
                    []
                )

                if not schedule:
                    raise RuntimeError(
                        "No schedule found"
                    )

                result = import_train(
                    cursor,
                    train_number,
                    train_data,
                    schedule,
                    station_lookup
                )

                # Commit every successful train.
                # This prevents one bad train from
                # rolling back everything imported before it.

                connection.commit()

                imported += 1

                total_geometry += result[
                    "geometry_points"
                ]

                print(
                    f"✓ "
                    f"{result['schedule_count']} stops, "
                    f"{result['geometry_points']} geometry points"
                )

            except Exception as error:

                connection.rollback()

                skipped += 1

                skipped_trains.append(
                    (
                        train_number,
                        str(error)
                    )
                )

                print(
                    f"✗ SKIPPED: {error}"
                )

        cursor.close()

    finally:

        connection.close()

    # --------------------------------------------------------
    # SUMMARY
    # --------------------------------------------------------

    print()
    print("=" * 70)
    print("IMPORT COMPLETE")
    print("=" * 70)

    print(
        f"Trains found       : {total_trains}"
    )

    print(
        f"Trains imported    : {imported}"
    )

    print(
        f"Trains skipped     : {skipped}"
    )

    print(
        f"Total geometry pts : {total_geometry}"
    )

    print("=" * 70)

    # --------------------------------------------------------
    # SKIPPED TRAINS
    # --------------------------------------------------------

    if skipped_trains:

        print()
        print("=" * 70)
        print("SKIPPED TRAINS")
        print("=" * 70)

        for train_number, reason in skipped_trains:

            print(
                f"{train_number}: {reason}"
            )

        print("=" * 70)

    print()
    print("Database verification:")
    print()
    print("  SELECT COUNT(*) FROM trains;")
    print("  SELECT COUNT(*) FROM routes;")
    print(
        "  SELECT COUNT(*) FROM routes "
        "WHERE geometry_json IS NOT NULL "
        "AND geometry_json <> '';"
    )
    print()


# ============================================================
# MAIN
# ============================================================

if __name__ == "__main__":

    import_all()