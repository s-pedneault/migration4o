# Reach Analysis Procedure

## Overview

The Reach Analysis feature identifies all objects in the database that are reachable from root classes through object references. This process helps determine which objects are actively used in the application by traversing the object graph from entry points.

## Purpose

- **Identify Reachable Objects**: Determine which database objects can be reached through references from root classes
- **Track Object Usage**: Understand which objects are actually used vs. stored
- **Aid Migration Planning**: Focus migration efforts on objects that are actively referenced
- **Detect Orphaned Objects**: Find objects that exist but are never referenced (unreached objects)

## Step-by-Step Procedure

### 1. Initialization

**Action**: User clicks the "Reach" button in the Migration Coverage Panel

**System Response**:
- Opens a monitoring dialog with status label, exploration tree view, and progress bar
- Initializes tracking structures:
  - Global `reachedObjectIds` set to prevent duplicate processing and infinite loops
  - `classProcessedCount` map to track progress per class
  - `classTotalCount` map with total objects per class
  - Exploration tree to visualize the traversal path

### 2. Database Connection

**Action**: System opens the database connection

**Steps**:
- Creates `DODatabaseOpener` instance
- Opens database using the stored database path
- Verifies connection is successful
- Updates status: "Database opened successfully"

### 3. Root Class Identification

**Action**: System identifies entry point classes for traversal

**Criteria**: Classes that are descendants of:
- `gest.gen.EntiteContientID` - Main business entities containing data
- `gest.gen.EntiteParam` - Parameter configuration entities

**Steps**:
- Iterate through all classes in the database schema
- Check class hierarchy for parent classes
- Count total root classes to process
- Update status with count of root classes found

### 4. Root Class Processing

**For Each Root Class**:

#### 4.1. Class Selection
- Select next unprocessed root class
- Extract simple class name for display
- Update status: "Exploring X/Y: ClassName (N objects)"

#### 4.2. Object Iteration
- Retrieve `uniqueObjectIds` array for the class
- For each object ID in the array:
  - Call `exploreObjectRecursively()` method
  - Pass global `reachedObjectIds` set for deduplication

### 5. Recursive Object Exploration

**Entry Point**: `exploreObjectRecursively(objectId, reachedObjectIds, ...)`

#### 5.1. Duplicate Check (Atomic)
```
if (!reachedObjectIds.add(objectId)) {
    return; // Already processed, skip
}
```
- Uses `Set.add()` which returns `false` if element already exists
- Atomic operation prevents race conditions
- Immediately returns if object was previously reached

#### 5.2. Object Retrieval and Activation
- Retrieve object from database by ID: `container.ext().getByID(objectId)`
- Extract class name from object
- Update class processing counter
- Create tree node label: "ClassName [processed/total]"
- Add node to exploration tree for visualization
- **Activate object** using `ObjectResolverUtil.activateObject()` with depth Integer.MAX_VALUE
  - Critical step to ensure all field values are loaded from database
  - Without activation, field values may be null or incomplete

#### 5.3. Field Exploration (GenericObject only)
If object is a `GenericObject`:
- Get `StoredClass` metadata
- Retrieve all `StoredField[]` array
- For each field: call `exploreAllFields()`

#### 5.4. Tree Cleanup
- Remove object node from tree when exploration completes
- Keeps tree focused on current exploration path

### 6. Field Value Processing

**Entry Point**: `exploreAllFields(obj, reachedObjectIds, ...)`

**For Each Field in Object**:

#### 6.1. Field Value Retrieval
- Get field value using `field.get(obj)`
- Skip if value is null

#### 6.2. Field Type Detection and Processing

**Collection Types** (`Collection` interface):
- Extract all items from collection
- Check if any item is "important" (descendant of EntiteContientID/EntiteParam/IDEntite)
- If has important items:
  - Add field node to tree: "Field: fieldName"
  - Process each item with field node as parent
- Otherwise:
  - Process items without showing field in tree (reduces noise)

**Array Types**:
- Get array length using reflection
- Check if any element is important
- If has important items:
  - Add field node to tree
  - Process each element with field node as parent
- Otherwise:
  - Process elements without showing field in tree

**Single Object References**:
- Get object ID using `container.ext().getID(fieldValue)`
- Skip if ID ≤ 0 (not persistent)
- If object is important:
  - Add field node to tree
  - Process with field node as parent
- Otherwise:
  - Process without showing field in tree

**Primitive Types and Non-Persistent Values**:
- Ignored (not references to other objects)

### 7. Field Reference Processing

**Entry Point**: `processFieldReference(item, fieldName, reachedObjectIds, ...)`

#### 7.1. Object ID Extraction
- Get persistent object ID
- Return if ID ≤ 0 (not a database object)

#### 7.2. Class Name Extraction
- Get class name from object
- Return if null

#### 7.3. IDEntite Detection and Handling

**Check if object is IDEntite descendant**:
- Look up class in schema
- Check if descendant of `gest.gen.IDEntite`

**If IDEntite**:
- Retrieve target class name from `pointsTo` attribute (preferred)
- Fallback: Extract from field name if `pointsTo` is null
  - Field name `mIDTypeActivite` → expected type `TypeActivite`
  - Class name `IDTypeActivite` → expected type `TypeActivite`
- Call `handleIDEntiteRelationship()` with target type

**If Not IDEntite** (Regular Object):
- Call `exploreObjectRecursively()` to traverse into the object
- Recursion continues the graph traversal

### 8. IDEntite Relationship Handling

**Entry Point**: `handleIDEntiteRelationship(idEntiteObj, expectedType, reachedObjectIds, ...)`

