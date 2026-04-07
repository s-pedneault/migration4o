---
name: schema-field-format
description: Apply the format transformation pipeline defined on a DOSchemaField to a string value (TRIM, UPPERCASE, LOWERCASE). Use this skill when transforming a raw database value before writing it to XML output.
---

# DOSchemaField — Value Formatting

Format transformation lives in `ValueUtil.formatFieldValue()` — it is **not on `DOSchemaField`** itself.

## Method map

| What you want | How to get it |
|---|---|
| Format a String value | `ValueUtil.formatFieldValue(stringValue, field)` |
| Format any Object value | `ValueUtil.formatFieldValue(anyObject, field)` — converts with `String.valueOf` first |

## Format keywords (stored in `field.attributes.format`, comma-separated, applied in order)

| Keyword | Effect |
|---|---|
| `TRIM` | Remove leading/trailing whitespace |
| `UPPERCASE` | Convert to upper case |
| `LOWERCASE` | Convert to lower case |

Unknown keywords are silently ignored.

## Examples

```java
// field.format examples
// "TRIM"           → trim whitespace
// "TRIM,UPPERCASE" → trim then uppercase
// "LOWERCASE"      → lowercase only
// null or ""       → pass-through (no change)

String formatted = ValueUtil.formatFieldValue(rawString, field);
// OR from any object
String formatted = ValueUtil.formatFieldValue(rawObject, field);
```

## Correct ordering in the export pipeline

```java
// 1. Skip evaluation
if (ValueUtil.shouldSkipField(rawValue, field, schema)) continue;

// 2. Format
String stringValue = ValueUtil.formatFieldValue(rawValue, field);

// 3. Value mapping
if (field.valueMap != null && !field.valueMap.isEmpty()) {
    stringValue = field.valueMap.getMappedValue(stringValue);
}

// 4. Write
writer.writeField(field.destinationName, stringValue);
```

## Key files
- `src/main/java/migration4o/util/ValueUtil.java`
- `src/main/java/migration4o/models/schema/DOSchemaField.java`
