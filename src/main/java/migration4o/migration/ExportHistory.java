package migration4o.migration;

import java.io.*;
import java.util.*;

/**
 * Manages persistence of export operation parameters to enable re-running the
 * last export.
 */
public class ExportHistory {
    private static final String HISTORY_FILE = "local/.export-history.properties";

    // Property name constants
    private static final String PROP_TYPE = "export.type";
    private static final String PROP_TARGET = "export.target";
    private static final String PROP_OUTPUT = "export.output";
    private static final String PROP_TIMESTAMP = "export.timestamp";
    private static final String PROP_CLASS_NAMES = "export.classNames";
    private static final String PROP_MODULE_NAMES = "export.moduleNames";
    private static final String PROP_MAX_OBJECTS_PER_CLASS = "export.maxObjectsPerClass";
    private static final String PROP_EXPORT_NATIVE_IDS = "export.exportNativeIds";
    private static final String PROP_OUTPUT_FORMAT = "export.outputFormat";
    private static final String PROP_APPLY_USER_SELECTED_FIELD_EXCLUSIONS = "export.applyUserSelectedFieldExclusions";
    private static final String PROP_APPLY_SKIP_WHEN_CONDITIONS = "export.applySkipWhenConditions";
    private static final String PROP_APPLY_EXPORT_CRITERIA_FILTERS = "export.applyExportCriteriaFilters";
    private static final String PROP_SKIP_OBJECTS_WITHOUT_EXPORTABLE_FIELDS = "export.skipObjectsWithoutExportableFields";
    private static final String PROP_FULL_TRACKING = "export.fullTracking";

    public enum ExportType {
        CLASS, MODULE
    }

    /**
     * Saves export parameters for later replay.
     */
    public static void saveExport(ExportType type, String targetName, String outputPath, List<String> classNames) {
        saveExport(type, targetName, outputPath, classNames, null, null, false, ExportOutputOption.XML_XSD);
    }

    /**
     * Saves export parameters for bulk module export.
     */
    public static void saveExport(ExportType type, String targetName, String outputPath, List<String> classNames, List<String> moduleNames) {
        saveExport(type, targetName, outputPath, classNames, moduleNames, null, false, ExportOutputOption.XML_XSD);
    }

    /**
     * Saves export parameters with object limit for bulk module export.
     */
    public static void saveExport(ExportType type, String targetName, String outputPath, List<String> classNames, List<String> moduleNames, Integer maxObjectsPerClass, boolean exportNativeIds) {
        saveExport(type, targetName, outputPath, classNames, moduleNames, maxObjectsPerClass, exportNativeIds, ExportOutputOption.XML_XSD);
    }

    /**
     * Saves export parameters with object limit and output format for bulk module
     * export.
     */
    public static void saveExport(ExportType type, String targetName, String outputPath, List<String> classNames, List<String> moduleNames, Integer maxObjectsPerClass, boolean exportNativeIds, String outputFormat) {
        saveExport(type, targetName, outputPath, classNames, moduleNames, maxObjectsPerClass, exportNativeIds, outputFormat, true, true, true, true);
    }

    /**
     * Saves export parameters with conditional exclusion settings.
     */
    public static void saveExport(ExportType type, String targetName, String outputPath, List<String> classNames, List<String> moduleNames, Integer maxObjectsPerClass, boolean exportNativeIds, String outputFormat, boolean applyUserSelectedFieldExclusions, boolean applySkipWhenConditions, boolean applyExportCriteriaFilters, boolean skipObjectsWithoutExportableFields) {
        saveExport(type, targetName, outputPath, classNames, moduleNames, maxObjectsPerClass, exportNativeIds, outputFormat, applyUserSelectedFieldExclusions, applySkipWhenConditions, applyExportCriteriaFilters, skipObjectsWithoutExportableFields, true);
    }

