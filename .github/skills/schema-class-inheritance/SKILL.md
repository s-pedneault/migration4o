---
name: schema-class-inheritance
description: Classify a DOSchemaClass by its inheritance — check if it's an IDEntite reference-holder, an Entite entity, a Param lookup table, or a descendant of any given ancestor class. Use this skill when type classification drives export routing.
---

# DOSchemaClass — Inheritance & Type Classification

All four classification methods live directly on `DOSchemaClass` and walk the ancestry chain via `attributes.parentClassName`. No `DOSchema` parameter needed — the class holds a back-reference to its schema.

## Method map

| What you want | How to get it |
|---|---|
| Is descendant of any ancestor? | `schemaClass.isDescendantOf(ancestorFQN)` |
| Is an IDEntite reference-holder? | `schemaClass.isIDEntite()` |
| Is a full entity (has own mID)? | `schemaClass.isEntite()` |
| Is a parameter/lookup table? | `schemaClass.isParam()` |

## Examples

```java
// Route: entity vs reference-holder
if (schemaClass.isEntite()) {
    // can be a target of IDEntite references — has its own mID
}

if (schemaClass.isIDEntite()) {
    // carries a foreign-key mID — schemaClass.attributes.pointsTo names the target entity
}

if (schemaClass.isParam()) {
    // lookup/code table
}

// Custom ancestry
if (schemaClass.isDescendantOf("gest.intervention.Intervention")) { ... }
```

## Notes
- `isDescendantOf` also returns `true` when the class **is** the named ancestor itself.
- Ancestry constants are in `DOSchemaConstants`: `ANCESTOR_IDENTITE`, `ANCESTOR_ENTITE_CONTIENT_ID`, `ANCESTOR_ENTITE_PARAM`, `ANCESTOR_ENTITE`.
- Classification requires the reference schema to be loaded (`ApplicationService.initialize()` must have run).

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaClass.java`
- `src/main/java/migration4o/models/schema/DOSchemaConstants.java`
- `src/main/java/migration4o/schema/DOSchemaService.java`
