---
name: schema-class-navigation
description: Navigate the schema class tree — get parent class, ancestors, subclasses, all descendants, or referenced classes. Use this skill when traversing class hierarchy or building class relationship graphs.
---

# DOSchemaClass — Schema Tree Navigation

Tree navigation uses `schema.findClassByName()` (on `DOSchema`) and `schemaClass.hasSubclasses()` (on `DOSchemaClass`). `SchemaUtil.findClassByName()` no longer exists.

## Method map

| What you want | How to get it |
|---|---|
| Parent class | `schema.findClassByName(schemaClass.attributes.parentClassName)` |
| Ordered ancestor chain | Walk `attributes.parentClassName` links manually (see below) |
| Direct subclasses | Filter `schema.getClasses()` where `c.attributes.parentClassName.equals(schemaClass.attributes.source)` |
| All descendants (any depth) | BFS over subclasses (see below) |
| Has any subclasses? | `schemaClass.hasSubclasses()` |
| IDEntite target class | `schemaClass.getPointsToClass()` or `schema.findClassByName(schemaClass.attributes.pointsTo)` |

## Ancestor chain

```java
List<DOSchemaClass> ancestors = new ArrayList<>();
String current = schemaClass.attributes.parentClassName;
while (current != null && !current.isEmpty()) {
    DOSchemaClass ancestor = schema.findClassByName(current);
    if (ancestor == null) break;
    ancestors.add(ancestor);
    current = ancestor.attributes.parentClassName;
}
```

## Direct subclasses

```java
List<DOSchemaClass> subclasses = new ArrayList<>();
for (DOSchemaClass c : schema.getClasses()) {
    if (schemaClass.attributes.source.equals(c.attributes.parentClassName)) {
        subclasses.add(c);
    }
}
```

## All descendants (BFS)

```java
Set<DOSchemaClass> descendants = new HashSet<>();
Queue<DOSchemaClass> queue = new LinkedList<>(subclasses);
while (!queue.isEmpty()) {
    DOSchemaClass node = queue.poll();
    if (descendants.add(node)) {
        for (DOSchemaClass c : schema.getClasses()) {
            if (node.attributes.source.equals(c.attributes.parentClassName)) queue.add(c);
        }
    }
}
```

## Notes
- `schema.findClassByName()` matches on `attributes.source` (fully-qualified name).
- `schemaClass.hasSubclasses()` checks if any Entite-type class names this class as parent — it requires the class's `schema` back-reference to be set.
- `DOSchema` is obtained from `DOSchemaService.getInstance().getReferenceSchema()`.

## Key files
- `src/main/java/migration4o/models/schema/DOSchemaClass.java`
- `src/main/java/migration4o/models/schema/DOSchema.java`
- `src/main/java/migration4o/schema/DOSchemaService.java`
- `SchemaUtil.findClassByName` does exact match first, then falls back to simple name match.
- `DOSchema.findClassByName(name)` is a convenience wrapper for the same.
- `SchemaUtil.hasSubclasses` only counts `Entite`-type subclasses, not all subclasses.

## Key files
- `src/main/java/migration4o/models/schema/DOSchema.java`
- `src/main/java/migration4o/util/SchemaUtil.java`
- `src/main/java/migration4o/schema/DOSchemaService.java`
