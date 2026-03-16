# XSD Overhaul Implementation Plan

## Guiding Principle

The XSD is the authority. The export conforms to it. We are in control of our XML export — design decisions must not be dictated by DB4O quirks.

## User Decisions

- **Field ordering**: Sort alphabetically by destination name (enables `xs:sequence` + `xs:extension`)
- **Top-level elements**: All exported classes (`isExported=true`) are top-level elements in root `<xs:choice>`

---

## Step 1 — Sort Field Output

**Files**: `FieldExporter.java` (line 162+)

In `exportAllFields()`, sort `StoredField[]` by schema destination name after collection. Fields without schema mapping should be **skipped** (not written as "unknown"). This resolves the deterministic ordering requirement for `xs:sequence`.

- `DatabaseUtil.getAllFieldsIncludingAncestors()` returns unsorted `StoredField[]` — sort by `DOSchemaField.getDestinationName()`
- Fields with no matching `DOSchemaField` → skip silently (no "unknown" element)

---

## Step 2 — Remove "unknown" Fallback

**Files**: `FieldExporter.java` (lines 317, 434, 451, 684), `XSDClassWriter.java` (line 108)

Remove all 4 `"unknown"` fallback locations in `FieldExporter` and the `<xs:element name="unknown">` wildcard from `XSDClassWriter`. Unmapped fields are simply not exported.

---

## Step 3 — Strong Type Mappings

**Files**: `XSDTypeMapper.java` (lines 51, 57), `XSDFieldWriter.java` (lines 44-46)

- `Boolean` → `xs:boolean` (currently `xs:string`)
- `Date` → `xs:dateTime` (currently `xs:string`)
- `null`/empty type → error instead of `xs:anyType`
- Remove `byte[]` → `xs:string` override in `XSDFieldWriter` (line 44-46); let `XSDTypeMapper`'s `xs:base64Binary` mapping flow through

---

## Step 4 — ISO Date Formatting

**Files**: `FieldExporter.java` (before line 594)

Add `instanceof Date` check in `exportRegularField()` before the `toString()` path. Use `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")` to produce ISO 8601 values that conform to `xs:dateTime`.

---

## Step 5 — Full-Schema XSD Generation (MOST IMPACTFUL)

**Files**: `XSDSchemaWriter.java`, `XSDContext.java`, `XSDBuilder.java`

**Rewrite `XSDSchemaWriter`** to iterate ALL `isExported=true` classes from the reference schema (`DOSchemaService.getInstance().getReferenceSchema().getClasses()`). Remove the observation-based discovery approach entirely:

- Remove `writeRegisteredClasses()` and `writeDiscoveredReferencedTypes()`
- Remove the `while(foundNewTypes)` iterative discovery loop
- Remove `classMap`, `fieldsByClass`, `topLevelObjects`, `referencedTypes` tracking from `XSDContext`
- All `isExported=true` classes get a complexType definition + global element declaration
- `XSDBuilder.addClass`, `addTopLevelObject`, `addField` registration methods become unnecessary

---

## Step 6 — xs:extension Inheritance

**Files**: `XSDClassWriter.java`

Switch `xs:all` → `xs:sequence` in `writeFieldsBody()`. Implement `xs:extension` for classes with exported parent classes:

```xml
<!-- Class with parent -->
<xs:complexType name="ChildType">
  <xs:complexContent>
    <xs:extension base="ParentType">
      <xs:sequence>
        <!-- only fields declared on ChildType, not inherited -->
      </xs:sequence>
    </xs:extension>
  </xs:complexContent>
</xs:complexType>
```

- Only emit fields **declared directly** on the class (not inherited fields)
- Parent must also be an exported class for `xs:extension` to apply
- If parent is not exported, flatten all fields into the child's sequence

---

## Step 7 — Hierarchy-Based xs:choice

**Files**: `XSDContext.java`, `XSDFieldWriter.java` (lines 125, 167, 185, 212)

