#!/usr/bin/env python3
import csv
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCHEMA_PATH = ROOT / "schema" / "reference-schema.xml"
REVIEW_PATH = ROOT / "doc" / "development" / "destination-name-by-source-review.csv"


def load_approved_names():
    approved = {}
    rows = []
    with REVIEW_PATH.open("r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            rows.append(row)
            status = (row.get("status") or "").strip().lower()
            if status not in {"approved", "edited"}:
                continue

            source = (row.get("source_name") or "").strip()
            if not source:
                continue

            approved_name = (row.get("suggested_destination_name") or "").strip()
            if not approved_name:
                continue

            approved[source] = approved_name

    # Fallback mode: if no status is provided anywhere, treat edited suggestions
    # as rows where suggested_destination_name differs from
    # current_destination_name.
    if approved:
        return approved, "status-mode"

    for row in rows:
        source = (row.get("source_name") or "").strip()
        if not source:
            continue

        suggested_name = (row.get("suggested_destination_name") or "").strip()
        current_name = (row.get("current_destination_name") or "").strip()
        if not suggested_name:
            continue

        if suggested_name != current_name:
            approved[source] = suggested_name

    return approved, "direct-edit-mode"


def apply_to_field_nodes(root, approved):
    changed = 0

    for field in root.findall("./fields/field"):
        source = (field.get("source") or "").strip()
        if source in approved and field.get("destinationName") is not None:
            new_name = approved[source]
            if field.get("destinationName") != new_name:
                field.set("destinationName", new_name)
                changed += 1

    for cls in root.findall("./class"):
        for field in cls.findall("./field"):
            source = (field.get("source") or "").strip()
            if source in approved and field.get("destinationName") is not None:
                new_name = approved[source]
                if field.get("destinationName") != new_name:
                    field.set("destinationName", new_name)
                    changed += 1

    return changed


def main():
    approved, mode = load_approved_names()
    if not approved:
        print("No approved/edited rows found. Nothing to apply.")
        return

    tree = ET.parse(SCHEMA_PATH)
    root = tree.getroot()

    changed = apply_to_field_nodes(root, approved)
    tree.write(SCHEMA_PATH, encoding="utf-8", xml_declaration=True)

    print(f"Applied {changed} destinationName updates using {len(approved)} source rows ({mode})")


if __name__ == "__main__":
    main()
