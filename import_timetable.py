"""
KMRL Timetable Excel -> SQLite Importer v2
Uses direct ZIP/XML parsing of the xlsx file for speed.
xlsx files are ZIP archives; each sheet is xl/worksheets/sheetN.xml
Shared strings are in xl/sharedStrings.xml
"""
import zipfile
import xml.etree.ElementTree as ET
import sqlite3
import os
import re
import sys
import shutil
import gzip
from datetime import datetime
from typing import Optional, Dict, List, Tuple

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

# Smart search for Excel file
DEFAULT_XLSX_PATH = ""
candidates = [
    os.path.join(BASE_DIR, "Latest Timetable.xlsx"),
    os.path.join(BASE_DIR, "Latest Timetable1.xlsx"),
    os.path.join(os.path.dirname(BASE_DIR), "Latest Timetable.xlsx"),
    os.path.join(os.path.dirname(BASE_DIR), "Latest Timetable1.xlsx"),
]
for p in candidates:
    if os.path.exists(p):
        DEFAULT_XLSX_PATH = p
        break

if not DEFAULT_XLSX_PATH:
    # Check any xlsx file
    for search_dir in [BASE_DIR, os.path.dirname(BASE_DIR)]:
        if os.path.exists(search_dir):
            for f in os.listdir(search_dir):
                if f.endswith(".xlsx") and not f.startswith("~$"):
                    DEFAULT_XLSX_PATH = os.path.join(search_dir, f)
                    break
        if DEFAULT_XLSX_PATH:
            break

if not DEFAULT_XLSX_PATH:
    DEFAULT_XLSX_PATH = os.path.join(BASE_DIR, "Latest Timetable.xlsx")

DB_PATH = os.path.join(BASE_DIR, "kmrl_timetable.db")

# Detect assets and docs directories
if os.path.exists(os.path.join(BASE_DIR, "app", "src", "main", "assets")):
    APP_ASSETS_DIR = os.path.join(BASE_DIR, "app", "src", "main", "assets")
    DOCS_DATA_DIR = os.path.join(BASE_DIR, "docs", "data")
else:
    APP_ASSETS_DIR = os.path.join(BASE_DIR, "kmrl_app", "app", "src", "main", "assets")
    DOCS_DATA_DIR = os.path.join(BASE_DIR, "kmrl_app", "docs", "data")

APP_ASSETS_DB_PATH = os.path.join(APP_ASSETS_DIR, "kmrl_timetable.db")

NS = {
    'ss': 'http://schemas.openxmlformats.org/spreadsheetml/2006/main',
    'r':  'http://schemas.openxmlformats.org/officeDocument/2006/relationships',
}

# ─── Station metadata ────────────────────────────────────────────────────────
STATIONS = [
    (1,  "ALVA", "Aluva"),
    (2,  "PNCU", "Pulinchodu"),
    (3,  "CPPY", "Companypady"),
    (4,  "AATK", "Ambattukavu"),
    (5,  "MUTT", "Muttom"),
    (6,  "KLMT", "Kalamassery"),
    (7,  "CCUV", "Cusat"),
    (8,  "PDPM", "Pathadipalam"),
    (9,  "EDAP", "Edapally"),
    (10, "CGPP", "Changampuzha Park"),
    (11, "PARV", "Palarivattom"),
    (12, "JLSD", "JLN Stadium"),
    (13, "KALR", "Kaloor"),
    (14, "TNHL", "Town Hall"),
    (15, "MGRD", "MG Road"),
    (16, "MACE", "Maharaja's College"),
    (17, "ERSH", "Ernakulam South"),
    (18, "KVTR", "Kadavanthra"),
    (19, "EMKM", "Elamkulam"),
    (20, "VYTA", "Vyttila"),
    (21, "TKDM", "Thykoodam"),
    (22, "PETT", "Pettah"),
    (23, "VAKK", "Vadakkekotta"),
    (24, "SNJN", "SN Junction"),
    (25, "TPHT", "Tripunithura"),
]
STATION_SEQ = {s[1]: s[0] for s in STATIONS}

SKIP_SHEETS = {
    "TIMETABLE NAME", "TT TODAY", "TT TOMORROW", "TRAIN LOCATION", "BG",
    "SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY",
    "SATURDAY", "NEXTSUNDAY",
    "TERM DEPT WEEKDAY", "TERM DEPT SUNDAY", "TERM DEPT SATURDAY",
    "ALTPSTW", "SUMMARY", "DEFAULT",
}