This handles the special db4o pattern where `IDEntite` objects are lightweight references that point to heavier `EntiteContientID` objects via an `mID` field.

#### 8.1. Mark IDEntite as Reached
- Add IDEntite object ID to `reachedObjectIds`

#### 8.2. Extract mID Field
- Activate the IDEntite object
- Search through fields for field named "mID"
- Extract value (Integer or Long)
- Return if no mID found

#### 8.3. Find Matching EntiteContientID Objects
- Iterate through all classes descended from `EntiteContientID`
- Filter by expected type if specified:
  - Only process classes whose simple name matches `expectedType`
  - Skip classes that don't match
- For each matching class:
  - Retrieve `uniqueObjectIds` array
  - For each object ID:
    - Retrieve and activate object
    - Extract its mID field value
    - **If mID values match**:
      - Call `exploreObjectRecursively()` on the EntiteContientID object
      - This traverses the "real" object graph through the lightweight reference
      - Break after first match (assume 1:1 relationship)

### 9. Reached Objects Aggregation

**After All Root Classes Processed**:

#### 9.1. Group by Class
- Create map: `Map<String, Set<Long>> reachedByClass`
- For each object ID in global `reachedObjectIds`:
  - Retrieve object from database
  - Get class name
  - Add object ID to the set for that class name

#### 9.2. Update Schema Classes
- For each class name and its set of reached object IDs:
  - Find corresponding `DOSchemaClass` in schema
  - Call `addIdsToReachedList(idsToAdd)` to populate `reachedObjectIds` field
  - This method adds IDs without duplicates

### 10. Table Update

**Action**: Update Migration Coverage table with reached counts

**For Each Row**:
- Update "Reached" column (index 3) with `getReachedObjectCount()`
- Refresh table display to show new values

### 11. Export Results

**Action**: Update export files with reached object information

**Files Updated**:

**counters.txt** (4 columns, tab-delimited):
```
ClassName    ObjectsCount    UniqueCount    ReachedCount
```
- For each class in sorted order
- Shows progression: total objects → unique (after deduplication) → reached (after analysis)

**entries.txt**:
- Lists all classes with their counts
- Used for migration tracking

**leafs.txt**:
- Lists only leaf classes in hierarchy
- Contains objects that have no subclasses

### 12. Completion

**Final Status Updates**:
- Display total count: "Reached X total objects"
- Close monitoring dialog
- Return control to user

## Key Algorithms

### Duplicate Prevention

**Atomic Set Operation**:
```java
if (!reachedObjectIds.add(objectId)) {
    return; // Already processed
}
```
- Single operation checks and adds
- Thread-safe
- Prevents infinite loops in cyclic object graphs

### IDEntite Resolution

**Two-Step Lookup**:
1. **Check pointsTo attribute**: Explicit mapping defined in schema XML
   ```xml
   <class source="gest.activite.IDActe" pointsTo="gest.activite.Acte">
   ```
2. **Fallback to name extraction**: Remove "ID" prefix from class/field name
   - `IDActe` → `Acte`
   - `mIDActe` → `Acte`

### Important Object Detection

Objects are "important" (shown in tree) if they descend from:
- `gest.gen.EntiteContientID`
- `gest.gen.EntiteParam`
- `gest.gen.IDEntite`

Other objects are processed but not visualized (reduces tree noise).

## Data Structures

### Global Tracking
- **reachedObjectIds**: `Set<Long>` - All object IDs reached during traversal
- **classProcessedCount**: `Map<String, Integer>` - Objects processed per class
- **classTotalCount**: `Map<String, Integer>` - Total unique objects per class

### Per-Class Storage
- **objectIds**: `long[]` - All objects of this class from database
- **uniqueObjectIds**: `long[]` - After deduplication (inheritance hierarchy)
- **reachedObjectIds**: `long[]` - Objects reached during analysis

## Performance Considerations

### Object Activation
- Uses `Integer.MAX_VALUE` depth for full activation
- Fallback to depth 10 if StackOverflowError occurs
- Critical for ensuring field values are loaded

### Tree Visualization
- Nodes added dynamically during exploration
- Nodes removed after processing to keep tree manageable
- Only "important" objects shown (filters noise)

### Progress Tracking
- Per-class counters updated in real-time
- Tree shows current exploration path
- Progress displayed as "Processed/Total" for each class

## Error Handling

### Object Retrieval Failures
- Catch exceptions when retrieving objects by ID
- Skip object and continue processing
- Log error to stderr

### Field Access Failures
- Catch exceptions when reading field values
- Skip field and continue with next field
- Log error to stderr

### Activation Errors
- StackOverflowError: Retry with depth 10
- Other exceptions: Log and skip object

## Output Files

### counters.txt Format
```
ClassName\tObjectsCount\tUniqueCount\tReachedCount
Acte\t150\t150\t142
TypeActivite\t85\t85\t85
...
```

### Interpretation
- **ObjectsCount**: Total objects in database for this class
- **UniqueCount**: Objects after deduplication (removing inherited duplicates)
- **ReachedCount**: Objects reachable from root classes
- **Unreached**: `UniqueCount - ReachedCount` = orphaned objects

## Use Cases

### Migration Planning
- Focus on classes with high ReachedCount
- Deprioritize classes with low reach (may be obsolete data)

### Data Cleanup
- Identify unreached objects (candidates for deletion)
- Verify expected object relationships exist

### Schema Validation
- Verify IDEntite → EntiteContientID mappings work correctly
- Ensure expected object graphs are reachable

### Performance Analysis
- Understand object graph complexity
- Identify classes with many references (potential bottlenecks)
