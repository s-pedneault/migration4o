---
name: schema-class-navigation
description: Navigate the schema class tree — get parent class, ancestors, subclasses, all descendants, or referenced classes. Use this skill when traversing class hierarchy or building class relationship graphs.
---

# DOSchemaClass — Schema Tree Navigation

Tree navigation is **not on `DOSchemaClass` directly** — use `SchemaUtil` static methods and manual iteration over `DOSchema.getClasses()`.

## Method map

| What you want | How to get it |
|---|---|
| Parent class | `SchemaUtil.findClassByName(schemaClass.parentClassName, schema)` |
| Ordered ancestor chain | Walk `parentClassName` links manually (see below) |
| Direct subclasses | Filter `schema.getClasses()` where `c.parentClassName.equals(schemaClass.source)` |
| All descendants (any depth) | BFS over subclasses (see below) |
| Has any subclasses? | `SchemaUtil.hasSubclasses(schema, schemaClass)` |
| IDEntite target class | `SchemaUtil.findClassByName(schemaClass.pointsTo, schema)` |

## Ancestor chain

```java
List<DOSchemaClass> ancestors = new ArrayList<>();
String current = schemaClass.parentClassName;
while (current != null && !current.isEmpty()) {
    DOSchemaClass ancestor = SchemaUtil.findClassByName(current, schema);
    if (ancestor == null) break;
    ancestors.add(ancestor);
    current = ancestor.parentClassName;
}
```

## Direct subclasses

```java
List<DOSchemaClass> subclasses = new ArrayList<>();
for (DOSchemaClass c : schema.getClasses()) {
    if (schemaClass.source.equals(c.parentClassName)) {
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
            if (node.source.equals(c.parentClassName)) queue.add(c);
        }
    }
}
```

## Notes
- `SchemaUtil.findClassByName` does exact match first, then falls back to simple name match.
- `DOSchema.findClassByName(name)` is a convenience wrapper for the same.
- `SchemaUtil.hasSubclasses` only counts `Entite`-type subclasses, not all subclasses.

## Key files
- `src/main/java/migration4o/models/schema/DOSchema.java`
- `src/main/java/migration4o/util/SchemaUtil.java`
- `src/main/java/migration4o/schema/DOSchemaService.java`
