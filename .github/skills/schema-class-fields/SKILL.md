---
name: schema-class-fields
description: Access, iterate, set, or find fields on a DOSchemaClass. Use this skill when reading or modifying the field list of a schema class, including establishing parentClass back-links.
---

# DOSchemaClass — Fields Management

## Important: no declared/inherited split in current code

The current model stores **all fields** in a single flat array `schemaClass.fields`. There is no separate inherited vs declared split — the schema loader flattens the full field list into this one array at load time.

## Field / method map

| What you want | How to get it |
|---|---|
| All fields on this class | `schemaClass.fields` (array, may be null) |
| Set fields + establish parentClass back-links | `schemaClass.setFields(DOSchemaField[])` |
| Find a field by destinationName | `schemaClass.findField(destinationName)` → null if absent |

## Setting fields — always use `setFields`, not direct assignment

```java
// CORRECT — establishes field.parentClass = this for every entry
schemaClass.setFields(new DOSchemaField[]{ field1, field2 });

// WRONG — leaves field.parentClass null
schemaClass.fields = new DOSchemaField[]{ field1, field2 };
```

## Iterating all fields

```java
if (schemaClass.fields != null) {
    for (DOSchemaField field : schemaClass.fields) {
        if (!field.isExported) continue;
        // process field
    }
}
```

## Finding a specific field

```java
DOSchemaField f = schemaClass.findField("destinationFieldName"); // null if not found
```

## Manually collecting parent-chain fields (when inheritance matters)

```java
List<DOSchemaField> inheritedFields = new ArrayList<>();
String parentName = schemaClass.parentClassName;
while (parentName != null && !parentName.isEmpty()) {
    DOSchemaClass parent = SchemaUtil.findClassByName(parentName, schema);
    if (parent == null) break;
    if (parent.fields != null) Collections.addAll(inheritedFields, parent.fields);
    parentName = parent.parentClassName;
}
```

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaClass.java`
- `src/main/java/migration4o/models/schema/DOSchemaField.java`
- `src/main/java/migration4o/util/SchemaUtil.java`
