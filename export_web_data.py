import sqlite3
import json
import os
import shutil

def export_data(db_path="app/src/main/assets/kmrl_timetable.db", output_dir="web/data"):
    print(f"Opening database: {db_path}")
    if not os.path.exists(db_path):
        raise FileNotFoundError(f"Database file not found: {db_path}")

    os.makedirs(output_dir, exist_ok=True)
    timetables_dir = os.path.join(output_dir, "timetables")
    os.makedirs(timetables_dir, exist_ok=True)

    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    # 1. Export Stations
    cursor.execute("SELECT id, code, name, sequence FROM stations ORDER BY sequence ASC")
    stations = [
        {"id": row[0], "code": row[1], "name": row[2], "sequence": row[3]}
        for row in cursor.fetchall()
    ]
    with open(os.path.join(output_dir, "stations.json"), "w", encoding="utf-8") as f:
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

    with open(os.path.join(output_dir, "timetables.json"), "w", encoding="utf-8") as f:
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

    print(f"Successfully exported {total_exported_schedules} timetable schedule files into {timetables_dir}")
    conn.close()

if __name__ == "__main__":
    export_data()