Add `getAllDescendants(DOSchemaClass, DOSchema)` utility method to `XSDContext` using BFS/DFS over `parentClassName`. Replace ALL `xs:any processContents="lax"` with `xs:choice` listing base class + all exported descendants:

```xml
<!-- Instead of xs:any -->
<xs:choice>
  <xs:element ref="ParentType"/>
  <xs:element ref="ChildType1"/>
  <xs:element ref="ChildType2"/>
</xs:choice>
```

- Only include descendants where `isExported=true`
- If no descendants exist, use a direct `xs:element ref="..."` (no choice needed)

---

## Step 8 — Title in xs:annotation

**Files**: `XSDClassWriter.java`

Replace XML comments (`<!-- title -->`) with proper `xs:annotation/xs:documentation`:

```xml
<xs:complexType name="MyType">
  <xs:annotation>
    <xs:documentation>Human-readable title from schema</xs:documentation>
  </xs:annotation>
  ...
</xs:complexType>
```

---

## Step 9 — xs:enumeration for Value Maps

**Files**: `XSDFieldWriter.java`

For fields with a `DOSchemaValueMap`, generate an inline `simpleType` restriction with `xs:enumeration` facets:

```xml
<xs:element name="status">
  <xs:simpleType>
    <xs:restriction base="xs:string">
      <xs:enumeration value="active"/>
      <xs:enumeration value="inactive"/>
      <xs:enumeration value="archived"/>
    </xs:restriction>
  </xs:simpleType>
</xs:element>
```

---

## Step 10 — Remove Empty-Class Wildcards

**Files**: `XSDClassWriter.java`

- Empty classes (no exported fields) → empty `complexType` (not `xs:any` skip)
- Non-exported classes → omit entirely from XSD
- Unknown/unmapped types → ERROR (fail loudly during generation)

---

## Step 11 — Extra.xml Metadata

**Files**: `XmlFormatHandler.java` (around line 228)

Add `StructuredWriterMetadata` in `exportUnreachedObjects()` so that `Extra.xml` gets proper XML declaration and XSD reference, consistent with all other exported XML files.

---

## Step 12 — Clean Up Observation Hooks

**Files**: `XmlFormatHandler.java`

Remove `observeObject()`, `observeField()`, `observeReferencedClass()` overrides. The XSD is no longer observation-driven — it's generated from the full reference schema.

---

## Step 13 — Virtual Field Timing

**Files**: `FieldExporter.java` (line 652)

Move `observeField` call before `exportCollectionLikeField` in `exportVirtualFields()`. Currently the observation happens after export, which is the wrong order. (Note: with Step 12 removing observation hooks, this may become moot — but fix the ordering regardless for correctness.)

---

## Step 14 — Update Specification Document

**Files**: `doc/xsd-specification.md`

Rewrite `xsd-specification.md` to reflect all new decisions made in Steps 1-13. Update all 14 sections with new verdicts, justifications, and implementation details.

---

## Dependencies

Steps should be executed in order as they have dependencies:

1. **Step 1** (sort fields) enables **Step 6** (xs:sequence + xs:extension)
2. **Step 5** (full-schema) enables **Step 7** (hierarchy xs:choice) and **Step 12** (remove observation)
3. **Steps 1-7** are the core structural changes
4. **Step 5** is the most impactful single change

## Verification

After implementation, run:
```bash
./run-ui.sh ./local/46058/BackupManuel.zip.nozip --repeat-export
```

Verify:
- [ ] All XML files pass XSD validation
- [ ] Fields sorted alphabetically in XML output
- [ ] `xs:extension` used for inherited classes
- [ ] `xs:choice` used for polymorphic fields (no `processContents="lax"` or `"skip"`)
- [ ] `xs:boolean` and `xs:dateTime` types in XSD
- [ ] No `"unknown"` elements in XML or XSD
- [ ] Full schema coverage (all `isExported=true` classes have complexType)
- [ ] `Extra.xml` has proper metadata
- [ ] ISO 8601 date values in XML output
- [ ] Value map fields have `xs:enumeration` restrictions