TIME_RE = re.compile(r'^(\d{1,2}):(\d{2})(?::(\d{2}))?$')
DEPOT_KEYWORDS = ("depot", "induct", "sdg", "3rd", "siding", "alva up", "alva dn")

def classify_timetable(name: str) -> str:
    n = name.upper()
    if "FEST" in n or "ONAM" in n or "SHIVARATRI" in n: return "FESTIVAL"
    if "UPSC" in n or "SUPSC" in n: return "SPECIAL"
    if "LULU" in n or "ISL" in n:   return "SPECIAL"
    m = re.match(r'^\d+([WS])', n)
    if m:
        return "WEEKDAY" if m.group(1) == 'W' else "SUNDAY"
    return "SPECIAL"

def parse_time_str(val: Optional[str]) -> Optional[str]:
    if not val:
        return None
    # Strip any literal single quotes that Excel sometimes embeds in text cells
    v = val.replace("'", "").strip()
    vl = v.lower()
    if any(k in vl for k in DEPOT_KEYWORDS):
        return None
    m = TIME_RE.match(v)
    if m:
        h, mn, s = int(m.group(1)), int(m.group(2)), int(m.group(3) or 0)
        return f"{h:02d}:{mn:02d}:{s:02d}"
    return None

# ─── xlsx ZIP reader ─────────────────────────────────────────────────────────

def col_letter_to_num(col: str) -> int:
    """Convert Excel column letter(s) to 1-based integer. E.g. A->1, Z->26, AA->27"""
    result = 0
    for ch in col.upper():
        result = result * 26 + (ord(ch) - ord('A') + 1)
    return result

def parse_cell_ref(ref: str) -> Tuple[int, int]:
    """Parse cell ref like 'A2' or 'BZ100' -> (row, col) both 1-based"""
    m = re.match(r'^([A-Z]+)(\d+)$', ref.upper())
    col = col_letter_to_num(m.group(1))
    row = int(m.group(2))
    return row, col

