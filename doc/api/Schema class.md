# Schema Class Actions — API Inventory

A `DOSchemaClass` is the export configuration for one class. It describes how a database class should be traversed, filtered, and written. Like `DOSchemaField`, it carries no DB4O runtime state — it holds only configuration that drives how the database class API operates.

---

## 1. Identity & Metadata

| Action | Description |
|---|---|
| **getSourceName** | Return the simple (unqualified) class name, e.g. `Vehicule` (`ClassUtil.getSimpleName(source)`). |
| **getSourcePackage** | Return the package portion of the fully qualified class name, e.g. `gest.trans` (`ClassUtil.getPackageName(source)`). |
| **getFullSourceName** | Return the fully qualified class name as stored in the database (`source`), e.g. `gest.trans.Vehicule`. |
| **getDestinationName** | Return the mapped export element name (`destinationName`). Used as the XML tag name for objects of this class. |
| **getParentClassName** | Return the fully qualified name of the declared parent class (`parentClassName`). Null or empty for root classes. |
| **getTitle** | Return the human-readable label for this class, used in the UI and documentation. |
| **getDescription** | Return the extended description of the class's semantic purpose. |
| **getSummary** | Return the brief summary line shown in overviews and generated HTML viewers. |
| **getSchemaNotes** | Return internal notes for schema maintainers, not exported to XML output. |
| **getPointsTo** | Return the target class name for IDEntite classes (`pointsTo`). Identifies the entity type this ID-holder references. |

---

## 2. Behavioral Flags

| Action | Description |
|---|---|
| **isMigrated** | Return whether objects of this class are included in the export (`migrate`). Classes with `migrate = false` are skipped entirely. |
| **isPrimitive** | Return whether the class's source name is a known Java primitive or standard wrapper type (`TypeUtil.isPrimitiveType(source)`). Primitive classes are not traversed as persistent objects. |

---

## 3. Inheritance & Type Classification

These require a `DOSchema` to walk the parent chain.

| Action | Description |
|---|---|
| **isDescendantOf** | Determine whether this class inherits from a given ancestor class, walking `parentClassName` links up the schema hierarchy (`SchemaUtil.isDescendantOf()`). |
| **isIDEntite** | Shortcut: check whether this class descends from `gest.gen.IDEntite`. Identifies reference-holder classes that carry an `mID` foreign key rather than entity data. |
| **isEntite** | Shortcut: check whether this class descends from `gest.gen.EntiteContientID`. Identifies full entity classes that have their own `mID` and can be targets of IDEntite references. |
| **isParam** | Shortcut: check whether this class descends from `gest.gen.EntiteParam`. Identifies parameter/lookup-table classes. |

---

## 4. Fields Management

### 4a. Declared Fields

Fields defined directly on this class, not counting anything inherited from parent classes.

| Action | Description |
|---|---|
| **getDeclaredFields** | Return only the `DOSchemaField` instances explicitly declared on this class (`fields` array). Does not include fields from any ancestor class. |
| **setDeclaredFields** | Set the declared fields array and establish parent back-links — each field's `parentClass` is set to this class. Used during schema loading and class construction. |
| **findDeclaredField** | Find a declared field on this class by its `destinationName`. Returns `null` if the field is not declared directly here (even if it exists on a parent). |

### 4b. Inherited Fields

Fields contributed by ancestor classes, walking the `parentClassName` chain through the schema.

| Action | Description |
|---|---|
| **getInheritedFields** | Return all `DOSchemaField` instances declared on ancestor classes, in order from immediate parent up to the root. Requires a `DOSchema` to resolve parent class names to class objects. |
| **getAllFields** | Return the full flat list of fields visible on this class: declared fields first, then inherited fields in ancestor order. This is the complete field set used for actual object traversal and export. |
| **findField** | Find a field by its `destinationName` across both declared and inherited fields. Returns the first match, giving declared fields priority over inherited ones. |

---

## 5. Schema Tree Navigation

All navigation requires a `DOSchema` to resolve class names to class objects.

| Action | Description |
|---|---|
| **getParentClass** | Resolve `parentClassName` against the schema and return the parent `DOSchemaClass`. Returns `null` for root classes or when the parent is not present in the schema. |
| **getAncestors** | Return the ordered list of ancestor classes from immediate parent up to the root, by walking `parentClassName` links. Used to collect inherited fields and to evaluate `isDescendantOf`. |
| **getSubclasses** | Return all classes in the schema that declare this class as their `parentClassName` (direct children only). |
| **getAllDescendants** | Return the complete set of descendant classes at any depth — direct children, grandchildren, and so on. Useful for finding all concrete types that share a base class. |
| **hasSubclasses** | Return whether at least one other class in the schema declares this class as its parent (`SchemaUtil.hasSubclasses()`). |
| **getReferencedClasses** | Return the set of distinct schema classes referenced by this class's fields: the resolved type class of each non-primitive field, and the resolved `childrenSchemaClass` of each collection field. These are the classes this class points to in the object graph. |
| **getPointsToClass** | For IDEntite classes, resolve `pointsTo` against the schema and return the target `DOSchemaClass`. Returns `null` when `pointsTo` is not set or the class is not found. |

---

## 6. Schema References

Schema references are links declared in the reference schema that point back to this class from other classes, used for reverse-navigation and coverage reporting.

| Action | Description |
|---|---|
| **getSchemaReferences** | Return the array of `DOSchemaReference` entries associated with this class. Each reference pairs a class name and a field name that targets this class. |

---

## 7. Export Metadata

| Action | Description |
|---|---|
| **getMetadata** | Produce a `StructuredWriterMetadata` instance for this class within a given module. Carries generator, provider, module name, type name, and total object count — written into the XML file header. |
