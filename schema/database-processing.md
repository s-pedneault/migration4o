# Database processing
This file describes the entire sequence of processing a database, using a schema.

# Classes processing phase
1. Instanciate a DOEngine
    1. Instanciate a DOSchema
        1. Load schema/migration-schema.xml
        1. We should get a list of modules (each module is a tree of classes), and a list of independant classes (that are reused throughout the architecture)
    1. Instanciate a DODatabase
        1. For each module from DOSchema.getModules()
            1. For each class from DOModule.getClasses()
                1. Search for that same class in the database
                1. If not found: report schema class as "not found in database"
                1. If found:
                    1. Add a reference in the database class to its schema class
                    1. Add the database class to a list of classes to process
        1. For each database class to process:
            1. Resolve parent class
        1. For each database class to process:
            1. Resolve inheritance chain
        1. For each database class:
            1. Resolve subclasses
            1. Mark end classes (classes that have no subclasses)

# Objects processing phase
1. Initialize reachability tracker with all object IDs from all database classes
1. Build a pre-computed index mapping each unique object ID to its most specific class
    - Sort classes by specificity (leaf classes first, then by inheritance depth)
    - For each object ID, use the most specific class found
    - This index enables O(1) lookup performance instead of expensive database scans
1. For each unique object ID in the database:
    1. Determine the object's ACTUAL runtime class (most specific type) from the pre-built index
    1. Do OBJECT ID PROCESSING (recursive, with reference following)
1. Mark reachability in all resolved objects based on references
1. Populate database classes with their resolved objects for reporting
1. Build HTML report with three main sections:
    1. Reached objects:
        1. An interactive tree view of object IDs organized by schema module structure
        1. Each node shows class name with object count
        1. Expandable to show individual object IDs (with display limits for performance)
    2. Unreached objects:
        1. Objects grouped by their most precise class with actual object IDs listed
        1. Expandable lists with display limits (200 IDs visible, "show more" message beyond)
    3. Object detail view (when clicking an object):
        1. **📎 Direct References**: Clickable links to referenced objects
        1. **📋 Data Fields**: Primitive field values (strings, numbers, dates) displayed with muted styling
        1. Automatically shows "No references (leaf object)" only if object truly has no outgoing references

## OBJECT ID PROCESSING
1. If object ID has already been processed, skip (avoid infinite loops)
1. Look up the object's most specific class from the pre-built index (O(1) performance)
    - If not found in index, object doesn't exist - return null
1. Activate the object (if not already activated)
1. Mark the object ID as reached for all classes in its inheritance chain
1. Create a DODatabaseObject representation with references
1. **Special case - ID-type objects** (classes starting with "ID"):
    1. Extract the `mID` field value (the target entity ID)
    1. Create a **synthetic reference** directly to the final target entity
        - This reference has `field=null` to indicate it's synthetic (not a real field reference)
        - Enables direct navigation from ID objects to their targets in the report
    1. Return early (ID objects only have this one synthetic reference)
1. For normal objects, process all fields:
    1. Do FIELD PROCESSING for each field

**Performance optimization**: All object lookups use the pre-built `objectIdToClassIndex` map for O(1) constant-time access, avoiding expensive O(n*m) database scans.

## FIELD PROCESSING
1. For each field in the object's class definition:
    1. If field is a **collection type** (Vector, ArrayList, LinkedList, etc.):
        1. Skip for now (collections handled separately - see COLLECTION PROCESSING below)
    1. If field is a **non-primitive object reference**:
        1. Extract field value using ObjectResolverUtil
        1. If field value is null, skip
        1. Get the object ID of the referenced object
        1. If object ID exists:
            1. Create a DOObjectReference with the target object ID
            1. Recursively do OBJECT ID PROCESSING on the referenced object
            1. The recursive call will handle:
                - Normal objects → process their fields
                - ID-type objects → create synthetic reference to final target via `mID` field
                - Already-processed objects → skip (cached in `processedObjectIds` set)

**Note on ID-type handling**: When a field references an ID object (e.g., `IDRapport`), the recursive processing automatically creates a synthetic reference from that ID object to its final target. This means navigation in the report goes: `SourceObject → IDObject → FinalTarget` with all links properly resolved.

## COLLECTION PROCESSING
After direct field references are extracted, collection fields are processed separately:

1. **Standalone collection objects** (object itself is a collection):
    1. Detect if object is any collection-like type (Collection, Object[], Map)
    1. Extract all contained object IDs using **universal collection extraction** (see below)
    1. Determine content type (element type) from field schema or first element
    1. Create a synthetic DOCollectionReference with field name "collection_elements"
    1. Process each contained object (see step 3 below)

2. **Collection-typed fields** (fields that are collections):
    1. For each field marked as a collection type in the class definition:
        1. Extract the field value using ObjectResolverUtil
        1. If field value is null, skip
        1. Use **universal collection extraction** to get all contained object IDs
        1. Get content type from schema (field.getContentTypeClass())
        1. Create a DOCollectionReference linking the parent object to all contained IDs

3. **Process contained objects recursively**:
    1. For each object ID contained in the collection:
        1. Skip if already processed (avoid duplicates)
        1. Mark object as reached for its entire inheritance chain (if class known)
        1. Use pre-built index for O(1) lookup of the object's most specific class
        1. Recursively do OBJECT ID PROCESSING on the contained object
        1. Add to global resolved objects list

### Universal Collection Extraction
This is a unified algorithm that works with ANY collection-like object, regardless of its specific type:

1. **Convert to Iterable**: Transform the collection into a standard Iterable interface:
    - If already `Iterable` (List, Set, Vector, etc.) → use directly
    - If `Object[]` array → convert using `Arrays.asList()`
    - If `Map` → can iterate over entries
    