class XlsxFastReader:
    """Fast xlsx reader using ZIP + XML directly"""
    
    def __init__(self, path: str):
        self.zf = zipfile.ZipFile(path, 'r')
        self._load_shared_strings()
        self._load_workbook_rels()
    
    def _load_shared_strings(self):
        """Load shared string table (text values stored by index)"""
        self.shared_strings = []
        try:
            with self.zf.open('xl/sharedStrings.xml') as f:
                tree = ET.parse(f)
                root = tree.getroot()
                for si in root.findall('.//{http://schemas.openxmlformats.org/spreadsheetml/2006/main}si'):
                    # Get all text nodes concatenated
                    texts = []
                    for t in si.iter('{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t'):
                        if t.text:
                            texts.append(t.text)
                    self.shared_strings.append(''.join(texts))
        except KeyError:
            pass  # no shared strings
    
    def _load_workbook_rels(self):
        """Load sheet name -> XML path mapping"""
        self.sheet_paths = {}   # sheet_name -> xml_path in ZIP
        self.sheet_names = []   # ordered list
        
        with self.zf.open('xl/workbook.xml') as f:
            tree = ET.parse(f)
            root = tree.getroot()
            sheets = root.findall('.//{http://schemas.openxmlformats.org/spreadsheetml/2006/main}sheet')
            sheet_rels = {}
            
            # Load workbook.xml.rels for sheet paths
            with self.zf.open('xl/_rels/workbook.xml.rels') as rf:
                rtree = ET.parse(rf)
                for rel in rtree.getroot():
                    rid = rel.get('Id')
                    target = rel.get('Target')
                    sheet_rels[rid] = target
            
            for sh in sheets:
                name = sh.get('name')
                rid  = sh.get('{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id')
                target = sheet_rels.get(rid, '')
                # target might be 'worksheets/sheet1.xml' or absolute
                if not target.startswith('/'):
                    target = 'xl/' + target
                else:
                    target = target.lstrip('/')
                self.sheet_names.append(name)
                self.sheet_paths[name] = target
    
    def read_sheet(self, sheet_name: str) -> Dict[int, Dict[int, str]]:
        """
        Read a sheet and return {row: {col: cell_value_str}}
        Only returns cells with actual string or numeric content.
        """
        path = self.sheet_paths.get(sheet_name)
        if not path:
            return {}
        
        rows: Dict[int, Dict[int, str]] = {}
        
        with self.zf.open(path) as f:
            # Parse XML iteratively for memory efficiency
            context = ET.iterparse(f, events=('end',))
            SS_NS = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
            
            for event, elem in context:
                if elem.tag == f'{SS_NS}c':
                    ref  = elem.get('r', '')
                    t    = elem.get('t', '')  # type: 's'=shared str, 'inlineStr', 'str', 'b', 'n'=number, ''=number
                    
                    v_elem = elem.find(f'{SS_NS}v')
                    v_text = v_elem.text if v_elem is not None else None
                    
                    if not ref:
                        elem.clear()
                        continue
                    
                    row, col = parse_cell_ref(ref)
                    
                    if t == 's':
                        # Shared string index
                        if v_text is not None:
                            idx = int(v_text)
                            if 0 <= idx < len(self.shared_strings):
                                val = self.shared_strings[idx]
                                if row not in rows:
                                    rows[row] = {}
                                rows[row][col] = val
                    elif t in ('str', 'inlineStr'):
                        # Formula result string or inline string
                        if v_text:
                            if row not in rows:
                                rows[row] = {}
                            rows[row][col] = v_text
                    elif t == '':
                        # Numeric cell — could be:
                        #   0 < val < 1  : Excel time fraction (e.g. 0.30694 = 07:22:00)
                        #   small int    : train number (e.g. 7 -> "07")
                        #   0            : empty/no-stop marker
                        if v_text is not None:
                            try:
                                num = float(v_text)
                                if 0 < num < 1:
                                    # Convert Excel time fraction to HH:MM:SS string
                                    total_sec = round(num * 86400)
                                    h = total_sec // 3600
                                    mn = (total_sec % 3600) // 60
                                    s = total_sec % 60
                                    val_str = f"{h:02d}:{mn:02d}:{s:02d}"
                                    if row not in rows:
                                        rows[row] = {}
                                    rows[row][col] = val_str
                                elif num > 0 and num == int(num) and int(num) < 100:
                                    # Small positive integer — likely a train number
                                    if row not in rows:
                                        rows[row] = {}
                                    rows[row][col] = str(int(num)).zfill(2)
                                # else: 0 or large number — skip (no-stop or date serial)
                            except (ValueError, OverflowError):
                                pass
                    
                    elem.clear()
        
        return rows
    
    def close(self):
        self.zf.close()

# ─── DB schema ───────────────────────────────────────────────────────────────

def create_schema(conn):
    conn.executescript("""
    PRAGMA journal_mode=WAL;
    CREATE TABLE IF NOT EXISTS `stations` (`id` INTEGER NOT NULL, `code` TEXT NOT NULL, `name` TEXT NOT NULL, `sequence` INTEGER NOT NULL, PRIMARY KEY(`id`));
    CREATE TABLE IF NOT EXISTS `timetables` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `train_count` INTEGER, `notes` TEXT, `bundled` INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timetable_id` INTEGER NOT NULL, `train_no` TEXT NOT NULL, `direction` TEXT NOT NULL, `col_index` INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS `stop_times` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trip_id` INTEGER NOT NULL, `station_id` INTEGER NOT NULL, `departure_time` TEXT);
    CREATE INDEX IF NOT EXISTS `index_stop_times_trip_id` ON `stop_times` (`trip_id`);
    CREATE INDEX IF NOT EXISTS `index_stop_times_station_id` ON `stop_times` (`station_id`);
    CREATE TABLE IF NOT EXISTS `schedule_overrides` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `override_date` TEXT NOT NULL, `timetable_name` TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS `day_defaults` (`day_of_week` INTEGER NOT NULL, `timetable_name` TEXT NOT NULL, PRIMARY KEY(`day_of_week`));
    CREATE TABLE IF NOT EXISTS `sync_metadata` (`key` TEXT NOT NULL, `value` TEXT, PRIMARY KEY(`key`));
    CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT);
    INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '6a0eb00e392c68847543393fe6211e00');
    """)
    conn.commit()
    conn.commit()

# ─── Parse TIMETABLE NAME sheet ──────────────────────────────────────────────

