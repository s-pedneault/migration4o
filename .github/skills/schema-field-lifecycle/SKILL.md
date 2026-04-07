---
name: schema-field-lifecycle
description: Deep-copy a DOSchemaField using field.copy(), for example when instantiating a shared field definition into a specific class context. Use this skill when duplicating a field and then customizing it for a concrete class.
---

# DOSchemaField — Lifecycle

The only lifecycle operation is `field.copy()` — a deep-copy factory on `DOSchemaField` itself.

## Method map

| What you want | How to get it |
|---|---|
| Deep copy of a field | `field.copy()` |

## What `copy()` does and does not duplicate

```
DEEP-COPIED (via copy.attributes.*):
  criterias          → new ArrayList with new DOFieldCriteria instances
  criteriasOperator
  valueMap           → valueMap.copy() (new LinkedHashMap)

SHALLOW-COPIED (strings/primitives are immutable — safe):
  destinationName, type, format, isExported, skipWhen,
  skipUserOption, isCollection, embedContents, childrenType,
  title, description, pointsTo, definitionId, group

NOT COPIED (set externally after copy):
  attributes.source  ← caller sets this explicitly
  parentClass        ← set by DOSchemaClass.setFields()
  childrenSchemaClass ← re-resolved at schema-load time
```

## Typical usage: instantiate a shared definition into a class

```java
DOSchemaField definition = field.schema.sharedFields.get(sharedFieldRef.attributes.definitionId);
if (definition != null) {
    DOSchemaField instance = definition.copy();
    instance.attributes.source = sharedFieldRef.attributes.source;  // carry the original DB field name
    instance.attributes.definitionId = null;                        // standalone — no longer shared
    // parentClass will be set by setFields() below
}

// Rebuild class fields incorporating the instantiated copy
schemaClass.setFields(new DOSchemaField[]{ ..., instance, ... });
```

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaField.java`
- `src/main/java/migration4o/models/schema/DOSchemaFieldAttributes.java`
- `src/main/java/migration4o/models/schema/DOSchemaValueMap.java`
- `src/main/java/migration4o/models/schema/DOFieldCriteria.java`