2. **Count total size**:
    - For `Collection` types → use `.size()`
    - For arrays → use `.length`
    - For other iterables → iterate and count manually

3. **Extract object IDs**:
    - Loop through each element in the iterable
    - Use `container.ext().getID(item)` to get the db4o object ID
    - Collect all non-null IDs into a list

4. **Determine content type**:
    - First try to get from field schema definition (`field.getContentTypeName()`)
    - If not available, infer from first element's actual class
    - Default to "java.lang.Object" if unknown

5. **Return result** with:
    - Array of contained object IDs
    - Content type class name
    - Total size of collection

**Why "universal"?**: This single algorithm handles Vector, ArrayList, LinkedList, HashSet, TreeSet, Arrays, and any other collection type uniformly, without needing type-specific code. It works even when db4o returns GenericObject wrappers by using **db4o's own APIs** (`StoredClass.storedField().get()`) instead of Java reflection, allowing uniform access to both native objects and GenericObject wrappers.

### GenericObject Handling
When db4o cannot find the original Java class files, it returns objects as `GenericObject` wrappers. Our system handles these transparently:

**Class Identity Preservation**:
- Class information is extracted from GenericObject using `genericObj.getGenericClass().getName()`
- Stored in DODatabaseObject's `mostSpecificClass` field (e.g., "VectRechID", "ArrayList")
- Full inheritance chain stored in `allClasses` array
- GenericObject itself is NOT stored in our model - only used temporarily during extraction

**Field Value Extraction**:
- Uses db4o's `StoredClass` and `StoredField` APIs (NOT Java reflection)
- Call sequence: `container.ext().storedClass(obj)` → `storedClass.storedField(fieldName)` → `storedField.get(obj)`
- Works identically for both native objects and GenericObject wrappers

**GenericObject Collection Challenge**:
- **Problem**: GenericObject does NOT implement the `Collection` interface, even when wrapping a collection class
- **Impact**: Universal collection extraction relies on `instanceof Collection` checks, which fail for GenericObjects
- **Solution**: Convert GenericObject → concrete collection for iteration

**Collection Conversion**:
- **Detection**: Uses `CollectionTypeUtil.isCollectionType(genericClassName)` which recognizes:
  - All Java collection types: Vector, ArrayList, LinkedList, HashSet, TreeSet, Stack, Queue, etc.
  - Collection interfaces: Collection, List, Set, Map
  - Project-specific: VectRechID and other custom collection classes
  - Arrays: `[]` suffix
- **Conversion Process**:
  1. Access internal storage using `storedClass.getStoredFields()`
  2. Extract element arrays or collections from internal fields  
  3. Build appropriate concrete collection (ArrayList, LinkedList, or Vector based on class name)
- **Class Identity Preserved**: Original class name (e.g., VectRechID) is already captured in `mostSpecificClass` before conversion
- **Temporary Usage**: The concrete collection is only used for extracting object IDs, then discarded
- **Result**: All collection types are handled uniformly - extract contents, preserve class identity

**Object ID Retrieval**:
- Uses `container.getID(obj)` which works for both native and GenericObject
- Always returns the correct db4o internal object ID

**Why avoid reflection?**: Java reflection requires the actual class files to be present. db4o's APIs work with the database's internal metadata, so they function even when original classes are missing or have changed.

## End goals
- From a loaded DOEngine, we should be able to:
    - Lookup root classes in the database (defined as module classes in the schema)
    - Navigate through every possible path to confirm reachability of every object
    - **ID-type objects are automatically resolved**: When encountering an ID object, it has a synthetic reference directly to the final target entity (via its `mID` field)
    - **Collections are supported**: Loop over collection contents and traverse each reference
    - **Performance optimized**: Pre-built index enables O(1) lookups instead of expensive database scans
- We maintain a complete in-memory tree of all object relationships starting from root classes
- **HTML Report provides**:
    - Interactive drill-down tree view of reached objects by module/class
    - Actual object IDs displayed (not just counts)
    - Click-through navigation following all references (including synthetic ones)
    - Visual display of both reference fields AND primitive data fields
    - Clear distinction between navigable references (blue links) and data values (muted display)


## Report format
The HTML reachability report provides comprehensive analysis with three main sections:

### 1. Global Statistics
- Total objects in database
- Reached object count
- Unreached object count  
- Percentage metrics

### 2. Reached Objects (Interactive Tree View)
- Organized by schema module structure
- Each node shows: `ClassName (count)` 
- Click to expand and see individual object IDs
- Display limits for performance:
  - Shows up to 100 object IDs per class
  - Displays "...and N more (click class header to show all)" if exceeded
- Click any object ID to view its details:
  - **📎 Direct References**: Clickable links to referenced objects (blue)
  - **📋 Data Fields**: Primitive values (strings, ints, dates) with muted gray styling
  - Type information shown for each field
  - String truncation for long values (100 chars max)
  - Date formatting: `yyyy-MM-dd HH:mm:ss`

### 3. Unreached Objects (Grouped by Class)
- Lists objects that were never reached from root classes
- Grouped by most precise class name
- Shows actual object IDs with display limits:
  - Up to 200 IDs shown per class
  - "...and N more IDs not shown" message if exceeded
- Expandable/collapsible sections for better navigation

**Key Features**:
- All object navigation is **pre-resolved** during loading (not computed on-demand in report)
- ID-type objects have **synthetic references** that directly point to final targets
- Clicking through references is fast (no database queries needed)
- Primitive fields clarify that "leaf objects" are successfully resolved even with no outgoing references