def parse_timetable_name(reader: XlsxFastReader, conn):
    rows = reader.read_sheet("TIMETABLE NAME")
    overrides = []
    day_defaults = {}

    for r in sorted(rows.keys()):
        cells = rows[r]
        # Rows 3-10: week schedule. Col B(2)=date serial, Col C(3)=tt name, Col G(7)=default name
        if 3 <= r <= 10:
            # Col G has the default timetable name (not a formula result)
            g_val = cells.get(7)  # col G
            b_val = cells.get(2)  # col B - date (will be a string from shared strings if text, or numeric)
            # The day of week mapping based on row:
            # Row 3=Sunday, 4=Mon, 5=Tue, 6=Wed, 7=Thu, 8=Fri, 9=Sat, 10=NextSunday
            DOW_MAP = {3: 6, 4: 0, 5: 1, 6: 2, 7: 3, 8: 4, 9: 5}  # row -> Python weekday (0=Mon,6=Sun)
            wd = DOW_MAP.get(r)
            if wd is not None and g_val and isinstance(g_val, str):
                day_defaults[wd] = g_val.strip()
        
        # Rows 12+: special dates table. Col D(4)=date, Col E(5)=tt name
        if r >= 12:
            d_val = cells.get(4)  # col D
            e_val = cells.get(5)  # col E
            if d_val and e_val and isinstance(e_val, str):
                date_str = None
                if isinstance(d_val, str):
                    # Try common string date formats
                    for fmt in ('%Y-%m-%d', '%d/%m/%Y', '%m/%d/%Y', '%d-%m-%Y'):
                        try:
                            date_str = datetime.strptime(d_val.strip(), fmt).strftime('%Y-%m-%d')
                            break
                        except ValueError:
                            pass
                elif isinstance(d_val, (int, float)):
                    # Excel date serial (days since 1899-12-30, with 1900 leap year bug)
                    try:
                        from datetime import timedelta
                        excel_epoch = datetime(1899, 12, 30)
                        date_str = (excel_epoch + timedelta(days=float(d_val))).strftime('%Y-%m-%d')
                    except Exception:
                        pass
                if date_str and e_val.strip():
                    overrides.append((date_str, e_val.strip()))

    for wd, tt_name in day_defaults.items():
        conn.execute("INSERT OR REPLACE INTO day_defaults (day_of_week, timetable_name) VALUES (?,?)",
                     (wd, tt_name))
    print(f"  day_defaults: {len(day_defaults)} entries")
    print(f"  defaults: {day_defaults}")

    conn.executemany(
        "INSERT OR IGNORE INTO schedule_overrides (override_date, timetable_name) VALUES (?,?)",
        overrides
    )
    print(f"  schedule_overrides: {len(overrides)} special dates")
    conn.commit()

def is_train_header(val: Optional[str]) -> bool:
    if not isinstance(val, str):
        return False
    norm = re.sub(r'[\s._-]+', '', val.lower())
    return norm in ('trainid', 'trainno', 'trainnum', 'trainnumber', 'train')

# ─── Parse timetable data sheet ──────────────────────────────────────────────

