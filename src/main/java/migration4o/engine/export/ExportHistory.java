package migration4o.engine.export;

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

    public enum ExportType {
        CLASS, MODULE
    }

    /**
     * Saves export parameters for later replay.
     */
    public static void saveExport(ExportType type, String targetName, String outputPath,
            List<String> classNames) {
        Properties props = new Properties();
        props.setProperty(PROP_TYPE, type.name());
        props.setProperty(PROP_TARGET, targetName);
        props.setProperty(PROP_OUTPUT, outputPath);
        props.setProperty(PROP_TIMESTAMP, String.valueOf(System.currentTimeMillis()));

        if (type == ExportType.MODULE && classNames != null) {
            props.setProperty(PROP_CLASS_NAMES, String.join(",", classNames));
        }

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

            if (type == ExportType.MODULE) {
                String classNamesStr = props.getProperty(PROP_CLASS_NAMES);
                if (classNamesStr != null && !classNamesStr.isEmpty()) {
                    classNames = Arrays.asList(classNamesStr.split(","));
                }
            }

            return new ExportParams(type, target, output, classNames,
                    timestamp != null ? Long.parseLong(timestamp) : 0);
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
        public final long timestamp;

        public ExportParams(ExportType type, String targetName, String outputPath,
                List<String> classNames, long timestamp) {
            this.type = type;
            this.targetName = targetName;
            this.outputPath = outputPath;
            this.classNames = classNames;
            this.timestamp = timestamp;
        }

        public String getDescription() {
            if (type == ExportType.CLASS) {
                return "Export class '" + targetName + "' to " + outputPath;
            } else {
                return "Export module '" + targetName + "' (" +
                        (classNames != null ? classNames.size() : 0) + " classes) to " + outputPath;
            }
        }

        public String getFormattedTimestamp() {
            return new Date(timestamp).toString();
        }
    }
}
