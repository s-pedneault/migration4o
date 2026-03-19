package migration4o.schema.diagram;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.schema.DOSchemaReference;
import migration4o.models.schema.DOSchemaModule;
import migration4o.models.ui.ClassExportConfig;
import migration4o.util.TypeUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * Exports a visual class-reference mapping of the reference schema to Graphviz DOT and optionally renders an SVG when Graphviz is available.
 */
public class SchemaDiagramExporter {

    public static class Result {
        public final Path dotFile;
        public final Path svgFile;
        public final boolean svgGenerated;
        public final String statusMessage;

        public Result(Path dotFile, Path svgFile, boolean svgGenerated, String statusMessage) {
            this.dotFile = dotFile;
            this.svgFile = svgFile;
            this.svgGenerated = svgGenerated;
            this.statusMessage = statusMessage;
        }
    }

    private static final int MAX_FIELDS_IN_LABEL = 8;

    public Result export(DOSchema schema, Path outputDirectory, String baseFileName) throws IOException, InterruptedException {
        return export(schema, outputDirectory, baseFileName, null);
    }

    public List<Result> exportPerModule(DOSchema schema, List<DOSchemaModule> modules, Path outputDirectory) throws IOException, InterruptedException {
        if (modules == null || modules.isEmpty()) {
            return List.of();
        }

        List<Result> results = new ArrayList<>();
        for (DOSchemaModule module : modules) {
            exportModuleRecursive(schema, module, outputDirectory, new ArrayList<>(), results);
        }

        return results;
    }

    private void exportModuleRecursive(DOSchema schema, DOSchemaModule module, Path outputDirectory, List<String> ancestry, List<Result> results) throws IOException, InterruptedException {
        if (module == null) {
            return;
        }

        List<String> currentPath = new ArrayList<>(ancestry);
        currentPath.add(module.name != null ? module.name : module.id);

        Set<String> requestedClassNames = new LinkedHashSet<>();
        for (ClassExportConfig config : module.classConfigs) {
            if (config != null && config.getClassName() != null && !config.getClassName().isBlank()) {
                requestedClassNames.add(config.getClassName());
            }
        }

        if (!requestedClassNames.isEmpty()) {
            String baseName = "module-" + sanitizeModulePath(currentPath);
            Result result = export(schema, outputDirectory, baseName, requestedClassNames);
            results.add(result);
        }

        for (DOSchemaModule child : module.children) {
            exportModuleRecursive(schema, child, outputDirectory, currentPath, results);
        }
    }

    private Result export(DOSchema schema, Path outputDirectory, String baseFileName, Set<String> classNameFilter) throws IOException, InterruptedException {
        if (schema == null || schema.getClasses() == null) {
            throw new IllegalArgumentException("Schema is null or has no classes");
        }

        Files.createDirectories(outputDirectory);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        String effectiveBaseName = (baseFileName == null || baseFileName.isBlank()) ? "reference-schema-diagram-" + timestamp : baseFileName;

        Path dotFile = outputDirectory.resolve(effectiveBaseName + ".dot");
        Path svgFile = outputDirectory.resolve(effectiveBaseName + ".svg");

        String dotContent = buildDot(schema, classNameFilter);
        Files.writeString(dotFile, dotContent, StandardCharsets.UTF_8);

        boolean svgGenerated = false;
        String message;

        if (GraphvizRunner.isDotAvailable()) {
            svgGenerated = GraphvizRunner.renderSvg(dotFile, svgFile);
            if (svgGenerated) {
                message = "Diagram generated successfully.";
            } else {
                message = "DOT generated, but SVG rendering failed.";
            }
        } else {
            message = "DOT generated. Install Graphviz (dot) to render SVG automatically.";
        }

        return new Result(dotFile, svgFile, svgGenerated, message);
    }