def parse_timetable_sheet(reader: XlsxFastReader, sheet_name: str, timetable_id: int, conn) -> dict:
    rows = reader.read_sheet(sheet_name)
    
    if not rows:
        return {"skipped": True, "reason": "empty sheet (no shared-string content)"}
    
    max_col = max(c for r_cells in rows.values() for c in r_cells.keys())
    
    # Find "Train ID" / "Train No" header rows in col A (col 1)
    header_rows = []
    for r in sorted(rows.keys()):
        val = rows[r].get(1)
        if is_train_header(val):
            header_rows.append(r)
        if len(header_rows) == 2:
            break
    
    if len(header_rows) < 2:
        # Debug: show what's in col A
        col_a_vals = {r: rows[r].get(1) for r in sorted(rows.keys())[:10]}
        return {"skipped": True, "reason": f"Train ID/No headers not found. Col A sample: {col_a_vals}"}
    
    up_header_row, down_header_row = header_rows[0], header_rows[1]
    
    # Collect station rows
    up_rows   = {}
    down_rows = {}
    for r in sorted(rows.keys()):
        val = rows[r].get(1)
        if not isinstance(val, str) or is_train_header(val) or not val.strip():
            continue
        sc = val.strip()
        if sc not in STATION_SEQ:
            continue
        if up_header_row < r < down_header_row:
            up_rows[sc] = r
        elif r > down_header_row:
            down_rows[sc] = r
    
    # Get train IDs from header rows
    def get_train_ids(hrow):
        tids = {}
        for col in range(2, max_col + 1):
            val = rows.get(hrow, {}).get(col)
            if isinstance(val, str):
                # Strip any literal single quotes
                tn = val.replace("'", "").strip()
                if tn and re.match(r'^\d+$', tn):
                    tids[col] = tn.zfill(2)
        return tids
    
    up_tids   = get_train_ids(up_header_row)
    down_tids = get_train_ids(down_header_row)
    
    trips_ins = 0
    stops_ins = 0
    
    def insert_dir(direction, station_rows, train_ids):
        nonlocal trips_ins, stops_ins
        for col_idx, (col, train_no) in enumerate(sorted(train_ids.items())):
            # Check column has at least one real time
            has_time = False
            for sc, rnum in station_rows.items():
                t = parse_time_str(rows.get(rnum, {}).get(col))
                if t:
                    has_time = True
                    break
            if not has_time:
                continue
            
            cur = conn.execute(
                "INSERT INTO trips (timetable_id, train_no, direction, col_index) VALUES (?,?,?,?)",
                (timetable_id, train_no, direction, col_idx)
            )
            trip_id = cur.lastrowid
            trips_ins += 1
            
            for sc, rnum in station_rows.items():
                t = parse_time_str(rows.get(rnum, {}).get(col))
                if t:
                    conn.execute(
                        "INSERT OR IGNORE INTO stop_times (trip_id, station_id, departure_time) VALUES (?,?,?)",
                        (trip_id, STATION_SEQ[sc], t)
                    )
                    stops_ins += 1
    
    insert_dir("UP",   up_rows,   up_tids)
    insert_dir("DOWN", down_rows, down_tids)
    
    return {
        "skipped": False,
        "trips": trips_ins,
        "stops": stops_ins,
        "up_stations": len(up_rows),
        "down_stations": len(down_rows),
        "up_trains": len(up_tids),
    }

def get_timetable_name_from_file(xlsx_path: str, reader: XlsxFastReader) -> str:
    for s in reader.shared_strings:
        m = re.search(r'Time\s*Table[-\s:]+([A-Za-z0-9_]+)', s, re.IGNORECASE)
        if m:
            return m.group(1).strip()
    base = os.path.splitext(os.path.basename(xlsx_path))[0]
    return base.strip()

def is_single_timetable_workbook(reader: XlsxFastReader) -> Tuple[bool, Optional[str]]:
    # Look for a sheet named 'Timetable' or similar
    for s in reader.sheet_names:
        if s.strip().lower() in ('timetable', 'timetable 1', 'tt'):
            return True, s
    # If there is only 1 non-skip sheet
    candidate_sheets = [s for s in reader.sheet_names if s.strip().upper() not in SKIP_SHEETS]
    if len(candidate_sheets) == 1:
        return True, candidate_sheets[0]
    return False, None

# ─── Main ─────────────────────────────────────────────────────────────────────