    /**
     * Saves export parameters with all settings including full-tracking flag.
     */
    public static void saveExport(ExportType type, String targetName, String outputPath, List<String> classNames, List<String> moduleNames, Integer maxObjectsPerClass, boolean exportNativeIds, String outputFormat, boolean applyUserSelectedFieldExclusions, boolean applySkipWhenConditions, boolean applyExportCriteriaFilters, boolean skipObjectsWithoutExportableFields, boolean fullTracking) {
        Properties props = new Properties();
        props.setProperty(PROP_TYPE, type.name());
        props.setProperty(PROP_TARGET, targetName);
        props.setProperty(PROP_OUTPUT, outputPath);
        props.setProperty(PROP_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

        if (type == ExportType.MODULE && classNames != null) {
            props.setProperty(PROP_CLASS_NAMES, String.join(",", classNames));
        }

        if (moduleNames != null && !moduleNames.isEmpty()) {
            props.setProperty(PROP_MODULE_NAMES, String.join(",", moduleNames));
        }

        if (maxObjectsPerClass != null) {
            props.setProperty(PROP_MAX_OBJECTS_PER_CLASS, String.valueOf(maxObjectsPerClass));
        }

        props.setProperty(PROP_EXPORT_NATIVE_IDS, String.valueOf(exportNativeIds));
        props.setProperty(PROP_OUTPUT_FORMAT, (outputFormat != null && !outputFormat.isBlank()) ? outputFormat : ExportOutputOption.XML_XSD);
        props.setProperty(PROP_APPLY_USER_SELECTED_FIELD_EXCLUSIONS, String.valueOf(applyUserSelectedFieldExclusions));
        props.setProperty(PROP_APPLY_SKIP_WHEN_CONDITIONS, String.valueOf(applySkipWhenConditions));
        props.setProperty(PROP_APPLY_EXPORT_CRITERIA_FILTERS, String.valueOf(applyExportCriteriaFilters));
        props.setProperty(PROP_SKIP_OBJECTS_WITHOUT_EXPORTABLE_FIELDS, String.valueOf(skipObjectsWithoutExportableFields));
        props.setProperty(PROP_FULL_TRACKING, String.valueOf(fullTracking));

        try (FileWriter writer = new FileWriter(HISTORY_FILE)) {
            props.store(writer, "Last export operation - automatically generated");
        } catch (IOException e) {
            // Silently ignore export history save errors
        }
    }

    /**
     * Loads the last export parameters, or null if none exist.
     */
    public static ExportParams loadLastExport() {
        File file = new File(HISTORY_FILE);
        if (!file.exists()) {
            return null;
        }

        Properties props = new Properties();
        try (FileReader reader = new FileReader(HISTORY_FILE)) {
            props.load(reader);

            ExportType type = ExportType.valueOf(props.getProperty(PROP_TYPE));
            String target = props.getProperty(PROP_TARGET);
            String output = props.getProperty(PROP_OUTPUT);
            String timestamp = props.getProperty(PROP_TIMESTAMP);
            List<String> classNames = null;
            List<String> moduleNames = null;
            Integer maxObjectsPerClass = null;
            String outputFormat = props.getProperty(PROP_OUTPUT_FORMAT, ExportOutputOption.XML_XSD);
            boolean applyUserSelectedFieldExclusions = Boolean.parseBoolean(props.getProperty(PROP_APPLY_USER_SELECTED_FIELD_EXCLUSIONS, "true"));
            boolean applySkipWhenConditions = Boolean.parseBoolean(props.getProperty(PROP_APPLY_SKIP_WHEN_CONDITIONS, "true"));
            boolean applyExportCriteriaFilters = Boolean.parseBoolean(props.getProperty(PROP_APPLY_EXPORT_CRITERIA_FILTERS, "true"));
            boolean skipObjectsWithoutExportableFields = Boolean.parseBoolean(props.getProperty(PROP_SKIP_OBJECTS_WITHOUT_EXPORTABLE_FIELDS, "true"));
            boolean fullTracking = Boolean.parseBoolean(props.getProperty(PROP_FULL_TRACKING, "true"));

            if (type == ExportType.MODULE) {
                String classNamesStr = props.getProperty(PROP_CLASS_NAMES);
                if (classNamesStr != null && !classNamesStr.isEmpty()) {
                    classNames = Arrays.asList(classNamesStr.split(","));
                }

                String moduleNamesStr = props.getProperty(PROP_MODULE_NAMES);
                if (moduleNamesStr != null && !moduleNamesStr.isEmpty()) {
                    moduleNames = Arrays.asList(moduleNamesStr.split(","));
                }
            }

            String maxObjectsStr = props.getProperty(PROP_MAX_OBJECTS_PER_CLASS);
            if (maxObjectsStr != null && !maxObjectsStr.isEmpty()) {
                try {
                    maxObjectsPerClass = Integer.parseInt(maxObjectsStr);
                } catch (NumberFormatException e) {
                    // Ignore invalid values
                }
            }

            boolean exportNativeIds = Boolean.parseBoolean(props.getProperty(PROP_EXPORT_NATIVE_IDS, "false"));

            return new ExportParams(type, target, output, classNames, moduleNames, timestamp != null ? Long.parseLong(timestamp) : 0, maxObjectsPerClass, exportNativeIds, outputFormat, applyUserSelectedFieldExclusions, applySkipWhenConditions, applyExportCriteriaFilters, skipObjectsWithoutExportableFields, fullTracking);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Warning: Could not load export history: " + e.getMessage());
            return null;
        }
    }

    /**
     * Checks if there is a saved export history.
     */
    public static boolean hasHistory() {
        return new File(HISTORY_FILE).exists();
    }

    /**
     * Container for export parameters.
     */
    public static class ExportParams {
        public final ExportType type;
        public final String targetName;
        public final String outputPath;
        public final List<String> classNames;
        public final List<String> moduleNames;
        public final long timestamp;
        public final Integer maxObjectsPerClass; // null = all objects
        public final boolean exportNativeIds; // whether to export DB4O object IDs
        public final String outputFormat; // selected output format (XML, EXCEL, ...)
        public final boolean applyUserSelectedFieldExclusions;
        public final boolean applySkipWhenConditions;
        public final boolean applyExportCriteriaFilters;
        public final boolean skipObjectsWithoutExportableFields;
        /** Whether full tracking & analysis was enabled for this export. */
        public final boolean fullTracking;

        public ExportParams(ExportType type, String targetName, String outputPath, List<String> classNames, List<String> moduleNames, long timestamp, Integer maxObjectsPerClass, boolean exportNativeIds, String outputFormat, boolean applyUserSelectedFieldExclusions, boolean applySkipWhenConditions, boolean applyExportCriteriaFilters, boolean skipObjectsWithoutExportableFields) {
            this(type, targetName, outputPath, classNames, moduleNames, timestamp, maxObjectsPerClass, exportNativeIds, outputFormat, applyUserSelectedFieldExclusions, applySkipWhenConditions, applyExportCriteriaFilters, skipObjectsWithoutExportableFields, true);
        }

        public ExportParams(ExportType type, String targetName, String outputPath, List<String> classNames, List<String> moduleNames, long timestamp, Integer maxObjectsPerClass, boolean exportNativeIds, String outputFormat, boolean applyUserSelectedFieldExclusions, boolean applySkipWhenConditions, boolean applyExportCriteriaFilters, boolean skipObjectsWithoutExportableFields, boolean fullTracking) {
            this.type = type;
            this.targetName = targetName;
            this.outputPath = outputPath;
            this.classNames = classNames;
            this.moduleNames = moduleNames;
            this.timestamp = timestamp;
            this.maxObjectsPerClass = maxObjectsPerClass;
            this.exportNativeIds = exportNativeIds;
            this.outputFormat = (outputFormat != null && !outputFormat.isBlank()) ? outputFormat : ExportOutputOption.XML_XSD;
            this.applyUserSelectedFieldExclusions = applyUserSelectedFieldExclusions;
            this.applySkipWhenConditions = applySkipWhenConditions;
            this.applyExportCriteriaFilters = applyExportCriteriaFilters;
            this.skipObjectsWithoutExportableFields = skipObjectsWithoutExportableFields;
            this.fullTracking = fullTracking;
        }

        public String getDescription() {
            if (type == ExportType.CLASS) {
                return "Export class '" + targetName + "' to " + outputPath;
            } else {
                String baseDesc;
                if (moduleNames != null && moduleNames.size() > 1) {
                    baseDesc = "Export " + moduleNames.size() + " modules to " + outputPath;
                } else {
                    baseDesc = "Export module '" + targetName + "' (" + (classNames != null ? classNames.size() : 0) + " classes) to " + outputPath;
                }

                if (maxObjectsPerClass != null) {
                    baseDesc += " (max " + maxObjectsPerClass + " objects per class)";
                }

                return baseDesc;
            }
        }

        public String getFormattedTimestamp() {
            return new Date(timestamp).toString();
        }
    }
}
