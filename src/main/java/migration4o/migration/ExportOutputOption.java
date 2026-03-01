package migration4o.migration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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