def main():
    xlsx_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_XLSX_PATH
    if not os.path.exists(xlsx_path):
        print(f"Error: Excel file not found: {xlsx_path}")
        print(f"Usage: py import_timetable.py [path_to_excel_file.xlsx]")
        sys.exit(1)

    print(f"Opening {xlsx_path} as ZIP (fast XML parser)...")
    reader = XlsxFastReader(xlsx_path)
    print(f"Shared strings loaded: {len(reader.shared_strings)}")
    print(f"Sheets: {len(reader.sheet_names)}")

    is_single, single_sheet = is_single_timetable_workbook(reader)
    
    if is_single and single_sheet:
        print("\n" + "="*60)
        print("SINGLE TIMETABLE IMPORT MODE (Appending/Updating DB)")
        print("="*60)
        
        # Connect to DB without dropping existing data
        conn = sqlite3.connect(DB_PATH)
        conn.execute("PRAGMA foreign_keys = ON")
        
        print("\n[1] Ensuring schema & stations exist...")
        create_schema(conn)
        conn.executemany(
            "INSERT OR IGNORE INTO stations (id, code, name, sequence) VALUES (?,?,?,?)",
            [(seq, code, name, seq) for seq, code, name in STATIONS]
        )
        conn.commit()
        
        tt_name = get_timetable_name_from_file(xlsx_path, reader)
        tt_type = classify_timetable(tt_name)
        m = re.match(r'^(\d+)', tt_name)
        tc = int(m.group(1)) if m else None
        
        print(f"\n[2] Target Timetable: '{tt_name}' (type={tt_type}, train_count={tc})")
        print(f"    Source sheet: '{single_sheet}'")
        
        # Clean up any previous records for this timetable name
        old_ids = [r[0] for r in conn.execute("SELECT id FROM timetables WHERE name=?", (tt_name,)).fetchall()]
        for old_id in old_ids:
            conn.execute("DELETE FROM stop_times WHERE trip_id IN (SELECT id FROM trips WHERE timetable_id=?)", (old_id,))
            conn.execute("DELETE FROM trips WHERE timetable_id=?", (old_id,))
            conn.execute("DELETE FROM timetables WHERE id=?", (old_id,))
        if old_ids:
            print(f"    Removed previous version of '{tt_name}'.")

        cur = conn.execute(
            "INSERT INTO timetables (name, type, train_count, bundled) VALUES (?,?,?,1)",
            (tt_name, tt_type, tc)
        )
        tid = cur.lastrowid
        
        print(f"\n[3] Parsing sheet '{single_sheet}'...")
        stats = parse_timetable_sheet(reader, single_sheet, tid, conn)
        conn.commit()
        
        if stats.get("skipped"):
            print(f"  ERROR: Sheet parsing skipped: {stats.get('reason')}")
            sys.exit(1)
        
        print(f"  OK: trips={stats['trips']} stop_times={stats['stops']}")
        
        # If this is a WEEKDAY timetable, update Mon-Sat defaults to use it
        if tt_type == "WEEKDAY":
            for d in range(0, 6):  # 0=Mon .. 5=Sat
                conn.execute("INSERT OR REPLACE INTO day_defaults (day_of_week, timetable_name) VALUES (?,?)", (d, tt_name))
            conn.commit()
            print(f"\n[4] Updated default weekday timetable (Mon-Sat) -> '{tt_name}'")
        elif tt_type == "SUNDAY":
            conn.execute("INSERT OR REPLACE INTO day_defaults (day_of_week, timetable_name) VALUES (?,?)", (6, tt_name))
            conn.commit()
            print(f"\n[4] Updated default Sunday timetable -> '{tt_name}'")
        
        total_tt = conn.execute("SELECT COUNT(*) FROM timetables").fetchone()[0]
        total_trips = conn.execute("SELECT COUNT(*) FROM trips").fetchone()[0]
        total_stops = conn.execute("SELECT COUNT(*) FROM stop_times").fetchone()[0]
        print(f"\n[5] Database Totals: {total_tt} timetables, {total_trips} trips, {total_stops} stop_times")
        
    else:
        # Full rebuild mode
        print("\n" + "="*60)
        print("FULL REBUILD MODE (Multi-Sheet Master Workbook)")
        print("="*60)
        
        if os.path.exists(DB_PATH):
            os.remove(DB_PATH)
            print(f"Removed existing DB.")
            
        conn = sqlite3.connect(DB_PATH)
        conn.execute("PRAGMA foreign_keys = ON")
        
        print("\n[1] Creating schema...")
        create_schema(conn)
        
        print("\n[2] Inserting stations...")
        conn.executemany(
            "INSERT OR IGNORE INTO stations (id, code, name, sequence) VALUES (?,?,?,?)",
            [(seq, code, name, seq) for seq, code, name in STATIONS]
        )
        conn.commit()
        print(f"  OK: {len(STATIONS)} stations")
        
        print("\n[3] Parsing TIMETABLE NAME sheet...")
        if "TIMETABLE NAME" in reader.sheet_names:
            parse_timetable_name(reader, conn)
        
        print("\n[4] Parsing timetable data sheets...")
        tt_sheets = [s for s in reader.sheet_names if s not in SKIP_SHEETS]
        print(f"  {len(tt_sheets)} sheets to process")
        
        total_trips = 0
        total_stops = 0
        skipped = 0
        
        for i, sname in enumerate(tt_sheets):
            tt_type = classify_timetable(sname)
            m = re.match(r'^(\d+)', sname)
            tc = int(m.group(1)) if m else None
            
            conn.execute(
                "INSERT OR IGNORE INTO timetables (name, type, train_count, bundled) VALUES (?,?,?,1)",
                (sname, tt_type, tc)
            )
            tid = conn.execute("SELECT id FROM timetables WHERE name=?", (sname,)).fetchone()[0]
            
            stats = parse_timetable_sheet(reader, sname, tid, conn)
            conn.commit()
            
            if stats.get("skipped"):
                skipped += 1
                reason = stats['reason'][:80]
                print(f"  [{i+1:2d}/{len(tt_sheets)}] SKIP  {sname:40s} -- {reason}")
            else:
                total_trips += stats["trips"]
                total_stops += stats["stops"]
                flag = "OK  " if stats["trips"] > 0 else "ZERO"
                print(f"  [{i+1:2d}/{len(tt_sheets)}] {flag}  {sname:40s} | trips={stats['trips']:4d} stops={stats['stops']:6d}")
        
        print("\n" + "="*60)
        print("IMPORT COMPLETE")
        print("="*60)
        print(f"  Sheets processed : {len(tt_sheets) - skipped}")
        print(f"  Sheets skipped   : {skipped}")
        print(f"  Total trips      : {total_trips}")
        print(f"  Total stop_times : {total_stops}")
        
        print("\n[5] Verification:")
        for q, label in [
            ("SELECT COUNT(*) FROM stations", "Stations"),
            ("SELECT COUNT(*) FROM timetables", "Timetables"),
            ("SELECT COUNT(*) FROM trips", "Trips"),
            ("SELECT COUNT(*) FROM stop_times", "Stop times"),
            ("SELECT COUNT(*) FROM schedule_overrides", "Special date overrides"),
            ("SELECT COUNT(*) FROM day_defaults", "Day defaults"),
        ]:
            print(f"  {label:28s}: {conn.execute(q).fetchone()[0]}")

    conn.execute("INSERT OR REPLACE INTO sync_metadata VALUES ('db_version','1')")
    conn.execute("INSERT OR REPLACE INTO sync_metadata VALUES ('built_at',?)", (datetime.now().isoformat(),))
    conn.commit()

    db_size = os.path.getsize(DB_PATH)
    print(f"\n  DB size : {db_size/1024:.1f} KB ({db_size/1024/1024:.2f} MB)")

    conn.close()
    reader.close()

    # Version timestamp for signature matching
    version_str = datetime.now().strftime("%Y%m%d_%H%M%S")
    
    # Automatically copy to Android app assets
    if os.path.exists(os.path.dirname(APP_ASSETS_DB_PATH)):
        shutil.copy2(DB_PATH, APP_ASSETS_DB_PATH)
        asset_ver_path = os.path.join(APP_ASSETS_DIR, "db_version.txt")
        with open(asset_ver_path, "w", encoding="utf-8") as f:
            f.write(version_str)
        print(f"\n[6] Copied database and version ({version_str}) to Android assets:")
        print(f"    -> {APP_ASSETS_DB_PATH}")
    else:
        print(f"\n[6] Note: Assets directory not found at {APP_ASSETS_DB_PATH}")

    # Copy and gzip to docs/data for Web & OTA mobile sync
    if os.path.exists(DOCS_DATA_DIR) or os.path.exists(os.path.dirname(DOCS_DATA_DIR)):
        os.makedirs(DOCS_DATA_DIR, exist_ok=True)
        docs_db = os.path.join(DOCS_DATA_DIR, "kmrl_timetable.db")
        shutil.copy2(DB_PATH, docs_db)
        
        docs_db_gz = os.path.join(DOCS_DATA_DIR, "kmrl_timetable.db.gz")
        print("\n[7] Compressing database with gzip for OTA cloud sync...")
        with open(DB_PATH, 'rb') as f_in, gzip.open(docs_db_gz, 'wb', compresslevel=9) as f_out:
            shutil.copyfileobj(f_in, f_out)
        gz_size = os.path.getsize(docs_db_gz)
        print(f"    -> {docs_db_gz} ({gz_size/1024/1024:.2f} MB)")
        
        docs_ver = os.path.join(DOCS_DATA_DIR, "db_version.txt")
        with open(docs_ver, "w", encoding="utf-8") as f:
            f.write(version_str)
        print(f"    -> {docs_ver} (Version: {version_str})")

    print("\nDone OK")

if __name__ == "__main__":
    main()