    private String buildDot(DOSchema schema, Set<String> classNameFilter) {
        StringBuilder dot = new StringBuilder();
        dot.append("digraph ReferenceSchema {\n");
        dot.append("  rankdir=TB;\n");
        dot.append("  graph [fontname=\"Arial\", fontsize=10, splines=polyline, overlap=false, concentrate=true, ranksep=0.6, nodesep=0.25, pad=0.15];\n");
        dot.append("  node [shape=record, fontname=\"Arial\", fontsize=9, style=\"rounded,filled\", fillcolor=\"#F7FBFF\", color=\"#A0A0A0\"];\n");
        dot.append("  edge [fontname=\"Arial\", fontsize=8, color=\"#808080\"];\n\n");

        List<DOSchemaClass> allClasses = new ArrayList<>();
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass != null && schemaClass.attributes.source != null && !schemaClass.attributes.source.isBlank()) {
                allClasses.add(schemaClass);
            }
        }
        allClasses.sort(Comparator.comparing(c -> c.attributes.source));

        Map<String, DOSchemaClass> allClassMap = new HashMap<>();
        for (DOSchemaClass schemaClass : allClasses) {
            allClassMap.put(schemaClass.attributes.source, schemaClass);
        }

        Set<String> canonicalFilter = resolveClassFilter(classNameFilter, allClassMap);

        Map<String, Integer> childCountByClass = new HashMap<>();
        for (DOSchemaClass schemaClass : allClasses) {
            childCountByClass.put(schemaClass.attributes.source, 0);
        }
        for (DOSchemaClass schemaClass : allClasses) {
            if (schemaClass.attributes.parentClassName == null || schemaClass.attributes.parentClassName.isBlank()) {
                continue;
            }
            DOSchemaClass parent = findByName(allClassMap, schemaClass.attributes.parentClassName);
            if (parent != null) {
                childCountByClass.put(parent.attributes.source, childCountByClass.getOrDefault(parent.attributes.source, 0) + 1);
            }
        }

        List<DOSchemaClass> classes = new ArrayList<>();
        for (DOSchemaClass schemaClass : allClasses) {
            if (!canonicalFilter.isEmpty() && !canonicalFilter.contains(schemaClass.attributes.source)) {
                continue;
            }
            boolean isLeaf = childCountByClass.getOrDefault(schemaClass.attributes.source, 0) == 0;
            boolean isIDPointer = schemaClass.isIDEntite();
            if (isLeaf && !isIDPointer) {
                classes.add(schemaClass);
            }
        }
        classes.sort(Comparator.comparing(c -> c.attributes.source));

        Set<String> includedNodeNames = new HashSet<>();
        for (DOSchemaClass schemaClass : classes) {
            includedNodeNames.add(schemaClass.attributes.source);
        }

        for (DOSchemaClass schemaClass : classes) {
            dot.append("  \"").append(escapeDot(schemaClass.attributes.source)).append("\"");
            dot.append(" [label=\"").append(buildClassLabel(schemaClass, allClassMap, includedNodeNames, schema)).append("\"];\n");
        }

        dot.append("\n");

        Set<String> emittedEdges = new HashSet<>();

        for (DOSchemaClass schemaClass : classes) {
            String source = schemaClass.attributes.source;

            if (schemaClass.schemaReferences != null) {
                for (DOSchemaReference reference : schemaClass.schemaReferences) {
                    if (reference == null || reference.className == null || reference.className.isBlank()) {
                        continue;
                    }
                    DOSchemaClass refSource = resolveGraphClass(allClassMap, schema, reference.className);
                    DOSchemaClass refTarget = resolveGraphClass(allClassMap, schema, source);
                    if (refSource != null && refTarget != null && includedNodeNames.contains(refSource.attributes.source) && includedNodeNames.contains(refTarget.attributes.source) && !refSource.attributes.source.equals(refTarget.attributes.source)) {
                        String referenceField = getDisplayFieldName(schemaClass, reference.fieldName);
                        String label = referenceField != null && !referenceField.isBlank() ? "ref:" + referenceField : "reference";
                        addEdge(dot, emittedEdges, refSource.attributes.source, refTarget.attributes.source, label, "#F58518", "normal", "solid", 1);
                    }
                }
            }

            if (schemaClass.fields != null) {
                for (DOSchemaField field : schemaClass.fields) {
                    if (field == null) {
                        continue;
                    }

                    addFieldTypeEdge(dot, emittedEdges, allClassMap, includedNodeNames, schema, source, field, field.attributes.type, false);
                    addFieldTypeEdge(dot, emittedEdges, allClassMap, includedNodeNames, schema, source, field, field.attributes.childrenType, true);
                }
            }
        }

        dot.append("}\n");
        return dot.toString();
    }

    private Set<String> resolveClassFilter(Set<String> requestedClassNames, Map<String, DOSchemaClass> classMap) {
        Set<String> resolved = new LinkedHashSet<>();
        if (requestedClassNames == null || requestedClassNames.isEmpty()) {
            return resolved;
        }

        for (String className : requestedClassNames) {
            DOSchemaClass schemaClass = findByName(classMap, className);
            if (schemaClass != null && schemaClass.attributes.source != null) {
                resolved.add(schemaClass.attributes.source);
            }
        }

        return resolved;
    }

    private void addFieldTypeEdge(StringBuilder dot, Set<String> emittedEdges, Map<String, DOSchemaClass> classMap, Set<String> includedNodeNames, DOSchema schema, String sourceClassName, DOSchemaField field, String rawTypeName, boolean childrenType) {
        if (rawTypeName == null || rawTypeName.isBlank() || TypeUtil.isPrimitiveType(rawTypeName)) {
            return;
        }

        DOSchemaClass target = resolveGraphClass(classMap, schema, rawTypeName);
        if (target == null || sourceClassName.equals(target.attributes.source) || !includedNodeNames.contains(target.attributes.source)) {
            return;
        }

        String kind = childrenType ? "children" : "type";
        String safeField = getDisplayFieldName(field);
        if (safeField == null || safeField.isBlank()) {
            safeField = "field";
        }
        String targetName = getDisplayClassName(target);
        String label = kind + ":" + safeField + " -> " + targetName;

        addEdge(dot, emittedEdges, sourceClassName, target.attributes.source, label, "#9C755F", "normal", "dotted", 1);
    }

    private DOSchemaClass resolveGraphClass(Map<String, DOSchemaClass> classMap, DOSchema schema, String className) {
        DOSchemaClass resolved = findByName(classMap, className);
        if (resolved == null) {
            return null;
        }

        if (resolved.isIDEntite() && resolved.attributes.pointsTo != null && !resolved.attributes.pointsTo.isBlank()) {
            DOSchemaClass pointed = findByName(classMap, resolved.attributes.pointsTo);
            if (pointed != null) {
                return pointed;
            }
        }

        return resolved;
    }

    private DOSchemaClass findByName(Map<String, DOSchemaClass> classMap, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        DOSchemaClass direct = classMap.get(name);
        if (direct != null) {
            return direct;
        }

        if (name.contains(".")) {
            String shortName = name.substring(name.lastIndexOf('.') + 1);
            for (Map.Entry<String, DOSchemaClass> entry : classMap.entrySet()) {
                String key = entry.getKey();
                if (key.endsWith("." + shortName) || key.equals(shortName)) {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    private void addEdge(StringBuilder dot, Set<String> emittedEdges, String from, String to, String label, String color, String arrowHead, String style, int penWidth) {
        String key = from + "->" + to + "|" + label + "|" + style;
        if (emittedEdges.contains(key)) {
            return;
        }
        emittedEdges.add(key);

        dot.append("  \"").append(escapeDot(from)).append("\" -> \"").append(escapeDot(to)).append("\"").append(" [label=\"").append(escapeDot(label)).append("\"").append(", color=\"").append(color).append("\"").append(", arrowhead=\"").append(arrowHead).append("\"").append(", style=\"").append(style).append("\"").append(", penwidth=").append(penWidth).append("];\n");
    }

    private String buildClassLabel(DOSchemaClass schemaClass, Map<String, DOSchemaClass> classMap, Set<String> includedNodeNames, DOSchema schema) {
        String classTitle = getDisplayClassName(schemaClass);

        StringBuilder label = new StringBuilder();
        label.append("{").append(escapeRecord(classTitle));

        List<String> fieldLines = new ArrayList<>();
        if (schemaClass.fields != null) {
            for (DOSchemaField field : schemaClass.fields) {
                if (field == null) {
                    continue;
                }
                String relationType = resolveDisplayRelationType(classMap, includedNodeNames, schema, field);
                if (relationType == null) {
                    continue;
                }

                String fieldName = getDisplayFieldName(field);
                if (fieldName == null || fieldName.isBlank()) {
                    continue;
                }

                fieldLines.add(fieldName + " : " + relationType);
            }
        }

        if (!fieldLines.isEmpty()) {
            label.append("|");
            int count = 0;
            for (String fieldLine : fieldLines) {
                if (count >= MAX_FIELDS_IN_LABEL) {
                    int remaining = fieldLines.size() - MAX_FIELDS_IN_LABEL;
                    label.append(escapeRecord("... (" + remaining + " more)")).append("\\l");
                    break;
                }
                label.append(escapeRecord(fieldLine)).append("\\l");
                count++;
            }
        }

        label.append("}");
        return label.toString();
    }

    private String getSimpleName(String className) {
        int idx = className.lastIndexOf('.');
        return idx >= 0 ? className.substring(idx + 1) : className;
    }

    private String getDisplayClassName(DOSchemaClass schemaClass) {
        if (schemaClass == null) {
            return "Unknown";
        }
        if (schemaClass.attributes.destinationName != null && !schemaClass.attributes.destinationName.isBlank()) {
            return schemaClass.attributes.destinationName;
        }
        return getSimpleName(schemaClass.attributes.source);
    }

    private String getDisplayFieldName(DOSchemaField field) {
        if (field == null) {
            return null;
        }
        if (field.attributes.destinationName != null && !field.attributes.destinationName.isBlank()) {
            return field.attributes.destinationName;
        }
        return field.attributes.source;
    }

    private String getDisplayFieldName(DOSchemaClass schemaClass, String sourceFieldName) {
        if (sourceFieldName == null || sourceFieldName.isBlank()) {
            return null;
        }
        if (schemaClass != null && schemaClass.fields != null) {
            for (DOSchemaField field : schemaClass.fields) {
                if (field == null) {
                    continue;
                }
                if (sourceFieldName.equals(field.attributes.source) || sourceFieldName.equals(field.attributes.destinationName)) {
                    String display = getDisplayFieldName(field);
                    if (display != null && !display.isBlank()) {
                        return display;
                    }
                }
            }
        }
        return sourceFieldName;
    }

    private String resolveDisplayRelationType(Map<String, DOSchemaClass> classMap, Set<String> includedNodeNames, DOSchema schema, DOSchemaField field) {
        DOSchemaClass childrenTarget = resolveRelationTarget(classMap, schema, field != null ? field.attributes.childrenType : null);
        if (childrenTarget != null && includedNodeNames.contains(childrenTarget.attributes.source)) {
            return getDisplayClassName(childrenTarget);
        }

        DOSchemaClass typeTarget = resolveRelationTarget(classMap, schema, field != null ? field.attributes.type : null);
        if (typeTarget != null && includedNodeNames.contains(typeTarget.attributes.source)) {
            return getDisplayClassName(typeTarget);
        }

        return null;
    }

    private DOSchemaClass resolveRelationTarget(Map<String, DOSchemaClass> classMap, DOSchema schema, String rawTypeName) {
        if (rawTypeName == null || rawTypeName.isBlank() || TypeUtil.isPrimitiveType(rawTypeName)) {
            return null;
        }
        return resolveGraphClass(classMap, schema, rawTypeName);
    }

    private String escapeDot(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeRecord(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("{", "\\{").replace("}", "\\}").replace("|", "\\|").replace("<", "\\<").replace(">", "\\>");
    }

    private String sanitizeFileName(String input) {
        if (input == null || input.isBlank()) {
            return "module";
        }
        return input.toLowerCase().replaceAll("[^a-z0-9._-]+", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
    }

    private String sanitizeModulePath(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return "module";
        }

        List<String> sanitized = new ArrayList<>();
        for (String part : parts) {
            sanitized.add(sanitizeFileName(part));
        }
        return String.join("__", sanitized);
    }
}
