"""
Compare municipality XML (RM54060) with provincial GeoPackage (Role.gpkg)
for municipality code 54060.
Uses SQLite directly for fast GeoPackage queries (no row-by-row fiona scan).
"""
import sqlite3
import xml.etree.ElementTree as ET

XML_FILE = 'local/roles/54060-063/RM54060_2026_2026_04_22_UNI.xml'
GPKG_FILE = 'local/roles/Role.gpkg'
MUN_CODE  = '54060'

# ── 1. Parse XML ─────────────────────────────────────────────────────────────
tree = ET.parse(XML_FILE)
root = tree.getroot()
xml_entries = root.findall('.//RLUEx')
print(f"XML total RLUEx entries: {len(xml_entries)}")

# Full formatted matricule is in RLZU1001A  (e.g. "5248-52-2780")
# mat18 equivalent: concatenate RL0104A + RL0104B + RL0104C zero-padded to 18 chars
xml_mats = {}
for e in xml_entries:
    fmt = e.findtext('.//RLZU1001A', '').strip()   # "5248-52-2780"
    a = e.findtext('.//RL0104A', '').strip()
    b = e.findtext('.//RL0104B', '').strip()
    c = e.findtext('.//RL0104C', '').strip()
    if a and b and c:
        # mat18 = right-pad each part to total 18 digits
        mat18 = f"{a}{b}{c}".ljust(18, '0')
        xml_mats[mat18] = {'formatted': fmt, 'entry': e}

print(f"XML unique mat18 keys:   {len(xml_mats)}")
print(f"XML sample mat18:        {sorted(xml_mats.keys())[:5]}")
print(f"XML sample formatted:    {[v['formatted'] for k,v in sorted(xml_mats.items())[:5]]}")

# ── 2. Query GeoPackage via SQLite ───────────────────────────────────────────
print(f"\n--- GeoPackage query for code_mun={MUN_CODE} ---")
db = sqlite3.connect(GPKG_FILE)
db.row_factory = sqlite3.Row

# List tables
tables = [r[0] for r in db.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")]
print(f"Tables: {tables}")

# Count and sample from each relevant table
for tbl in ['b05v_unite_evaln_2026', 'b05v_adr_unite_evaln_2026', 'b05v_lot_cadst_2026', 'b05v_repar_fisc_2026']:
    if tbl not in tables:
        continue
    row = db.execute(f"SELECT COUNT(*) FROM {tbl} WHERE code_mun=?", (MUN_CODE,)).fetchone()
    count = row[0]
    print(f"\n{tbl}: {count} rows for {MUN_CODE}")
    if count > 0:
        samples = db.execute(f"SELECT * FROM {tbl} WHERE code_mun=? LIMIT 2", (MUN_CODE,)).fetchall()
        for s in samples:
            print(f"  {dict(s)}")

# ── 3. Key match analysis ─────────────────────────────────────────────────────
print(f"\n--- mat18 overlap ---")
gpkg_mats = set(
    r[0] for r in db.execute(
        "SELECT mat18 FROM b05v_unite_evaln_2026 WHERE code_mun=? AND mat18 IS NOT NULL",
        (MUN_CODE,)
    )
)
print(f"GPKG unique mat18 for {MUN_CODE}: {len(gpkg_mats)}")
print(f"GPKG sample mat18: {sorted(gpkg_mats)[:5]}")

xml_key_set  = set(xml_mats.keys())
direct_match = xml_key_set & gpkg_mats
xml_only     = xml_key_set - gpkg_mats
gpkg_only    = gpkg_mats - xml_key_set

print(f"\nDirect mat18 matches:  {len(direct_match)}")
print(f"Only in XML:           {len(xml_only)}")
print(f"Only in GPKG:          {len(gpkg_only)}")
if xml_only:
    print(f"  XML-only sample:  {sorted(xml_only)[:5]}")
if gpkg_only:
    print(f"  GPKG-only sample: {sorted(gpkg_only)[:5]}")

# ── 4. Field-level comparison on a matched entry ─────────────────────────────
if direct_match:
    mat = sorted(direct_match)[0]
    xml_info = xml_mats[mat]
    e = xml_info['entry']

    # Flatten XML fields
    xml_fields = {}
    def collect_flat(el):
        if len(el) == 0 and el.text and el.text.strip():
            xml_fields[el.tag.upper()] = el.text.strip()
        for child in el:
            collect_flat(child)
    collect_flat(e)

    print(f"\n--- Field comparison for mat18={mat} (formatted: {xml_info['formatted']}) ---")
    print(f"\nXML fields ({len(xml_fields)}):")
    for k, v in sorted(xml_fields.items()):
        print(f"  {k}: {v!r}")

    print(f"\nGPKG b05v_unite_evaln_2026:")
    gpkg_row = dict(db.execute("SELECT * FROM b05v_unite_evaln_2026 WHERE mat18=? AND code_mun=?",
                                (mat, MUN_CODE)).fetchone())
    for k, v in sorted(gpkg_row.items()):
        if v is not None:
            print(f"  {k}: {v!r}")

    print(f"\nGPKG b05v_adr_unite_evaln_2026:")
    addr_rows = db.execute("SELECT * FROM b05v_adr_unite_evaln_2026 WHERE mat18=? AND code_mun=?",
                            (mat, MUN_CODE)).fetchall()
    for row in addr_rows:
        for k, v in sorted(dict(row).items()):
            if v is not None:
                print(f"  {k}: {v!r}")

db.close()
