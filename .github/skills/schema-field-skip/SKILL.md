---
name: schema-field-skip
description: Evaluate whether a DOSchemaField should be omitted from XML output based on its skipWhen conditions or user-selected runtime skip options. Use this skill when deciding whether to write a field value during export.
---

# DOSchemaField — Skip Evaluation

Skip logic lives entirely in `ValueUtil` static methods — it is **not on `DOSchemaField`** itself.

## Method map

| What you want | How to get it |
|---|---|
| Should skip based on `skipWhen`? | `ValueUtil.shouldSkipField(value, field, schema)` |
| Should skip (skipWhen + user options)? | `ValueUtil.shouldSkipField(value, field, schema, userSelectedSkipOptions)` |
| Should skip by user option only? | `ValueUtil.shouldSkipField(value, field, schema, opts, true, false)` |
| Does value match one condition keyword? | `ValueUtil.matchesSkipCondition(value, "KEYWORD", field, schema)` |

## Skip condition keywords (stored in `field.skipWhen`, comma-separated)

| Keyword | Skips when |
|---|---|
| `NULL` | value is null |
| `ZERO` | numeric == 0 |
| `MINUS_ONE` | numeric == -1 |
| `EMPTY_STRING` | null or blank string |
| `EMPTY_COLLECTION` | null, empty Collection, or zero-length array |
| `FALSE` | boolean false |
| `DEFAULT` | legacy `isEmpty()` — covers null, blank, empty collection, -1 for IDEntite |

## Examples

```java
// Typical guard in export engine — apply before writing field
if (ValueUtil.shouldSkipField(rawValue, field, schema)) {
    continue;
}

// With runtime user skip options
List<DOSchemaField> userSkips = exportSession.getUserSelectedSkipOptions();
if (ValueUtil.shouldSkipField(rawValue, field, schema, userSkips)) {
    continue;
}

// Manual condition check
if (ValueUtil.matchesSkipCondition(rawValue, "NULL,ZERO", field, schema)) {
    continue;
}
```

## Notes
- `field.skipWhen` is a plain `String` (e.g. `"NULL,ZERO"`) parsed inside `ValueUtil`.
- `field.skipUserOption` is the UI label of the option — the runtime UI uses it to identify which fields a user has chosen to skip; it does not affect `shouldSkipField` directly.
- Apply skip evaluation **before** formatting and value mapping.

## Key files
- `src/main/java/migration4o/util/ValueUtil.java`
- `src/main/java/migration4o/models/schema/DOSchemaField.java`
