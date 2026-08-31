import json
import sys
from pathlib import Path
from datetime import datetime


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

import psycopg2


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
    """
    Convert dataset time strings into Python time-compatible
    strings for PostgreSQL.

    Dataset uses 'None' for missing arrival/departure.
    """
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
    """
    Build:

        station_code -> station information

    This allows us to quickly resolve a schedule's station_code.
    """

    lookup = {}

    for feature in stations_data["features"]:

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
# TRAIN
# ============================================================

def find_train(trains_data, train_number):

    for feature in trains_data["features"]:

        properties = feature.get("properties", {})

        if properties.get("number") == train_number:
            # Return the complete GeoJSON feature so we keep both
            # train properties and the real LineString geometry.
            return feature

    return None


def extract_line_string_geometry(train_feature):
    """
    Extract the real railway LineString from trains.json.

    GeoJSON coordinates are stored as:
        [longitude, latitude]

    Returns the geometry as a JSON string so PostgreSQL can store it
    without requiring PostGIS.
    """

    geometry = train_feature.get("geometry")

    if not geometry:
        raise RuntimeError("Train has no geometry")

    if geometry.get("type") != "LineString":
        raise RuntimeError(
            f"Expected LineString geometry, got {geometry.get('type')}"
        )

    coordinates = geometry.get("coordinates")

    if not coordinates or len(coordinates) < 2:
        raise RuntimeError("Train LineString contains fewer than 2 coordinates")

    return json.dumps(geometry, separators=(",", ":"))


# ============================================================
# SCHEDULE
# ============================================================

def find_schedule(schedules_data, train_number):

    records = [
        record
        for record in schedules_data
        if record.get("train_number") == train_number
    ]

    # The dataset records are already in route order.
    return records


# ============================================================
# IMPORT
# ============================================================

def import_train(train_number):

    print()
    print("=" * 60)
    print(f"Importing train {train_number}")
    print("=" * 60)

    # --------------------------------------------------------
    # Load JSON
    # --------------------------------------------------------

    stations_data, trains_data, schedules_data = load_data()

    # --------------------------------------------------------
    # Find train
    # --------------------------------------------------------

    train_data = find_train(trains_data, train_number)

    if not train_data:
        raise RuntimeError(
            f"Train {train_number} not found in trains.json"
        )

    train_properties = train_data["properties"]
    geometry_json = extract_line_string_geometry(train_data)

    print(f"Train found: {train_properties['name']}")
    print(
        f"Route: "
        f"{train_properties['from_station_code']} → "
        f"{train_properties['to_station_code']}"
    )
    print(
        f"Geometry: LineString with "
        f"{len(train_data['geometry']['coordinates'])} coordinates"
    )

    # --------------------------------------------------------
    # Find schedule
    # --------------------------------------------------------

    schedule = find_schedule(
        schedules_data,
        train_number
    )

    if not schedule:
        raise RuntimeError(
            f"No schedule found for train {train_number}"
        )

    print(f"Schedule stops: {len(schedule)}")

    # --------------------------------------------------------
    # Build station lookup
    # --------------------------------------------------------

    station_lookup = build_station_lookup(stations_data)

    # --------------------------------------------------------
    # Verify stations before touching DB
    # --------------------------------------------------------

    missing_stations = []

    for stop in schedule:

        station_code = stop["station_code"]

        if station_code not in station_lookup:
            missing_stations.append(station_code)

    if missing_stations:

        raise RuntimeError(
            "Missing stations: "
            + ", ".join(missing_stations)
        )

    print("All schedule stations found.")

    # --------------------------------------------------------
    # Connect database
    # --------------------------------------------------------

    connection = get_connection()

    try:

        cursor = connection.cursor()

        # ====================================================
        # 1. IMPORT STATIONS
        # ====================================================

        print()
        print("Importing stations...")

        station_ids = {}

        for stop in schedule:

            station_code = stop["station_code"]

            station = station_lookup[station_code]

            cursor.execute(
                """
                INSERT INTO stations
                    (code, name, latitude, longitude, created_at)
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

            station_id = cursor.fetchone()[0]

            station_ids[station_code] = station_id

        print(f"Stations imported/updated: {len(station_ids)}")

        # ====================================================
        # 2. CREATE ROUTE
        # ====================================================

        route_code = train_number
        route_name = train_properties["name"]

        print()
        print("Creating route...")

        cursor.execute(
            """
            INSERT INTO routes
                (route_code, name, geometry_json, created_at)
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
                route_name,
                geometry_json,
                get_now()
            )
        )

        route_id = cursor.fetchone()[0]

        print(f"Route ID: {route_id}")

        # ====================================================
        # 3. CREATE TRAIN
        # ====================================================

        print()
        print("Creating train...")

        cursor.execute(
            """
            INSERT INTO trains
                (train_no, name, route_id, active, created_at)
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
                train_properties["name"],
                route_id,
                get_now()
            )
        )

        train_id = cursor.fetchone()[0]

        print(f"Train ID: {train_id}")

        # ====================================================
        # 4. REMOVE OLD ROUTE STATIONS
        # ====================================================

        cursor.execute(
            """
            DELETE FROM route_stations
            WHERE route_id = %s;
            """,
            (route_id,)
        )

        # ====================================================
        # 5. CREATE ROUTE STATIONS
        # ====================================================

        print()
        print("Creating route stations...")

        for sequence, stop in enumerate(schedule, start=1):

            station_code = stop["station_code"]

            station_id = station_ids[station_code]

            arrival = parse_time(stop.get("arrival"))
            departure = parse_time(stop.get("departure"))
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

        print(
            f"Route stations created: {len(schedule)}"
        )

        # ====================================================
        # COMMIT
        # ====================================================

        connection.commit()

        print()
        print("=" * 60)
        print("IMPORT SUCCESSFUL")
        print("=" * 60)
        print(f"Train       : {train_number}")
        print(f"Train name  : {train_properties['name']}")
        print(f"Route ID    : {route_id}")
        print(f"Train ID    : {train_id}")
        print(f"Stations    : {len(station_ids)}")
        print(f"Route stops : {len(schedule)}")
        print("=" * 60)

    except Exception:

        connection.rollback()

        print()
        print("IMPORT FAILED")
        print("Database transaction rolled back.")

        raise

    finally:

        cursor.close()
        connection.close()


# ============================================================
# MAIN
# ============================================================

if __name__ == "__main__":

    if len(sys.argv) != 2:

        print(
            "Usage:\n"
            "  python import_railway_data.py 12031"
        )

        sys.exit(1)

    train_number = sys.argv[1]

    import_train(train_number)
