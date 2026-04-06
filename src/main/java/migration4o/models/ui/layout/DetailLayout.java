package migration4o.models.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.schema.modules.DOModuleService;
import migration4o.util.DatabaseUtil;
import migration4o.util.TypeUtil;

/**
 * Complete detail layout for a class export. Contains top-level layout nodes that define the record detail view structure.
 */
public class DetailLayout {
    public List<LayoutNode> nodes = new ArrayList<>();

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /** Serialize to JSON for embedding in HTML exports. */
    public String toJson() {
        if (nodes.isEmpty())
            return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0)
                sb.append(',');
            nodes.get(i).appendJson(sb);
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Serialize to JSON, resolving any layoutRef references by inlining the referenced class layouts with prefixed field paths.
     */
    public String toResolvedJson() {
        return toResolvedJson(null, null);
    }

    /**
     * Serialize to JSON, resolving layoutRef references, translating source-name refs to destination-name refs, adding field title labels, and auto-generating layouts for embedded classes that don't have one.
     */
    public String toResolvedJson(DOSchemaClass schemaClass, DOSchema refSchema) {
        if (nodes.isEmpty())
            return "null";
        Set<String> visiting = new HashSet<>();
        if (schemaClass != null && schemaClass.attributes.source != null)
            visiting.add(schemaClass.attributes.source);
        List<LayoutNode> resolved = new ArrayList<>();
        for (LayoutNode node : nodes)
            resolved.add(resolveNode(node, schemaClass, refSchema, visiting));
        // Post-process: mark lone table children of collapsible sections as
        // bare so the JS renderer skips the duplicate header.
        markBareTables(resolved);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < resolved.size(); i++) {
            if (i > 0)
                sb.append(',');
            resolved.get(i).appendJson(sb);
        }
        sb.append(']');
        return sb.toString();
    }

    private static LayoutNode resolveNode(LayoutNode node, DOSchemaClass schemaClass, DOSchema refSchema, Set<String> visiting) {
        String layoutRef = node.prop("layoutRef");
        if (layoutRef != null && (node.type == LayoutNodeType.SECTION || node.type == LayoutNodeType.TAB)) {
            String refPrefix = node.prop("ref");
            // Translate the ref prefix (source name) to destination name
            String destPrefix = refPrefix;
            DOSchemaClass embeddedClass = null;
            DOSchemaField parentField = null;
            if (refPrefix != null && schemaClass != null && refSchema != null) {
                parentField = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(schemaClass, refPrefix, refSchema);
                if (parentField != null) {
                    destPrefix = parentField.attributes.destinationName != null ? parentField.attributes.destinationName : refPrefix;
                    embeddedClass = refSchema.findClassByName(parentField.attributes.type);
                }
            }
            if (embeddedClass == null && refSchema != null) {
                embeddedClass = refSchema.findClassByName(layoutRef);
            }

            // IDEntite wrappers: when embedContents=true the exported data
            // contains the TARGET entity's full field set (e.g. Fichier),
            // not the IDEntite wrapper's fields. Resolve through pointsTo
            // to the target class so the layout generates a full section.
            // When embedContents=false the data is a compact {_id, _label}
            // reference — collapse to a single FIELD node.
            if (embeddedClass != null && embeddedClass.isIDEntite()) {
                if (parentField != null && parentField.attributes.embedContents && embeddedClass.attributes.pointsTo != null && refSchema != null) {
                    DOSchemaClass targetClass = refSchema.findClassByName(embeddedClass.attributes.pointsTo);
                    if (targetClass != null) {
                        embeddedClass = targetClass;
                        layoutRef = targetClass.attributes.source;
                        // Fall through to section generation below
                    }
                }
                // Still an IDEntite after resolution attempt — compact reference
                if (embeddedClass.isIDEntite()) {
                    LayoutNode fld = new LayoutNode(LayoutNodeType.FIELD);
                    fld.setProp("ref", destPrefix);
                    String title = node.prop("title");
                    if (title != null && !title.isEmpty())
                        fld.setProp("label", title);
                    // A TAB node must stay as a TAB so the tabbed-section
                    // renderer can use its title for the tab button label.
                    // Wrap the compact field inside a new TAB that preserves
                    // all props (esp. title) from the original node.
                    if (node.type == LayoutNodeType.TAB) {
                        LayoutNode tab = new LayoutNode(LayoutNodeType.TAB);
                        for (Map.Entry<String, String> e : node.properties.entrySet()) {
                            if (!"layoutRef".equals(e.getKey()) && !"ref".equals(e.getKey()))
                                tab.setProp(e.getKey(), e.getValue());
                        }
                        tab.children.add(fld);
                        return tab;
                    }
                    return fld;
                }
            }

            // Cycle detection: if we've already visited this class in the
            // current resolution chain, emit a compact FIELD to avoid
            // infinite recursion (e.g. Prevention → X → Prevention).
            if (visiting.contains(layoutRef)) {
                LayoutNode fld = new LayoutNode(LayoutNodeType.FIELD);
                fld.setProp("ref", destPrefix);
                String title = node.prop("title");
                if (title != null && !title.isEmpty())
                    fld.setProp("label", title);
                return fld;
            }

            DetailLayout refLayout = DOModuleService.getInstance().getClassLayout(layoutRef);
            if (refLayout != null) {
                // Build a node with the referenced layout's nodes inlined, refs prefixed
                LayoutNode resolved = new LayoutNode(node.type);
                for (Map.Entry<String, String> e : node.properties.entrySet()) {
                    if (!"layoutRef".equals(e.getKey()) && !"ref".equals(e.getKey()))
                        resolved.setProp(e.getKey(), e.getValue());
                }
                resolved.setProp("ref", destPrefix);
                visiting.add(layoutRef);
                for (LayoutNode refChild : refLayout.nodes) {
                    resolved.children.add(prefixRefs(resolveNode(refChild, embeddedClass, refSchema, visiting), destPrefix, embeddedClass, refSchema));
                }
                visiting.remove(layoutRef);
                return resolved;
            }
            // No custom layout — auto-generate a flat field list for the embedded class
            if (embeddedClass != null && refSchema != null) {
                LayoutNode resolved = new LayoutNode(node.type);
                for (Map.Entry<String, String> e : node.properties.entrySet()) {
                    if (!"layoutRef".equals(e.getKey()) && !"ref".equals(e.getKey()))
                        resolved.setProp(e.getKey(), e.getValue());
                }
                resolved.setProp("ref", destPrefix);
                visiting.add(layoutRef);
                populateInlineFields(resolved, embeddedClass, destPrefix, refSchema, visiting);
                visiting.remove(layoutRef);
                return resolved;
            }
        }

        // Translate ref and add label for FIELD and TABLE nodes
        if (schemaClass != null && refSchema != null) {
            if (node.type == LayoutNodeType.FIELD || node.type == LayoutNodeType.TABLE) {
                String ref = node.prop("ref");
                if (ref != null) {
                    TranslatedRef tr = translateRefPath(ref, schemaClass, refSchema);
                    LayoutNode copy = new LayoutNode(node.type);
                    copy.properties = new LinkedHashMap<>(node.properties);
                    copy.setProp("ref", tr.destPath);
                    if (copy.prop("label") == null && tr.title != null)
                        copy.setProp("label", tr.title);
                    if (node.type == LayoutNodeType.TABLE) {
                        translateTableColumnsFromProps(copy, tr.resolvedField, refSchema);
                    }
                    for (LayoutNode child : node.children)
                        copy.children.add(resolveNode(child, schemaClass, refSchema, visiting));
                    return copy;
                }
            }
        }

        // Recurse into children
        if (node.children.isEmpty())
            return node;
        LayoutNode copy = new LayoutNode(node.type);
        copy.properties = new LinkedHashMap<>(node.properties);
        for (LayoutNode child : node.children)
            copy.children.add(resolveNode(child, schemaClass, refSchema, visiting));
        return copy;
    }

    /**
     * Recursively mark TABLE nodes as "bare" when they are the sole child of a collapsible SECTION — the section already provides the collapsible header, so the table should not render its own duplicate wrapper.
     */
    private static void markBareTables(List<LayoutNode> nodes) {
        for (LayoutNode node : nodes) {
            if (node.type == LayoutNodeType.SECTION && "true".equals(node.prop("collapsible")) && node.children.size() == 1 && node.children.get(0).type == LayoutNodeType.TABLE) {
                node.children.get(0).setProp("bare", "true");
            }
            if (!node.children.isEmpty())
                markBareTables(node.children);
        }
    }

    /** Translate a dot-separated source name path to destination name path. */
    private static TranslatedRef translateRefPath(String sourcePath, DOSchemaClass rootClass, DOSchema refSchema) {
        String[] parts = sourcePath.split("\\.");
        StringBuilder destPath = new StringBuilder();
        DOSchemaClass current = rootClass;
        DOSchemaField lastField = null;
        String lastTitle = null;

        for (int i = 0; i < parts.length; i++) {
            DOSchemaField field = current != null ? DatabaseUtil.findSchemaFieldByNameIncludingAncestors(current, parts[i], refSchema) : null;
            if (field != null) {
                lastField = field;
                lastTitle = field.attributes.title;
                String dn = field.attributes.destinationName != null ? field.attributes.destinationName : parts[i];
                if (destPath.length() > 0)
                    destPath.append('.');
                destPath.append(dn);
                // Navigate to child class for next segment
                if (i < parts.length - 1) {
                    String nextType = field.attributes.isCollection && field.attributes.childrenType != null ? field.attributes.childrenType : field.attributes.type;
                    current = nextType != null ? refSchema.findClassByName(nextType) : null;
                }
            } else {
                // No match — keep the segment as-is
                if (destPath.length() > 0)
                    destPath.append('.');
                destPath.append(parts[i]);
                current = null;
            }
        }
        return new TranslatedRef(destPath.toString(), lastTitle, lastField);
    }

    private static class TranslatedRef {
        final String destPath;
        final String title;
        final DOSchemaField resolvedField;

        TranslatedRef(String destPath, String title, DOSchemaField resolvedField) {
            this.destPath = destPath;
            this.title = title;
            this.resolvedField = resolvedField;
        }
    }

    /** Translate TABLE column names from source to destination. */
    private static void translateTableColumnsFromProps(LayoutNode tableNode, DOSchemaField collectionField, DOSchema refSchema) {
        String columns = tableNode.prop("columns");
        if (columns == null || columns.isEmpty() || collectionField == null)
            return;
        String childType = collectionField.attributes.childrenType;
        if (childType == null)
            return;
        DOSchemaClass childClass = refSchema.findClassByName(childType);
        if (childClass == null)
            return;

        String[] srcCols = columns.split(",");
        List<String> destCols = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        for (String srcCol : srcCols) {
            String col = srcCol.trim();
            DOSchemaField cf = DatabaseUtil.findSchemaFieldByNameIncludingAncestors(childClass, col, refSchema);
            if (cf != null) {
                destCols.add(cf.attributes.destinationName != null ? cf.attributes.destinationName : col);
                titles.add(cf.attributes.title != null ? cf.attributes.title : "");
            } else {
                destCols.add(col);
                titles.add("");
            }
        }
        tableNode.setProp("columns", String.join(",", destCols));
        if (titles.stream().anyMatch(t -> !t.isEmpty()))
            tableNode.setProp("columnTitles", String.join(",", titles));
    }

    /**
     * Recursively populate a layout node with children for each exported field of the given class. Collections become TABLE nodes, embedded non-primitive fields become collapsible SECTION nodes (with recursive children), and simple fields become FIELD nodes.
     */
    private static void populateInlineFields(LayoutNode parent, DOSchemaClass cls, String prefix, DOSchema refSchema, Set<String> visiting) {
        for (DOSchemaField sf : DatabaseUtil.getAllSchemaFieldsIncludingAncestors(cls, refSchema)) {
            if (!sf.attributes.isExported || sf.attributes.source == null)
                continue;
            String destName = sf.attributes.destinationName != null ? sf.attributes.destinationName : sf.attributes.source;
            if (sf.attributes.isCollection) {
                LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
                table.setProp("ref", prefix + "." + destName);
                if (sf.attributes.title != null && !sf.attributes.title.isEmpty())
                    table.setProp("label", sf.attributes.title);
                translateTableColumns(table, sf, refSchema);
                parent.children.add(table);
            } else if (sf.attributes.embedContents && sf.attributes.type != null && !TypeUtil.isPrimitiveType(sf.attributes.type)) {
                DOSchemaClass nestedClass = refSchema.findClassByName(sf.attributes.type);
                if (nestedClass != null && nestedClass.isIDEntite() && nestedClass.attributes.pointsTo != null) {
                    DOSchemaClass target = refSchema.findClassByName(nestedClass.attributes.pointsTo);
                    if (target != null)
                        nestedClass = target;
                }
                if (nestedClass != null && nestedClass.attributes.migrate && !visiting.contains(nestedClass.attributes.source)) {
                    LayoutNode sub = new LayoutNode(LayoutNodeType.SECTION);
                    sub.setProp("title", sf.attributes.title != null ? sf.attributes.title : destName);
                    sub.setProp("collapsible", "true");
                    String subPrefix = prefix + "." + destName;
                    sub.setProp("ref", subPrefix);
                    visiting.add(nestedClass.attributes.source);
                    populateInlineFields(sub, nestedClass, subPrefix, refSchema, visiting);
                    visiting.remove(nestedClass.attributes.source);
                    parent.children.add(sub);
                }
            } else {
                LayoutNode fld = new LayoutNode(LayoutNodeType.FIELD);
                fld.setProp("ref", prefix + "." + destName);
                if (sf.attributes.title != null && !sf.attributes.title.isEmpty())
                    fld.setProp("label", sf.attributes.title);
                parent.children.add(fld);
            }
        }
    }

    /** Generate table column metadata for a collection field. */
    private static void translateTableColumns(LayoutNode table, DOSchemaField collectionField, DOSchema refSchema) {
        String childType = collectionField.attributes.childrenType;
        if (childType == null || TypeUtil.isPrimitiveType(childType))
            return;
        DOSchemaClass childClass = refSchema.findClassByName(childType);
        if (childClass == null)
            return;
        List<String> colNames = new ArrayList<>();
        List<String> colTitles = new ArrayList<>();
        for (DOSchemaField sf : DatabaseUtil.getAllSchemaFieldsIncludingAncestors(childClass, refSchema)) {
            if (!sf.attributes.isExported || sf.attributes.source == null)
                continue;
            if (sf.attributes.isCollection)
                continue;
            colNames.add(sf.attributes.destinationName != null ? sf.attributes.destinationName : sf.attributes.source);
            colTitles.add(sf.attributes.title != null ? sf.attributes.title : "");
        }
        if (!colNames.isEmpty()) {
            table.setProp("columns", String.join(",", colNames));
            if (colTitles.stream().anyMatch(t -> !t.isEmpty()))
                table.setProp("columnTitles", String.join(",", colTitles));
        }
    }

    private static LayoutNode prefixRefs(LayoutNode node, String prefix, DOSchemaClass embeddedClass, DOSchema refSchema) {
        if (prefix == null || prefix.isEmpty())
            return node;
        LayoutNode copy = new LayoutNode(node.type);
        copy.properties = new LinkedHashMap<>(node.properties);
        // Prefix the ref property for FIELD and TABLE nodes
        String ref = copy.prop("ref");
        if (ref != null && (node.type == LayoutNodeType.FIELD || node.type == LayoutNodeType.TABLE)) {
            copy.setProp("ref", prefix + "." + ref);
        }
        for (LayoutNode child : node.children)
            copy.children.add(prefixRefs(child, prefix, embeddedClass, refSchema));
        return copy;
    }

    // ═══════════════════════════════════════════════════════════════
    // Auto-layout generator — same algorithm as DetailLayoutDesigner
    // ═══════════════════════════════════════════════════════════════

    private static final String[][] GROUP_ORDER = { { "identity", null }, { "text", "Texte" }, { "status", "\u00c9tat" }, { "dates", "Dates" }, { "contact", "Contact" }, { "address", "Adresse" }, { "reference", "R\u00e9f\u00e9rences" }, { "embedded", null }, { "attachment", "Pi\u00e8ces jointes" }, { "collections", null }, { "other", "Autres champs" }, };

    /**
     * Auto-generate a smart layout for a class that has no custom WYSIWYG layout. Groups fields by semantic category (dates, status, references, etc.) and arranges them in columns, tabs, and collapsible sections.
     */
    public static DetailLayout autoGenerate(DOSchemaClass schemaClass, DOSchema refSchema) {
        DetailLayout layout = new DetailLayout();
        if (schemaClass == null || refSchema == null)
            return layout;

        LinkedHashMap<String, List<DOSchemaField>> groups = buildFieldGroups(schemaClass, refSchema);

        // Phase 1: Identity fields
        List<DOSchemaField> identityFields = groups.getOrDefault("identity", Collections.emptyList());
        if (!identityFields.isEmpty()) {
            if (identityFields.size() >= 2)
                layout.nodes.add(agMakeColumnsFromFields(identityFields));
            else
                layout.nodes.add(agMakeFieldNode(identityFields.get(0)));
        }

        // Phase 2: Middle groups — pair small groups, use columns for compactness
        String[] middleGroups = { "text", "status", "dates", "contact", "address", "reference" };
        List<String[]> pendingSections = new ArrayList<>();
        for (String gk : middleGroups) {
            List<DOSchemaField> fields = groups.get(gk);
            if (fields == null || fields.isEmpty())
                continue;
            List<DOSchemaField> flatFields = new ArrayList<>();
            List<DOSchemaField> embeddedFields = new ArrayList<>();
            for (DOSchemaField f : fields) {
                if (f.attributes.embedContents && !TypeUtil.isPrimitiveType(f.attributes.type))
                    embeddedFields.add(f);
                else
                    flatFields.add(f);
            }
            String title = agGroupTitle(gk);
            if (!flatFields.isEmpty())
                pendingSections.add(new String[] { gk, title, "flat" });
            if (!embeddedFields.isEmpty())
                pendingSections.add(new String[] { gk, title, "embedded" });
        }
        int i = 0;
        while (i < pendingSections.size()) {
            String[] entry = pendingSections.get(i);
            String gk = entry[0];
            String title = entry[1];
            boolean isEmbedded = "embedded".equals(entry[2]);
            List<DOSchemaField> fields = groups.get(gk);
            List<DOSchemaField> flatFields = new ArrayList<>();
            List<DOSchemaField> embeddedFields = new ArrayList<>();
            for (DOSchemaField f : fields) {
                if (f.attributes.embedContents && !TypeUtil.isPrimitiveType(f.attributes.type))
                    embeddedFields.add(f);
                else
                    flatFields.add(f);
            }
            if (isEmbedded) {
                for (DOSchemaField f : embeddedFields)
                    layout.nodes.add(agMakeEmbeddedSection(f, refSchema));
                i++;
                continue;
            }
            boolean compactGroup = "dates".equals(gk) || "status".equals(gk) || "reference".equals(gk);
            if (flatFields.size() <= 4 && i + 1 < pendingSections.size()) {
                String[] nextEntry = pendingSections.get(i + 1);
                if ("flat".equals(nextEntry[2])) {
                    String nextGk = nextEntry[0];
                    List<DOSchemaField> nextFlat = new ArrayList<>();
                    for (DOSchemaField f : groups.get(nextGk)) {
                        if (!(f.attributes.embedContents && !TypeUtil.isPrimitiveType(f.attributes.type)))
                            nextFlat.add(f);
                    }
                    if (nextFlat.size() <= 4) {
                        layout.nodes.add(agMakeTwoGroupColumns(title, flatFields, agGroupTitle(nextGk), nextFlat));
                        i += 2;
                        continue;
                    }
                }
            }
            if (compactGroup && flatFields.size() >= 2) {
                LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
                section.setProp("title", title);
                section.setProp("collapsible", "true");
                section.children.add(agMakeColumnsFromFields(flatFields));
                layout.nodes.add(section);
            } else if (flatFields.size() == 1) {
                layout.nodes.add(agMakeFieldNode(flatFields.get(0)));
            } else {
                LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
                section.setProp("title", title);
                section.setProp("collapsible", "true");
                for (DOSchemaField f : flatFields)
                    section.children.add(agMakeFieldNode(f));
                layout.nodes.add(section);
            }
            i++;
        }

        // Phase 3: Embedded objects
        List<DOSchemaField> embeddedGroup = groups.getOrDefault("embedded", Collections.emptyList());
        if (embeddedGroup.size() >= 2) {
            int tabLimit = Math.min(embeddedGroup.size(), 5);
            LayoutNode tabs = new LayoutNode(LayoutNodeType.TABBED_SECTION);
            for (int j = 0; j < tabLimit; j++) {
                DOSchemaField f = embeddedGroup.get(j);
                LayoutNode tab = new LayoutNode(LayoutNodeType.TAB);
                tab.setProp("title", agGetFieldLabel(f));
                agPopulateEmbedded(tab, f, refSchema);
                tabs.children.add(tab);
            }
            layout.nodes.add(tabs);
            for (int j = tabLimit; j < embeddedGroup.size(); j++)
                layout.nodes.add(agMakeEmbeddedSection(embeddedGroup.get(j), refSchema));
        } else if (embeddedGroup.size() == 1) {
            layout.nodes.add(agMakeEmbeddedSection(embeddedGroup.get(0), refSchema));
        }

        // Phase 4: Attachments
        List<DOSchemaField> attachments = groups.getOrDefault("attachment", Collections.emptyList());
        if (!attachments.isEmpty()) {
            LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
            section.setProp("title", "Pi\u00e8ces jointes");
            section.setProp("collapsible", "true");
            for (DOSchemaField f : attachments)
                section.children.add(agMakeFieldNode(f));
            layout.nodes.add(section);
        }

        // Phase 5: Collections
        List<DOSchemaField> collections = groups.getOrDefault("collections", Collections.emptyList());
        if (collections.size() >= 2) {
            if (!layout.nodes.isEmpty())
                layout.nodes.add(new LayoutNode(LayoutNodeType.DIVIDER));
            int tabLimit = Math.min(collections.size(), 5);
            LayoutNode tabs = new LayoutNode(LayoutNodeType.TABBED_SECTION);
            for (int j = 0; j < tabLimit; j++) {
                DOSchemaField f = collections.get(j);
                LayoutNode tab = new LayoutNode(LayoutNodeType.TAB);
                tab.setProp("title", agGetFieldLabel(f));
                LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
                table.setProp("ref", f.attributes.source);
                agAddTableColumns(table, f, refSchema);
                tab.children.add(table);
                tabs.children.add(tab);
            }
            layout.nodes.add(tabs);
            for (int j = tabLimit; j < collections.size(); j++) {
                DOSchemaField f = collections.get(j);
                LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
                section.setProp("title", agGetFieldLabel(f));
                section.setProp("collapsible", "true");
                LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
                table.setProp("ref", f.attributes.source);
                agAddTableColumns(table, f, refSchema);
                section.children.add(table);
                layout.nodes.add(section);
            }
        } else if (collections.size() == 1) {
            if (!layout.nodes.isEmpty())
                layout.nodes.add(new LayoutNode(LayoutNodeType.DIVIDER));
            DOSchemaField f = collections.get(0);
            LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
            section.setProp("title", agGetFieldLabel(f));
            section.setProp("collapsible", "true");
            LayoutNode table = new LayoutNode(LayoutNodeType.TABLE);
            table.setProp("ref", f.attributes.source);
            agAddTableColumns(table, f, refSchema);
            section.children.add(table);
            layout.nodes.add(section);
        }

        // Phase 6: Other (uncategorized)
        List<DOSchemaField> otherFields = groups.getOrDefault("other", Collections.emptyList());
        if (!otherFields.isEmpty()) {
            List<DOSchemaField> otherFlat = new ArrayList<>();
            List<DOSchemaField> otherEmbedded = new ArrayList<>();
            for (DOSchemaField f : otherFields) {
                if (f.attributes.embedContents && !TypeUtil.isPrimitiveType(f.attributes.type))
                    otherEmbedded.add(f);
                else
                    otherFlat.add(f);
            }
            if (!otherFlat.isEmpty()) {
                LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
                section.setProp("title", "Autres champs");
                section.setProp("collapsible", "true");
                if (otherFlat.size() >= 4)
                    section.children.add(agMakeColumnsFromFields(otherFlat));
                else
                    for (DOSchemaField f : otherFlat)
                        section.children.add(agMakeFieldNode(f));
                layout.nodes.add(section);
            }
            for (DOSchemaField f : otherEmbedded)
                layout.nodes.add(agMakeEmbeddedSection(f, refSchema));
        }

        return layout;
    }

    // ── Auto-generate helpers (static, prefixed with ag) ──

    private static LinkedHashMap<String, List<DOSchemaField>> buildFieldGroups(DOSchemaClass schemaClass, DOSchema refSchema) {
        List<DOSchemaField> allFields = DatabaseUtil.getAllSchemaFieldsIncludingAncestors(schemaClass, refSchema);
        LinkedHashMap<String, List<DOSchemaField>> groups = new LinkedHashMap<>();
        for (String[] entry : GROUP_ORDER)
            groups.put(entry[0], new ArrayList<>());
        for (DOSchemaField field : allFields) {
            if (!field.attributes.isExported)
                continue;
            if (field.attributes.source == null || field.attributes.source.isEmpty())
                continue;
            if (field.attributes.embedContents && !TypeUtil.isPrimitiveType(field.attributes.type)) {
                DOSchemaClass typeClass = refSchema.findClassByName(field.attributes.type);
                if (typeClass != null && !typeClass.attributes.migrate)
                    continue;
            }
            String group = agInferGroup(field, refSchema);
            groups.computeIfAbsent(group, k -> new ArrayList<>()).add(field);
        }
        return groups;
    }

    private static String agInferGroup(DOSchemaField field, DOSchema refSchema) {
        if (field.attributes.group != null && !field.attributes.group.isEmpty())
            return field.attributes.group;
        if (field.attributes.isCollection)
            return "collections";
        if (field.attributes.embedContents && !TypeUtil.isPrimitiveType(field.attributes.type))
            return "embedded";
        if (field.attributes.type != null) {
            switch (field.attributes.type) {
            case "date":
            case "java.util.Date":
            case "java.sql.Timestamp":
                return "dates";
            case "boolean":
            case "java.lang.Boolean":
                return "status";
            }
            if (field.attributes.type != null) {
                DOSchemaClass typeClass = refSchema.findClassByName(field.attributes.type);
                if (typeClass != null && typeClass.isIDEntite())
                    return "reference";
            }
        }
        if ("long".equals(field.attributes.type) || "java.lang.Long".equals(field.attributes.type)) {
            String name = field.attributes.source != null ? field.attributes.source.toLowerCase() : "";
            if (name.contains("date") || name.contains("modification") || name.contains("creation") || name.contains("debut") || name.contains("fin") || name.contains("echeance"))
                return "dates";
        }
        return "other";
    }

    private static LayoutNode agMakeFieldNode(DOSchemaField field) {
        LayoutNode node = new LayoutNode(LayoutNodeType.FIELD);
        node.setProp("ref", field.attributes.source);
        agApplyAutoFormat(node, field);
        return node;
    }

    private static LayoutNode agMakeColumnsFromFields(List<DOSchemaField> fields) {
        LayoutNode cols = new LayoutNode(LayoutNodeType.COLUMNS);
        cols.setProp("count", "2");
        cols.setProp("sizes", "50,50");
        LayoutNode left = new LayoutNode(LayoutNodeType.COLUMN);
        LayoutNode right = new LayoutNode(LayoutNodeType.COLUMN);
        int half = (fields.size() + 1) / 2;
        for (int j = 0; j < fields.size(); j++)
            (j < half ? left : right).children.add(agMakeFieldNode(fields.get(j)));
        cols.children.add(left);
        cols.children.add(right);
        return cols;
    }

    private static LayoutNode agMakeTwoGroupColumns(String titleA, List<DOSchemaField> fieldsA, String titleB, List<DOSchemaField> fieldsB) {
        LayoutNode cols = new LayoutNode(LayoutNodeType.COLUMNS);
        cols.setProp("count", "2");
        cols.setProp("sizes", "50,50");
        LayoutNode colA = new LayoutNode(LayoutNodeType.COLUMN);
        LayoutNode colB = new LayoutNode(LayoutNodeType.COLUMN);
        LayoutNode secA = new LayoutNode(LayoutNodeType.SECTION);
        secA.setProp("title", titleA);
        for (DOSchemaField f : fieldsA)
            secA.children.add(agMakeFieldNode(f));
        colA.children.add(secA);
        LayoutNode secB = new LayoutNode(LayoutNodeType.SECTION);
        secB.setProp("title", titleB);
        for (DOSchemaField f : fieldsB)
            secB.children.add(agMakeFieldNode(f));
        colB.children.add(secB);
        cols.children.add(colA);
        cols.children.add(colB);
        return cols;
    }

    private static LayoutNode agMakeEmbeddedSection(DOSchemaField field, DOSchema refSchema) {
        LayoutNode section = new LayoutNode(LayoutNodeType.SECTION);
        section.setProp("title", agGetFieldLabel(field));
        section.setProp("collapsible", "true");
        agPopulateEmbedded(section, field, refSchema);
        return section;
    }

    private static void agPopulateEmbedded(LayoutNode parent, DOSchemaField field, DOSchema refSchema) {
        DOSchemaClass embeddedClass = refSchema.findClassByName(field.attributes.type);
        if (embeddedClass != null) {
            parent.setProp("layoutRef", embeddedClass.attributes.source);
            parent.setProp("ref", field.attributes.source);
        }
    }

    private static String agGroupTitle(String groupKey) {
        for (String[] entry : GROUP_ORDER) {
            if (entry[0].equals(groupKey))
                return entry[1] != null ? entry[1] : agHumanize(groupKey);
        }
        return agHumanize(groupKey);
    }

    private static String agGetFieldLabel(DOSchemaField field) {
        if (field == null)
            return "";
        if (field.attributes.title != null && !field.attributes.title.trim().isEmpty())
            return field.attributes.title.trim();
        if (field.attributes.destinationName != null && !field.attributes.destinationName.trim().isEmpty())
            return agHumanize(field.attributes.destinationName.trim());
        if (field.attributes.source != null && !field.attributes.source.trim().isEmpty())
            return agHumanize(field.attributes.source.trim());
        return "";
    }

    private static String agHumanize(String s) {
        if (s == null || s.isEmpty())
            return "";
        int dot = s.lastIndexOf('.');
        if (dot >= 0)
            s = s.substring(dot + 1);
        StringBuilder sb = new StringBuilder();
        for (int idx = 0; idx < s.length(); idx++) {
            char c = s.charAt(idx);
            if (idx > 0 && Character.isUpperCase(c))
                sb.append(' ');
            sb.append(idx == 0 ? Character.toUpperCase(c) : c);
        }
        return sb.toString();
    }

    private static void agApplyAutoFormat(LayoutNode node, DOSchemaField field) {
        if (field.attributes.type == null)
            return;
        switch (field.attributes.type) {
        case "date":
        case "java.util.Date":
        case "java.sql.Timestamp":
            node.setProp("format", "date:yyyy-MM-dd");
            break;
        case "boolean":
        case "java.lang.Boolean":
            node.setProp("format", "bool:Oui,Non");
            break;
        case "long":
        case "java.lang.Long":
            String name = field.attributes.source != null ? field.attributes.source.toLowerCase() : "";
            if (name.contains("date") || name.contains("modification") || name.contains("creation") || name.contains("debut") || name.contains("fin") || name.contains("echeance"))
                node.setProp("format", "longdate:yyyy-MM-dd HH:mm");
            break;
        }
    }

    private static void agAddTableColumns(LayoutNode table, DOSchemaField collectionField, DOSchema refSchema) {
        String childType = collectionField.attributes.childrenType;
        if (childType == null || TypeUtil.isPrimitiveType(childType))
            return;
        DOSchemaClass childClass = refSchema.findClassByName(childType);
        if (childClass == null)
            return;
        List<String> colNames = new ArrayList<>();
        List<String> colTitles = new ArrayList<>();
        for (DOSchemaField sf : DatabaseUtil.getAllSchemaFieldsIncludingAncestors(childClass, refSchema)) {
            if (!sf.attributes.isExported || sf.attributes.source == null)
                continue;
            if (sf.attributes.isCollection)
                continue;
            colNames.add(sf.attributes.source);
            colTitles.add(sf.attributes.title != null ? sf.attributes.title : "");
        }
        if (!colNames.isEmpty()) {
            table.setProp("columns", String.join(",", colNames));
            if (colTitles.stream().anyMatch(t -> t != null && !t.isBlank()))
                table.setProp("columnTitles", String.join(",", colTitles));
        }
    }
}
