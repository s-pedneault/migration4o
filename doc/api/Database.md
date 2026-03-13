# M4oDatabase Actions — API Inventory

`M4oDatabase` is the top-level database object. It manages the connection lifecycle, owns the full registry of `M4oDatabaseClass` instances, and is the entry point for any cross-class operation. It wraps an `ExtObjectContainer` (DB4O) but does not expose it publicly.

---

## 1. Lifecycle

| Action | Description |
|---|---|
| **open** | Open a database file, run the discovery pipeline (read all classes, build `M4oDatabaseClass` instances, deduplicate object IDs, enrich from reference schema), and make the database ready for use. |
| **close** | Close the database connection and release all resources. The `M4oDatabase` instance becomes unusable after this call. |
| **isOpen** | Return whether the database connection is currently open. |

> | Private | Description |
> |---|---|
> | **openDB4OContainer** | Open the raw `ExtObjectContainer` at the given file path. Used as the first step of `open`. |
> | **readDB4OClasses** | Enumerate all `StoredClass` instances from the container (`container.storedClasses()`). Used internally during `open`. |
> | **buildDatabaseClasses** | Convert the enumerated `StoredClass` array into `M4oDatabaseClass` instances, resolving fields and parent links. Used internally during `open`. |
> | **deduplicateObjectIds** | Walk the inheritance hierarchy across all classes and populate `uniqueObjectIds` on each, removing IDs that belong to more-derived subclasses. Used internally during `open`. |
> | **enrichFromReferenceSchema** | Copy `pointsTo` hints and other reference-schema annotations into the database classes for IDEntite resolution. Used internally during `open`. |

---

## 2. Class Registry

| Action | Description |
|---|---|
| **getClasses** | Return all `M4oDatabaseClass` instances registered in this database, in the order they were discovered. |
| **findClass** | Find an `M4oDatabaseClass` by its fully qualified source name (e.g., `gest.trans.Vehicule`). Returns `null` if not found. Falls back to simple name matching if no exact match is found. |
| **getClassCount** | Return the total number of classes registered in this database. |
| **getTotalObjectCount** | Return the sum of `instanceCount` across all valid classes. |

---

## 3. Object Identity & Class Resolution

| Action | Description |
|---|---|
| **resolveClassForObject** | Given a loaded activated object, return the `M4oDatabaseClass` that represents its actual runtime type. Used when traversing a polymorphic reference or collection where the type is not statically known. |

> | Private | Description |
> |---|---|
> | **getObjectId** | Return the DB4O internal `long` ID for a loaded object (`container.ext().getID(obj)`). Returns zero or negative if the object is not a persistent DB4O instance. Used internally by `M4oDatabaseField` for value classification and reachability tracking. |
> | **resolveDB4OClassForObject** | Resolve the raw `StoredClass` for a loaded object (`container.ext().storedClass(obj)`). Used internally by `resolveClassForObject` to then look up the matching `M4oDatabaseClass`. |
