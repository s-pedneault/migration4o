# DB4O Database Data Structure

How objects are stored in a DB4O database file, and every technique we use to access them.

## Opening a Database

- DB4O databases are single binary files (`.dat`, `.nozip`, or renamed archives)
- We load the entire file into memory via `MemoryIoAdapter` for performance → `DODatabaseOpener.openDatabase()`
- Multiple encoding configurations are tried in order until one succeeds → `DODatabaseEncoding.encodings`
  - UTF-8 (default), Latin-1 (legacy), UTF-8 no-intern, Latin-1 no-intern, minimal config
  - Each `DODatabaseEncoding` controls: unicode, internStrings, dotnetSupport
- Configuration flags applied to every attempt → `DODatabaseOpener.createDatabaseConfiguration()`:
  - `activationDepth(0)` — objects load as hollow shells; we activate explicitly later
  - `TransparentActivationSupport` — allows lazy activation
  - `JdkReflector` — standard JDK reflection (most reliable across DB4O formats)
  - `allowVersionUpdates(true)` — opens databases from older DB4O versions
  - `callConstructors(true)` — required for some stored object types
  - `exceptionsOnNotStorable(false)` — prevents crash on unmappable types
  - `DotnetSupport` — enabled for databases created by .NET versions of DB4O
- The container is opened once, stored as singleton → `DODatabaseService.context.container`
- **Never reopen** mid-session; all code shares the same `ExtObjectContainer` instance
- Java 9+ requires `--add-opens` JVM flags for DB4O reflection → `run-ui.sh`

## Stored Classes

- `container.storedClasses()` returns all `StoredClass` entries in the database → `DatabaseUtil.getStoredClassesSafely()`
- Each `StoredClass` provides:
  - `getName()` → fully qualified class name (e.g., `gest.vehicule.Vehicule`)
  - `instanceCount()` → number of stored instances
  - `getIDs()` → `long[]` of all object IDs belonging to that class
  - `getStoredFields()` → `StoredField[]` declared **in that class only** (not inherited)
  - `getParentStoredClass()` → parent in the inheritance chain (or null)
  - `storedField(name, type)` → look up a specific field by name

## Stored Fields

- `StoredField` represents one field declared at one level of the class hierarchy
- Provides:
  - `getName()` → field name as stored (e.g., `mVectCompartiment`)
  - `getStoredType().getName()` → type name (e.g., `java.util.Vector`, `long`, `gest.gen.IDVehicule`)
  - `isArray()` → whether the field is stored as an array
  - `get(object)` → reads the field value from a specific object instance
- **Gotcha**: `getStoredFields()` only returns fields declared at that exact class level
  - Inherited fields require walking up via `getParentStoredClass()` → `DatabaseUtil.getAllFieldsIncludingAncestors()`
- **Gotcha**: duplicate field names can exist (array vs. scalar version); we keep the array version → `DOFieldsConverter`

## Object IDs

- Every persistent object has a unique `long` ID in the database
- `storedClass.getIDs()` → all IDs for instances of that class
- `container.ext().getByID(objectId)` → retrieves the object (hollow until activated)
- `container.ext().getID(object)` → gets the ID of an already-loaded object
- **Gotcha — inheritance duplication**: DB4O registers each object at every level of its inheritance chain
  - Example: if `Vehicule` extends `Entite` extends `Object`, the same object ID appears in `Vehicule.getIDs()`, `Entite.getIDs()`, and `Object.getIDs()`
  - We deduplicate by keeping IDs only in the most-derived (leaf) class → `DOObjectDeduplicator`
  - `DOSchemaClass.objectIds` = raw IDs from DB4O; `DOSchemaClass.uniqueObjectIds` = after deduplication

## Object Activation

- Objects returned by `getByID()` are hollow — fields are null/default until activated
- `container.activate(object, depth)` populates fields to the given depth
  - Depth 1 = the object's own fields only (child references stay hollow)
  - Depth 2 = one level of nested objects also activated
  - `Integer.MAX_VALUE` = full deep activation (used for IDEntite mID extraction)
