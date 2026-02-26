# Data Navigation

How to navigate from a class to its instances, then to their fields, then to everything they reference — until we hit a loop or reach leaf primitives.

## Starting Point: Class → Object IDs

- Given a `DOSchemaClass`, its `objectIds` (raw) or `uniqueObjectIds` (deduplicated) contain all instance IDs
- These IDs come from `StoredClass.getIDs()` at schema discovery time → `DOClassConverter`
- The export starts with whichever ID array the class provides → `ExportEngine.exportClassToFile()`

## Step 1: Object ID → Object Instance

- `container.ext().getByID(objectId)` → returns a hollow object → `ObjectActivator.getAndActivate()`
- The object is almost always a `GenericObject` (we don't have original classes on the classpath)
- If `getByID()` returns null → object cannot be retrieved; skip it

## Step 2: Activate the Object

- `ObjectResolverUtil.activateObjectShallow(container, obj, objectId)` → `container.activate(obj, 1)`
- This populates the object's immediate fields (depth 1)
- Nested objects (references, collections) remain hollow until explicitly activated later
- Class name extracted via `ClassUtil.getClassName(obj)` → uses `GenericObject.getGenericClass().getName()`

## Step 3: Object → StoredClass → Fields

- `container.ext().storedClass(genericObj)` → retrieves the `StoredClass` for this instance
- **Critical**: `storedClass.getStoredFields()` only returns fields declared at **this class level**
- To get all fields including inherited ones: `DatabaseUtil.getAllFieldsIncludingAncestors(storedClass)`
  - Walks up `getParentStoredClass()` chain, accumulates all `StoredField[]`
- This is used by both `FieldExporter.exportAllFields()` and `FieldExporter.countFieldsToExport()`

## Step 4: Read Field Value

- `storedField.get(genericObj)` → returns the field's value
- Possible return types:
  - `null` → field is empty
  - Java primitive wrapper (`Long`, `Integer`, `Boolean`, `Double`, `String`, etc.) → leaf value
  - `GenericObject` → another persistent object (follow the reference)
  - `java.util.Collection` → activated collection of items
  - `GenericObject` representing a collection → needs translator field extraction
  - `byte[]` → binary data
  - `Class<?>` (or GenericObject wrapping a Class) → class reference
  - Plain array (`Object[]`, `int[]`, etc.) → array of items

## Step 5: Determine Field Nature

For each field value, determine what it is and how to traverse it:

### 5a. Null
- Nothing to follow
- May or may not be exported depending on skip conditions → `ValueUtil.shouldSkipField()`

### 5b. Primitive / Leaf Value
- `container.ext().getID(fieldValue)` returns `0` or negative → not a persistent object
- Directly convert to string → `fieldValue.toString()`
- Navigation stops here (leaf node)
- Applied: value mapping → `FieldValueMapper.applyMapping()`, formatting → `ValueUtil.formatFieldValue()`

### 5c. Persistent Object Reference
- `container.ext().getID(fieldValue)` returns positive `long` → it's a persistent DB4O object
- Get class name → `ClassUtil.getClassName(fieldValue)`
- Look up in reference schema → `SchemaUtil.findClassByName()`
- **If IDEntite** (see [Data Interpretation](data-interpretation.md)):
  - Follow the IDEntite resolution path
- **If regular object**:
  - Recurse: `ObjectExporter.exportObjectRecursively()` on this object's ID
  - The recursion restarts at Step 1 for this new object

### 5d. Collection (Schema-Flagged)
- `DOSchemaField.isCollection == true` in the reference schema
- Activate collection: `container.activate(fieldValue, 1)`
- Extract items via `RecipeCollectionItems.getItems(container, fieldValue)`
- For each non-null item in the collection:
  - If IDEntite handling applies → `IDReferenceDetector.detectIDReference()`
  - Otherwise → `FieldExporter.exportFieldValue()` on each item (recurse to Step 1)

### 5e. Java Collection (Not Schema-Flagged)
- `fieldValue instanceof Collection` and not `GenericObject`
- Already a real collection; iterate directly
- Same item processing as 5d

### 5f. Collection by Ancestry (Safety Net)
- `fieldValue instanceof GenericObject` and schema field type inherits from a known collection base class
- Detected via `CollectionTypeUtil.isCollectionByAncestry()`, which walks `DOSchemaClass.parentClassName` chain
- Catches custom collection classes (e.g. `VectChampPerso → VectRechID → HVector → Vector`) whose names don't match the hardcoded keyword list
- Processed same as 5d via `exportSchemaCollectionField()`

### 5g. Array
- `fieldValue.getClass().isArray()`
- Special case: `byte[]` → binary data, not navigated (exported as Base64)
- Other arrays: wrap in iterable via `FieldExporter.createArrayIterable()`, process each item

### 5h. Class Reference
- Schema field type is `java.lang.Class` or `Class`
- May be a real `Class<?>` or a GenericObject wrapping one
- Extract class name string → navigation stops (leaf value)

## Step 6: Virtual Fields (Schema-Only)

- Some fields exist only in the reference schema, not in the database → `DOSchemaField.isVirtualField()` (source starts with `@`)
- These use criteria-based queries to find related objects → `FieldExporter.exportVirtualFields()`
- Process:
  1. Get target class name from `schemaField.type`
  2. Load all objects of that class (cached per class name) → `FieldExporter.preloadedObjectsByClass`
  3. For each `DOFieldCriteria`:
     - Extract match value from current object: `this.fieldName` → `storedField.get(obj)`
     - Extract comparison value from target object: `criterion.with` (supports dotted paths like `mIDIntervention.mID`)
  4. Filter by operator (equals, notEquals, greaterThan, etc.) → `FieldExporter.compareCriterionValues()`
  5. Combine multiple criteria with AND/OR → `schemaField.criteriasOperator`
  6. Matching objects are exported as a collection

## Step 7: Dotted Field Path Navigation

- Virtual field criteria support dotted paths (e.g., `mIDIntervention.mID`) → `FieldExporter.getFieldValueByPath()`
- Split by `.`, then for each segment:
  1. `container.ext().storedClass(currentValue)` → get StoredClass
  2. `storedClass.storedField(fieldName, null)` → look up field
  3. `field.get(currentValue)` → get value, which becomes `currentValue` for next segment

## Loop Prevention

- `ExportOperation.exportedObjectIds` (a `Set<Long>`) tracks all exported object IDs
- Before exporting, `ObjectIdTracker.shouldExport()` checks:
  - **Root objects** (from `objectIds` array): always exported (not checked for duplicates) — allows multiple criteria-based exports of the same class
  - **Embedded objects** (`isEmbedded=true`): always exported — value objects can repeat inline
  - **All other objects**: checked against the set; skip if already exported
- This prevents infinite loops when objects reference each other cyclically

## Inheritance Field Lookup in Schema

- When looking up a field definition in the reference schema, we search ancestors too → `DatabaseUtil.findSchemaFieldByNameIncludingAncestors()`
- Walks up `DOSchemaClass.parentClassName` chain until the field is found or we run out of parents
- This is critical because a field may be defined on an ancestor class in the reference schema but appear on a descendant in the database

## Navigation Summary Diagram

```
Class (objectIds)
  └─ for each objectId
       └─ getByID(objectId) → activate → GenericObject
            └─ getAllFieldsIncludingAncestors()
                 └─ for each StoredField
                      ├─ null → skip or export empty
                      ├─ primitive → export value (leaf)
                      ├─ GenericObject (IDEntite) → resolve mID → find target → recurse
                      ├─ GenericObject (regular) → recurse into object
                      ├─ Collection/GenericObject-collection → extract items → recurse each
                      ├─ GenericObject (collection by ancestry) → extract items → recurse each
                      ├─ byte[] → export as Base64 (leaf)
                      └─ array → iterate items → recurse each
            └─ virtual fields (@source)
                 └─ criteria query → matching objects → export as collection
```
