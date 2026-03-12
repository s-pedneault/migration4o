package migration4o.migration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import migration4o.migration.format.FormatHandler;

public final class ExportOutputOption {

    public static final String XML_XSD = "XML + XSD";
    public static final String HTML_JS = "HTML + JS";
    public static final String JSON = "JSON";
    public static final String EXCEL = "EXCEL";

    private ExportOutputOption() {
    }

    public static List<String> allOptions() {
        return List.of(XML_XSD, HTML_JS, JSON, EXCEL);
    }

    public static String toWriterFormat(String option) {
        if (option == null) {
            return "XML";
        }
        if (XML_XSD.equalsIgnoreCase(option)) {
            return "XML";
        }
        if (HTML_JS.equalsIgnoreCase(option)) {
            return "JS";
        }
        if (JSON.equalsIgnoreCase(option)) {
            return "JSON";
        }
        if (EXCEL.equalsIgnoreCase(option)) {
            return "EXCEL";
        }
        return option;
    }

    public static boolean generatesHtmlViewer(String option) {
        return HTML_JS.equalsIgnoreCase(option);
    }

    public static boolean generatesXsd(String option) {
        return XML_XSD.equalsIgnoreCase(option);
    }

    public static List<String> normalize(List<String> requestedOptions) {
        Set<String> normalized = new LinkedHashSet<>();

        if (requestedOptions != null) {
            for (String option : requestedOptions) {
                if (option == null || option.isBlank()) {
                    continue;
                }
                String mapped = fromPersistedToken(option.trim());
                if (mapped != null) {
                    normalized.add(mapped);
                }
            }
        }

        if (normalized.isEmpty()) {
            normalized.add(XML_XSD);
        }

        // HTML export requires XML: ensure XML + XSD comes first so that the
        // primary handler (which records full diagnostics) is always XML.
        if (normalized.contains(HTML_JS) && !normalized.contains(XML_XSD)) {
            Set<String> withXml = new LinkedHashSet<>();
            withXml.add(XML_XSD);
            withXml.addAll(normalized);
            normalized = withXml;
        }

        return new ArrayList<>(normalized);
    }

    public static List<String> parsePersistedOptions(String persisted) {
        if (persisted == null || persisted.isBlank()) {
            return List.of(XML_XSD);
        }

        String[] parts = persisted.split(",");
        List<String> parsed = new ArrayList<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            parsed.add(part.trim());
        }
        return normalize(parsed);
    }

    public static String toPersistedOptions(List<String> options) {
        List<String> normalized = normalize(options);
        return String.join(",", normalized);
    }

    /**
     * Converts a list of output-option strings to the corresponding
     * {@link ExportFormat} values (in normalized order, no duplicates).
     */
    public static List<ExportFormat> toFormats(List<String> options) {
        List<String> normalized = normalize(options);
        List<ExportFormat> formats = new ArrayList<>();
        for (String opt : normalized) {
            if (XML_XSD.equalsIgnoreCase(opt))
                formats.add(ExportFormat.XML);
            else if (HTML_JS.equalsIgnoreCase(opt))
                formats.add(ExportFormat.HTML);
            else if (JSON.equalsIgnoreCase(opt))
                formats.add(ExportFormat.JSON);
            else if (EXCEL.equalsIgnoreCase(opt))
                formats.add(ExportFormat.EXCEL);
        }
        return formats;
    }

    /**
     * Returns {@code true} when the option list includes {@code XML + XSD}
     * (i.e. the XML format with XSD generation).
     */
    public static boolean generatesXsd(List<String> options) {
        return normalize(options).stream().anyMatch(o -> XML_XSD.equalsIgnoreCase(o));
    }

    /**
     * Creates ready-to-use {@link FormatHandler} instances for the requested
     * output options.
     */
    public static List<FormatHandler> toHandlers(List<String> options) {
        return FormatHandler.create(toFormats(options), generatesXsd(options));
    }

    private static String fromPersistedToken(String token) {
        if (token.equalsIgnoreCase(XML_XSD) || token.equalsIgnoreCase("XML")) {
            return XML_XSD;
        }
        if (token.equalsIgnoreCase(HTML_JS) || token.equalsIgnoreCase("JS")) {
            return HTML_JS;
        }
        if (token.equalsIgnoreCase(JSON)) {
            return JSON;
        }
        if (token.equalsIgnoreCase(EXCEL)) {
            return EXCEL;
        }
        return null;
    }
}