- We generally use **shallow activation (depth 1)** → `ObjectResolverUtil.activateObjectShallow()`
- Collections (especially `java.util.Vector`) require explicit activation before reading `.size()` or iterating → `FieldExporter` calls `container.activate(fieldValue, 1)` on collection fields
- **Gotcha**: calling `.size()` on a GenericObject proxy before activation may return incorrect values

## GenericObject

- When DB4O cannot instantiate the original Java class (because the class is not on the classpath), it returns a `GenericObject` proxy
- This is the **normal case** in our app — we don't have the original application's classes
- All domain objects come back as `GenericObject` instances
- To read a GenericObject's fields:
  1. `container.ext().storedClass(genericObj)` → get its `StoredClass`
  2. `storedClass.getStoredFields()` → get fields for that class level
  3. `field.get(genericObj)` → read the value
  4. Walk up `getParentStoredClass()` for inherited fields
- To get the class name: `genericObj.getGenericClass().getName()` → `ClassUtil.getClassName()`
- **Gotcha**: primitives (int, long, boolean, etc.) come back as their Java wrapper types, not GenericObject

## Collections in DB4O

- Collections (Vector, ArrayList, LinkedList, etc.) may appear as:
  1. **Real Java Collection** — after activation, the object is a proper `java.util.Collection`; use it directly
  2. **GenericObject proxy** — DB4O stores collections using internal translator fields
- For GenericObject proxies, the actual items live in a field named `com.db4o.config.TCollection` (or similar `com.db4o.config.T*` prefix) → `RecipeCollectionItems.extractFromGenericObject()`
  - That translator field contains an array of the collection's items
  - Must traverse the entire class hierarchy to find it (translator field may be in a parent class)
- Collection extraction is unified in `RecipeCollectionItems.getItems(container, collectionObj)`:
  1. Activate the collection object
  2. If it's already a `java.util.Collection` → use directly
  3. If it's a `GenericObject` → search for `com.db4o.config.T*` translator fields → extract array contents
  4. If it's a plain array (but not `byte[]`) → convert to list via `ValueUtil.arrayToList()`
- **Gotcha**: `byte[]` arrays are **not** collections — they are binary data (exported as Base64)
- Known collection class names: `java.util.Vector`, `java.util.ArrayList`, `java.util.LinkedList`, any class containing `VectRechID` → `ObjectResolverUtil.isCollectionClassName()`
- Collection type detection for schema building → `CollectionTypeUtil.isCollectionType()`

## Database Schema Discovery

The database schema is the runtime-discovered structure of what actually exists:

1. `DODatabaseService.getDatabaseSchema()` → delegates to `DODatabaseReader.readDatabaseAsSchema()`
2. `DatabaseUtil.getStoredClassesSafely(container)` → enumerate all `StoredClass` entries
3. Create `DODatabaseContext` with container + stored class map
4. For each `StoredClass` → `DOClassConverter.convertStoredClassToSchemaClass()`:
   - Extracts: class name, parent class name, object IDs, fields
   - Fields converted via `DOFieldsConverter` → `DOFieldConverter` per field
   - Field type normalization: `java.lang.String` → `string`, `java.lang.Integer` → `int`, etc.
   - Collection detection based on type name
5. Post-process: `DOObjectDeduplicator.deduplicateObjectIdsInInheritanceHierarchies()` removes inherited ID duplicates
6. Result cached in `DODatabaseService.context.databaseSchema`

## Reference Schema vs. Database Schema

| Aspect | Reference Schema | Database Schema |
|--------|-----------------|-----------------|
| Source | `schema/reference-schema.xml` | Runtime discovery from DB4O |
| Purpose | Defines **what to export and how** | Defines **what exists** |
| Contains | Export flags, field mappings, skip rules, virtual fields, value maps | Raw class/field/ID inventory |
| Drives | Export decisions, field naming, filtering | Structure validation, object retrieval |
| Read by | `DOReferenceSchemaReader` | `DODatabaseReader` |
| Stored in | `DOSchemaService.referenceSchema` | `DODatabaseService.context.databaseSchema` |

Both are represented as `DOSchema` containing `DOSchemaClass[]` containing `DOSchemaField[]`.
