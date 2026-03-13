# M4oDatabaseClass Actions — API Inventory

An `M4oDatabaseClass` is a runtime entity backed by a `StoredClass` (DB4O descriptor) with a required `M4oSchemaClass` attached (export configuration). This document lists every action the new API must support. Actions whose answers come entirely from the schema class — name, destination name, type classification, behavioral flags, field configuration — are intentionally excluded here; they live on `M4oSchemaClass`.

---

## 1. Raw Object Counts

| Action | Description |
|---|---|
| **getInstanceCount** | Return the total number of instances registered in DB4O for this class (`StoredClass.instanceCount()`). Includes objects registered at parent class levels due to inheritance. Fast and does not require loading objects. |
| **isValid** | Return whether this is a real application class worth processing: the class name does not start with `com.db4o.` and the instance count is greater than zero. |

---

## 2. Object ID Access

| Action | Description |
|---|---|
| **getAllObjectIds** | Return all object IDs registered by DB4O for this class (`StoredClass.getIDs()`). Includes duplicates that arise from inheritance registration — the same physical object appears in this class and all its ancestor classes. |
| **getUniqueObjectIds** | Return deduplicated object IDs, keeping only IDs that belong to this class as the most-derived (leaf) class. This is the authoritative set of instances to iterate for export. |

---

## 3. Object Loading & Activation

| Action | Description |
|---|---|
| **loadAndActivateObject** | Load an object by ID and activate it to depth 1 in one step. Returns an activated object ready for field reading. The standard entry point for object retrieval during export. |
| **loadAndActivateObjectsBatch** | Load and activate a list of object IDs in one pass, returning a map of `objectId → activated object`. Skips objects that fail to load or activate. |

> | Private | Description |
> |---|---|
> | **loadDB4OObject** | Retrieve a single raw hollow `GenericObject` by its ID (`container.ext().getByID(objectId)`). Returns an unactivated DB4O object; fields are not yet readable. Used internally as the first step of `loadAndActivateObject` and `loadAndActivateObjectsBatch`. |
> | **activateDB4OObject** | Activate a hollow `GenericObject` to depth 1 (`container.activate(obj, 1)`). Shallow activation is preferred to avoid cascading loads. Used internally by `loadAndActivateObject` and `loadAndActivateObjectsBatch`. |

---

## 4. Database Field Access

An `M4oDatabaseClass` exposes `M4oDatabaseField` instances to consumers. The underlying `StoredField` machinery is kept private.

| Action | Description |
|---|---|
| **getDeclaredFields** | Return only the `M4oDatabaseField` instances declared at this exact class level. Does not include fields inherited from parent classes. |
| **getAllFields** | Return all `M4oDatabaseField` instances visible on this class: declared fields first, then inherited fields in ancestor order. This is the complete field set used for object traversal and export. |

> | Private | Description |
> |---|---|
> | **getDeclaredDB4OFields** | Return only the raw `StoredField` descriptors declared at this class level (`StoredClass.getStoredFields()`). Used internally to construct `M4oDatabaseField` instances. |
> | **getAllDB4OFields** | Return all raw `StoredField` descriptors visible on this class, walking up the `StoredClass` parent chain (`DatabaseUtil.getAllFieldsIncludingAncestors()`). Used internally to construct the full `M4oDatabaseField` list. |
> | **findDB4OField** | Locate a raw `StoredField` by name, searching this class and its ancestors. Returns `null` if not found. Used internally by field construction and value reading. |

---

## 5. Class Hierarchy Navigation

| Action | Description |
|---|---|
| **getParentClass** | Return the parent `M4oDatabaseClass` as registered in the database. May differ from the `M4oSchemaClass` parent chain if the persisted hierarchy diverges from the reference schema. |

> | Private | Description |
> |---|---|
> | **getParentDB4OClass** | Return the raw parent `StoredClass` (`StoredClass.getParentStoredClass()`). Used internally to resolve `getParentClass`. |
