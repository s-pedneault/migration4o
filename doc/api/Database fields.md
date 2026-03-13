# M4oDatabaseField Actions — API Inventory

An `M4oDatabaseField` is a runtime entity backed by a `StoredField` (DB4O descriptor) with a required `M4oSchemaField` attached (export configuration). Actions that belong purely on `M4oSchemaField` — configuration getters, skip/format/map logic, child/embed settings — are intentionally excluded here; they live on the schema field itself.

---

## 1. Identity & Metadata

These come from the DB4O `StoredField` descriptor, not from the schema configuration.

| Action | Description |
|---|---|
| **getSourceName** | Return the raw field name as stored in the database (`StoredField.getName()`). |
| **getTypeName** | Return the declared Java type name of the field (e.g., `long`, `java.util.Vector`, `gest.gen.IDVehicule`). |
| **isArray** | Return whether the field is stored as a raw Java array (`StoredField.isArray()`). Distinct from collection: primitive arrays such as `byte[]` and `int[]` are arrays, not collections. |

---

## 2. Raw Value Reading

| Action | Description |
|---|---|
| **readValue** | Read the field's raw value from a specific object instance. Returns `null`, a primitive wrapper, an activated object, a `Collection`, or an array. Inherited fields are accessed by retrieving the appropriate `M4oDatabaseField` from `M4oDatabaseClass.getAllFields()`. |

> | Private | Description |
> |---|---|
> | **readDB4OValue** | Directly call `StoredField.get(obj)` on the backing `StoredField`. Returns the raw unprocessed DB4O result before any type normalization. Used internally by `readValue`. |

---

## 3. Value Classification

Once a raw value is in hand, these actions classify what it contains so downstream logic knows how to handle it.

| Action | Description |
|---|---|
| **isPrimitiveValue** | Determine whether the value is a leaf (String, Long, Integer, Boolean, Double, etc.) that requires no further traversal. |
| **isObjectReference** | Determine whether the value is a persistent DB4O object that requires further traversal or ID export. |
| **isCollectionValue** | Determine whether the value is a collection — either schema-flagged, a direct `Collection` instance, or an object whose type inherits from a known collection base class. |
| **isArrayValue** | Determine whether the value is a plain Java array. Covers `byte[]`, `int[]`, `Object[]`, etc. |
| **isBinaryValue** | Determine whether the value is `byte[]` — binary data to be exported as Base64, not traversed. |
| **isEmpty** | Determine whether the value is considered empty: `null`, empty string, whitespace-only string, empty collection, or `−1` for an IDEntite-type field. |

> | Private | Description |
> |---|---|
> | **getDB4OObjectId** | Return the DB4O internal `long` ID for a value object (`container.ext().getID(value)`). Returns zero or negative for non-persistent values. Used internally by `isPrimitiveValue` and `isObjectReference`. |
> | **resolveDB4OFieldClass** | Look up the `StoredClass` for this field's declared type. Used internally by `isCollectionByAncestry`. |
> | **isCollectionByAncestry** | Check whether the field's type inherits from a known collection base class by walking the class hierarchy. Catches custom collection subclasses whose names do not match keyword lists. Used internally by `isCollectionValue`. |
> | **isPrimitiveType** | Check whether the type name is a known Java primitive or wrapper. Used internally by `isPrimitiveValue`. |
> | **isIDEntiteType** | Check whether the field's resolved schema class is an IDEntite class. Used internally by `isEmpty` and `isIDEntiteField`. |

---

## 4. Collection Field Operations

Applies when `isCollectionValue` is true.

| Action | Description |
|---|---|
| **getCollectionItems** | Extract the list of individual items from the collection. Handles three variants: a real `java.util.Collection`, a `GenericObject`-based DB4O internal collection (requires translator field extraction), and ancestry-based custom collection classes (e.g., `VectChampPerso → HVector → Vector`). |

> | Private | Description |
> |---|---|
> | **activateDB4OCollection** | Activate the collection object to depth 1 (`container.activate(value, 1)`) so its contents are populated from the database. Used internally as the first step of `getCollectionItems`. |

---

## 5. IDEntite Reference Field Operations

Applies when the field's type is an IDEntite class (a reference-holder pattern in this database).

| Action | Description |
|---|---|
| **isIDEntiteField** | Determine whether this field's declared type resolves to an IDEntite class in the schema. |
| **resolveTargetObjectId** | Return the object ID to export for this IDEntite reference: either the IDEntite wrapper's own ID (scalar reference mode) or the resolved target entity's ID (embedded mode), guided by the schema field's `embedContents` flag. |

> | Private | Description |
> |---|---|
> | **extractMID** | Read the `mID` integer stored inside the IDEntite object — the logical foreign key identifying the target entity. Used internally by `resolveTargetObjectId`. |
> | **getDB4OIDEntiteObjectId** | Get the DB4O internal object ID of the IDEntite wrapper object itself. Used internally by `resolveTargetObjectId`. |
> | **resolveReferenceByMID** | Scan `EntiteContientID` descendant classes, activate each, compare `mID`, and return the matching object ID. Used internally by `resolveTargetObjectId`. |
> | **inferTargetTypeName** | Derive the expected target entity type from the field's destination name (e.g., `mIDTypeVehicule` → `TypeVehicule`) to narrow the scan in `resolveReferenceByMID`. |

---

## 6. Object Reference Field Operations

Applies when `isObjectReference` is true and the field is not an IDEntite type.

| Action | Description |
|---|---|
| **getObjectId** | Return the DB4O internal `long` ID of the referenced object. Needed to export a reference by ID or to record reachability. |

> | Private | Description |
> |---|---|
> | **activateDB4OObject** | Activate the referenced object to depth 1 so its fields are readable. Used internally before field traversal. |
> | **resolveDB4OClass** | Look up the `StoredClass` descriptor for the referenced object's runtime type. Used internally to enumerate its fields for traversal. |

---

## 7. Virtual Field Operations

Applies when the schema field is virtual (`M4oSchemaField.isVirtualField()`, source starts with `@`).

| Action | Description |
|---|---|
| **evaluateCriterias** | Evaluate the schema field's criteria list against a candidate object to determine whether it matches, computing the virtual field's value at runtime. |

---

## 8. Reachability Tracking

| Action | Description |
|---|---|
| **markReached** | Record the object ID of a value as "reached" from its parent, contributing to export coverage statistics. |

> | Private | Description |
> |---|---|
> | **markCollectionWrapperReached** | Mark the collection wrapper object itself as reached before iterating its items. Used internally by `getCollectionItems`. |
