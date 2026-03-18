#!/usr/bin/env python3
"""
Replaces DOSchemaField field accesses with attributes-chained accesses.
e.g.  field.source  ->  field.attributes.source
"""

import re
import os

# Known DOSchemaField variable names in the codebase
FIELD_VARS = [
    "cf", "cmpField", "collectionField", "collField", "commonField",
    "copy", "currentField", "def", "effectiveField", "existing",
    "f", "field", "fieldData", "idField", "matchingSchemaField",
    "newField", "newFieldWithData", "oldField", "originalField",
    "refField", "schemaField", "selectedField", "sf", "sfA", "sfB",
    "sharedField", "sourceField",
    # comparator lambda parameters
    "a", "b", "f1", "f2",
]

# Fields that were moved to DOSchemaFieldAttributes
MOVED_FIELDS = [
    "source",
    "destinationName",
    "type",
    "format",
    "isExported",
    "skipWhen",
    "skipUserOption",
    "isCollection",
    "embedContents",
    "childrenType",
    "title",
    "description",
    "pointsTo",
    "valueMap",
    "definitionId",
    "criterias",
    "criteriasOperator",
]

SRC_DIR = os.path.join(os.path.dirname(__file__), "../src")


def build_pattern(var, field):
    # Match var.field (including chained usage like var.field.method())
    # Idempotency is natural: after replacing var.field -> var.attributes.field,
    # the pattern \b(var)\.(field)\b no longer matches.
    return re.compile(r'\b(' + re.escape(var) + r')\.(' + re.escape(field) + r')\b')


def process_file(path):
    with open(path, "r", encoding="utf-8") as f:
        original = f.read()

    content = original

    for var in FIELD_VARS:
        for field in MOVED_FIELDS:
            pat = build_pattern(var, field)
            # Replace varname.field with varname.attributes.field
            # but skip if it already says varname.attributes.field
            def replace(m, var=var, field=field):
                # Check the text just before to see if .attributes. is already there
                return m.group(1) + ".attributes." + m.group(2)

            new_content = pat.sub(replace, content)
            if new_content != content:
                print(f"  [{path.split('/src/')[1]}] {var}.{field} -> {var}.attributes.{field}")
                content = new_content

    if content != original:
        with open(path, "w", encoding="utf-8") as f:
            f.write(content)
        return True
    return False


def main():
    changed = 0
    for root, dirs, files in os.walk(SRC_DIR):
        # Skip the model file itself (already updated manually)
        for fname in files:
            if not fname.endswith(".java"):
                continue
            if fname in ("DOSchemaField.java", "DOSchemaFieldAttributes.java"):
                continue
            path = os.path.join(root, fname)
            if process_file(path):
                changed += 1
    print(f"\nModified {changed} file(s).")


if __name__ == "__main__":
    main()
