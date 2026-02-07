# Referenced Class Tracker Bug Fix

## Bug Description
Classes that belonged to modules (e.g., `gest.activite.Acte` in the `Listes` module) were incorrectly being exported to the "Referenced" folder instead of their proper module folder.

## Root Cause
The `ReferencedClassTracker.reset()` method was clearing the `moduleClasses` map, which contained the mapping of which classes belong to which modules. This map was being cleared AFTER modules were registered but BEFORE classes were exported.

### Execution Flow

**Before Fix:**
1. `XMLExportEngine.exportModuleStructured()` calls `registerModuleClasses()` 
   - ✓ All modules registered with their classes
   - `moduleClasses` map populated correctly
   
2. `exportModuleStructured()` calls `exportModuleRecursive()` → `exportClassToFile()`
   
3. `exportClassToFile()` creates `ExportOperation` and `ObjectExporter`
   
4. `exportClassToFile()` calls `objectExporter.reset()`
   
5. `ObjectExporter.reset()` calls `referencedClassTracker.reset()`
   
6. **BUG:** `ReferencedClassTracker.reset()` clears `moduleClasses.clear()`
   - ✗ All module registrations lost!
   
7. During field export, `registerReferencedClass("gest.activite.Acte")` is called
   
8. `isInExportRequest()` checks `moduleClasses` but it's empty → returns false
   
9. Acte incorrectly added to `referencedClasses` set → exported to "Referenced" folder

## The Fix

**File:** `ReferencedClassTracker.java`

Changed the `reset()` method to NOT clear `moduleClasses`:

```java
/**
 * Resets the tracker for a new export operation.
 * NOTE: Does NOT clear moduleClasses - those are registered once at the start
 * of the entire export and should persist across all class exports.
 */
public void reset() {
    // Do NOT clear moduleClasses - they're registered once for the whole export!
    referencedClasses.clear();
    exportedReferencedClasses.clear();
}
```

## Rationale

The `moduleClasses` map represents the **export structure** (which classes belong to which modules). This is determined ONCE at the beginning of the export and should remain constant throughout the entire export operation.

The `referencedClasses` and `exportedReferencedClasses` sets track **runtime state** during export:
- `referencedClasses`: Classes discovered during field traversal that weren't in the original request
- `exportedReferencedClasses`: Which of those referenced classes have already been exported

These runtime tracking sets should be reset between class exports, but the module structure should persist.

## Verification

**Before Fix (Debug Output):**
```
DEBUG: Registering module 'Listes' with 18 classes
DEBUG: >>> Module 'Listes' contains gest.activite.Acte
...
DEBUG: Registering referenced class: gest.activite.Acte (not in any module - will go to Referenced folder)
DEBUG: Current modules: []  ← BUG: Empty!
```

**After Fix:**
- Acte correctly recognized as part of Listes module
- Not added to referencedClasses
- Exported to Listes module folder

## Files Modified

1. **ReferencedClassTracker.java** - Fixed `reset()` to preserve `moduleClasses`
2. **XMLExportEngine.java** - Removed defensive check (no longer needed)

## Related Cleanup

Also removed debug logging that was added during investigation:
- Removed `System.out.println()` statements in `registerModule()`
- Removed `System.out.println()` statements in `registerReferencedClass()`
