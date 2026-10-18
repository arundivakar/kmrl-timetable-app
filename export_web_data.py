import sqlite3
import json
import os
import shutil

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
DEFAULT_DB_PATH = os.path.join(PROJECT_ROOT, "kmrl_timetable.db")
ASSETS_DB_PATH = os.path.join(SCRIPT_DIR, "app", "src", "main", "assets", "kmrl_timetable.db")

def export_data(db_path=None, output_dirs=None):
    if db_path is None:
        if os.path.exists(os.path.join(SCRIPT_DIR, "kmrl_timetable.db")):
            db_path = os.path.join(SCRIPT_DIR, "kmrl_timetable.db")
        elif os.path.exists(ASSETS_DB_PATH):
            db_path = ASSETS_DB_PATH
        elif os.path.exists(DEFAULT_DB_PATH):
            db_path = DEFAULT_DB_PATH
        else:
            db_path = "kmrl_timetable.db"

    if output_dirs is None:
        output_dirs = [
            os.path.join(SCRIPT_DIR, "web", "data"),
            os.path.join(SCRIPT_DIR, "docs", "data"),
        ]
    elif isinstance(output_dirs, str):
        output_dirs = [output_dirs]

    print(f"Opening database: {db_path}")
    if not os.path.exists(db_path):
        raise FileNotFoundError(f"Database file not found: {db_path}")

    primary_dir = output_dirs[0]
    os.makedirs(primary_dir, exist_ok=True)
    timetables_dir = os.path.join(primary_dir, "timetables")
    os.makedirs(timetables_dir, exist_ok=True)

    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    # 1. Export Stations
    cursor.execute("SELECT id, code, name, sequence FROM stations ORDER BY sequence ASC")
    stations = [
        {"id": row[0], "code": row[1], "name": row[2], "sequence": row[3]}
        for row in cursor.fetchall()
    ]
    with open(os.path.join(primary_dir, "stations.json"), "w", encoding="utf-8") as f:
        json.dump(stations, f, separators=(",", ":"))
    print(f"Exported {len(stations)} stations to stations.json")

    # 2. Export Day Defaults
    cursor.execute("SELECT day_of_week, timetable_name FROM day_defaults ORDER BY day_of_week ASC")
    day_defaults = {row[0]: row[1] for row in cursor.fetchall()}

    # 3. Export Timetables Metadata
    cursor.execute("SELECT id, name, type, train_count, notes, bundled FROM timetables ORDER BY id DESC")
    timetables_meta = []
    timetable_ids = []
    for row in cursor.fetchall():
        tt_id, name, tt_type, train_count, notes, bundled = row
        timetables_meta.append({
            "id": tt_id,
            "name": name,
            "type": tt_type,
            "train_count": train_count,
            "notes": notes or "",
            "bundled": bundled
        })
        timetable_ids.append((tt_id, name))

    with open(os.path.join(primary_dir, "timetables.json"), "w", encoding="utf-8") as f:
        json.dump({"defaults": day_defaults, "timetables": timetables_meta}, f, separators=(",", ":"))
    print(f"Exported {len(timetables_meta)} timetables metadata to timetables.json")

    # 4. Export each timetable's trips and stop_times
    total_exported_schedules = 0
    for tt_id, name in timetable_ids:
        cursor.execute("SELECT id, train_no, direction FROM trips WHERE timetable_id = ? ORDER BY id ASC", (tt_id,))
        trips = cursor.fetchall()
        if not trips:
            continue

        trips_data = []
        for trip_id, train_no, direction in trips:
            cursor.execute(
                "SELECT station_id, departure_time FROM stop_times WHERE trip_id = ? AND departure_time IS NOT NULL ORDER BY station_id ASC",
                (trip_id,)
            )
            st_rows = cursor.fetchall()
            if not st_rows:
                continue

            stops = {str(st_id): dep_time for st_id, dep_time in st_rows}
            term_dep = min((dep_time for _, dep_time in st_rows), default="")
            trips_data.append({
                "train_no": train_no,
                "direction": direction,
                "terminal_departure": term_dep,
                "stops": stops
            })

        clean_name = name.strip()
        safe_filename = "".join(c for c in clean_name if c.isalnum() or c in ("-", "_", ".")) + ".json"
        with open(os.path.join(timetables_dir, safe_filename), "w", encoding="utf-8") as f:
            json.dump({
                "name": clean_name,
                "id": tt_id,
                "trip_count": len(trips_data),
                "trips": trips_data
            }, f, separators=(",", ":"))
        total_exported_schedules += 1

    # Remove any stale timetable files (e.g. invalid 'in.json')
    valid_filenames = {
        "".join(c for c in name.strip() if c.isalnum() or c in ("-", "_", ".")) + ".json"
        for _, name in timetable_ids
    }
    for existing_file in os.listdir(timetables_dir):
        if existing_file.endswith(".json") and existing_file not in valid_filenames:
            os.remove(os.path.join(timetables_dir, existing_file))
            print(f"Removed stale schedule file: {existing_file}")

    print(f"Successfully exported {total_exported_schedules} timetable schedule files into {timetables_dir}")
    conn.close()

    # Sync to any additional output directories (e.g. docs/data for GitHub Pages)
    for other_dir in output_dirs[1:]:
        if os.path.abspath(other_dir) != os.path.abspath(primary_dir):
            os.makedirs(other_dir, exist_ok=True)
            other_tt_dir = os.path.join(other_dir, "timetables")
            if os.path.exists(other_tt_dir):
                for existing_file in os.listdir(other_tt_dir):
                    if existing_file.endswith(".json") and existing_file not in valid_filenames:
                        os.remove(os.path.join(other_tt_dir, existing_file))
                        print(f"Removed stale schedule file from {other_dir}: {existing_file}")
            shutil.copytree(primary_dir, other_dir, dirs_exist_ok=True)
            print(f"Synchronized web data -> {other_dir}")

    return {
        "stations": len(stations),
        "timetables": len(timetables_meta),
        "schedules": total_exported_schedules
    }

if __name__ == "__main__":
    export_data()
