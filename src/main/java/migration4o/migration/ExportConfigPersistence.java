package migration4o.migration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import migration4o.models.ui.ExportConfig;
import migration4o.models.ui.ExportConfig.ExportMode;
import migration4o.models.ui.SeedCondition;
import migration4o.models.ui.SeedQuery;

/**
 * Saves and loads {@link ExportConfig} as {@code export-config.json} in the
 * database file's parent folder. Uses manual JSON serialization — no external
 * dependencies.
 */
public class ExportConfigPersistence {

    private static final String CONFIG_FILENAME = "export-config.json";

    /**
     * Saves the config to {@code <databaseParentFolder>/export-config.json}.
     */
    public static void save(ExportConfig config, String databaseFilePath) throws IOException {
        Path configPath = resolveConfigPath(databaseFilePath);
        Files.createDirectories(configPath.getParent());
        try (BufferedWriter w = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            w.write(toJson(config));
        }
    }

    /**
     * Loads from {@code <databaseParentFolder>/export-config.json}, or returns
     * a default config if the file doesn't exist.
     */
    public static ExportConfig load(String databaseFilePath) {
        Path configPath = resolveConfigPath(databaseFilePath);
        if (!Files.exists(configPath)) {
            return new ExportConfig();
        }
        try (BufferedReader r = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return fromJson(sb.toString());
        } catch (Exception e) {
            System.err.println("[ExportConfigPersistence] Failed to load config: " + e.getMessage());
            return new ExportConfig();
        }
    }

    private static Path resolveConfigPath(String databaseFilePath) {
        Path dbPath = Paths.get(databaseFilePath);
        Path parent = dbPath.getParent();
        if (parent == null) {
            parent = Paths.get(".");
        }
        return parent.resolve(CONFIG_FILENAME);
    }

    // ── JSON serialization ──────────────────────────────────────────────────

    private static String toJson(ExportConfig c) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"exportMode\": ").append(jsonStr(c.getExportMode().name())).append(",\n");
        sb.append("  \"maxObjectsPerClass\": ").append(c.getMaxObjectsPerClass()).append(",\n");
        sb.append("  \"exportNativeIds\": ").append(c.isExportNativeIds()).append(",\n");
        sb.append("  \"fullTracking\": ").append(c.isFullTracking()).append(",\n");
        sb.append("  \"applyUserSelectedFieldExclusions\": ").append(c.isApplyUserSelectedFieldExclusions()).append(",\n");
        sb.append("  \"applySkipWhenConditions\": ").append(c.isApplySkipWhenConditions()).append(",\n");
        sb.append("  \"applyExportCriteriaFilters\": ").append(c.isApplyExportCriteriaFilters()).append(",\n");
        sb.append("  \"skipObjectsWithoutExportableFields\": ").append(c.isSkipObjectsWithoutExportableFields()).append(",\n");
        sb.append("  \"outputOptions\": ").append(jsonStringList(c.getOutputOptions())).append(",\n");
        sb.append("  \"selectedSkipOptionNames\": ").append(jsonStringList(c.getSelectedSkipOptionNames())).append(",\n");
        sb.append("  \"seeds\": ").append(seedsToJson(c.getSeeds())).append(",\n");
        sb.append("  \"seedMaxPerClass\": ").append(c.getSeedMaxPerClass()).append(",\n");
        sb.append("  \"outputBranch\": ").append(jsonStr(c.getOutputBranch())).append("\n");
        sb.append("}");
        return sb.toString();
    }

    private static String seedsToJson(List<SeedQuery> seeds) {
        if (seeds == null || seeds.isEmpty())
            return "[]";
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < seeds.size(); i++) {
            SeedQuery q = seeds.get(i);
            sb.append("    {\n");
            sb.append("      \"className\": ").append(jsonStr(q.getClassName())).append(",\n");
            sb.append("      \"conditions\": ");
            List<SeedCondition> conds = q.getConditions();
            if (conds == null || conds.isEmpty()) {
                sb.append("[]");
            } else {
                sb.append("[\n");
                for (int j = 0; j < conds.size(); j++) {
                    SeedCondition cond = conds.get(j);
                    sb.append("        { \"fieldPath\": ").append(jsonStr(cond.getFieldPath()));
                    sb.append(", \"operator\": ").append(jsonStr(cond.getOperator().name()));
                    sb.append(", \"value\": ").append(jsonStr(cond.getValue())).append(" }");
                    if (j < conds.size() - 1)
                        sb.append(",");
                    sb.append("\n");
                }
                sb.append("      ]");
            }
            sb.append("\n    }");
            if (i < seeds.size() - 1)
                sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]");
        return sb.toString();
    }

    private static String jsonStr(String s) {
        if (s == null)
            return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    private static String jsonStringList(List<String> list) {
        if (list == null || list.isEmpty())
            return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(jsonStr(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    // ── JSON parsing (minimal hand-rolled parser) ────────────────────────────

    private static ExportConfig fromJson(String json) {
        ExportConfig c = new ExportConfig();
        c.setExportMode(parseEnum(json, "exportMode", ExportMode.class, ExportMode.ALL_OBJECTS));
        c.setMaxObjectsPerClass(parseInt(json, "maxObjectsPerClass", 50));
        c.setExportNativeIds(parseBool(json, "exportNativeIds", false));
        c.setFullTracking(parseBool(json, "fullTracking", true));
        c.setApplyUserSelectedFieldExclusions(parseBool(json, "applyUserSelectedFieldExclusions", true));
        c.setApplySkipWhenConditions(parseBool(json, "applySkipWhenConditions", true));
        c.setApplyExportCriteriaFilters(parseBool(json, "applyExportCriteriaFilters", true));
        c.setSkipObjectsWithoutExportableFields(parseBool(json, "skipObjectsWithoutExportableFields", true));
        c.setOutputOptions(parseStringList(json, "outputOptions"));
        c.setSelectedSkipOptionNames(parseStringList(json, "selectedSkipOptionNames"));
        c.setSeeds(parseSeeds(json));
        c.setSeedMaxPerClass(parseInt(json, "seedMaxPerClass", 50));
        c.setOutputBranch(parseString(json, "outputBranch"));
        return c;
    }

    private static String parseString(String json, String key) {
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0)
            return null;
        int colon = json.indexOf(':', ki + search.length());
        if (colon < 0)
            return null;
        int start = json.indexOf('"', colon + 1);
        if (start < 0)
            return null;
        start++;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (next == '"') {
                    sb.append('"');
                    i++;
                } else if (next == '\\') {
                    sb.append('\\');
                    i++;
                } else if (next == 'n') {
                    sb.append('\n');
                    i++;
                } else if (next == 'r') {
                    sb.append('\r');
                    i++;
                } else if (next == 't') {
                    sb.append('\t');
                    i++;
                } else {
                    sb.append(ch);
                }
            } else if (ch == '"') {
                break;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static boolean parseBool(String json, String key, boolean defaultVal) {
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0)
            return defaultVal;
        int colon = json.indexOf(':', ki + search.length());
        if (colon < 0)
            return defaultVal;
        String after = json.substring(colon + 1).trim();
        if (after.startsWith("true"))
            return true;
        if (after.startsWith("false"))
            return false;
        return defaultVal;
    }

    private static int parseInt(String json, String key, int defaultVal) {
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0)
            return defaultVal;
        int colon = json.indexOf(':', ki + search.length());
        if (colon < 0)
            return defaultVal;
        String after = json.substring(colon + 1).trim();
        StringBuilder num = new StringBuilder();
        for (int i = 0; i < after.length(); i++) {
            char ch = after.charAt(i);
            if (ch == '-' || (ch >= '0' && ch <= '9'))
                num.append(ch);
            else
                break;
        }
        try {
            return Integer.parseInt(num.toString());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static <E extends Enum<E>> E parseEnum(String json, String key, Class<E> enumClass, E defaultVal) {
        String val = parseString(json, key);
        if (val == null)
            return defaultVal;
        try {
            return Enum.valueOf(enumClass, val);
        } catch (IllegalArgumentException e) {
            return defaultVal;
        }
    }

    private static List<String> parseStringList(String json, String key) {
        List<String> result = new ArrayList<>();
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0)
            return result;
        int colon = json.indexOf(':', ki + search.length());
        if (colon < 0)
            return result;
        int open = json.indexOf('[', colon);
        if (open < 0)
            return result;
        int close = findMatchingBracket(json, open);
        if (close < 0)
            return result;
        String arr = json.substring(open + 1, close);
        int i = 0;
        while (i < arr.length()) {
            int q1 = arr.indexOf('"', i);
            if (q1 < 0)
                break;
            int q2 = findClosingQuote(arr, q1 + 1);
            if (q2 < 0)
                break;
            result.add(unescapeJsonString(arr.substring(q1 + 1, q2)));
            i = q2 + 1;
        }
        return result;
    }

    private static List<SeedQuery> parseSeeds(String json) {
        List<SeedQuery> seeds = new ArrayList<>();
        String search = "\"seeds\"";
        int ki = json.indexOf(search);
        if (ki < 0)
            return seeds;
        int colon = json.indexOf(':', ki + search.length());
        if (colon < 0)
            return seeds;
        int open = json.indexOf('[', colon);
        if (open < 0)
            return seeds;
        int close = findMatchingBracket(json, open);
        if (close < 0)
            return seeds;
        String arr = json.substring(open + 1, close);

        // Find each seed object { ... }
        int i = 0;
        while (i < arr.length()) {
            int objStart = arr.indexOf('{', i);
            if (objStart < 0)
                break;
            int objEnd = findMatchingBrace(arr, objStart);
            if (objEnd < 0)
                break;
            String objStr = arr.substring(objStart, objEnd + 1);
            SeedQuery q = parseSeedQuery(objStr);
            if (q != null)
                seeds.add(q);
            i = objEnd + 1;
        }
        return seeds;
    }

    private static SeedQuery parseSeedQuery(String json) {
        String className = parseString(json, "className");
        if (className == null)
            return null;
        SeedQuery q = new SeedQuery(className);
        // Parse conditions array
        String search = "\"conditions\"";
        int ki = json.indexOf(search);
        if (ki < 0)
            return q;
        int colon = json.indexOf(':', ki + search.length());
        if (colon < 0)
            return q;
        int open = json.indexOf('[', colon);
        if (open < 0)
            return q;
        int close = findMatchingBracket(json, open);
        if (close < 0)
            return q;
        String arr = json.substring(open + 1, close);
        int i = 0;
        while (i < arr.length()) {
            int cs = arr.indexOf('{', i);
            if (cs < 0)
                break;
            int ce = findMatchingBrace(arr, cs);
            if (ce < 0)
                break;
            String condStr = arr.substring(cs, ce + 1);
            String fp = parseString(condStr, "fieldPath");
            String op = parseString(condStr, "operator");
            String val = parseString(condStr, "value");
            if (fp != null && op != null) {
                SeedCondition.Operator operator;
                try {
                    operator = SeedCondition.Operator.valueOf(op);
                } catch (IllegalArgumentException e) {
                    operator = SeedCondition.Operator.EQUALS;
                }
                q.addCondition(new SeedCondition(fp, operator, val));
            }
            i = ce + 1;
        }
        return q;
    }

    private static int findMatchingBracket(String s, int openPos) {
        int depth = 0;
        boolean inString = false;
        for (int i = openPos; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && inString) {
                i++;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString)
                continue;
            if (ch == '[')
                depth++;
            if (ch == ']') {
                depth--;
                if (depth == 0)
                    return i;
            }
        }
        return -1;
    }

    private static int findMatchingBrace(String s, int openPos) {
        int depth = 0;
        boolean inString = false;
        for (int i = openPos; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && inString) {
                i++;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString)
                continue;
            if (ch == '{')
                depth++;
            if (ch == '}') {
                depth--;
                if (depth == 0)
                    return i;
            }
        }
        return -1;
    }

    private static int findClosingQuote(String s, int startAfterOpenQuote) {
        for (int i = startAfterOpenQuote; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\') {
                i++;
                continue;
            }
            if (ch == '"')
                return i;
        }
        return -1;
    }

    private static String unescapeJsonString(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == '"') {
                    sb.append('"');
                    i++;
                } else if (next == '\\') {
                    sb.append('\\');
                    i++;
                } else if (next == 'n') {
                    sb.append('\n');
                    i++;
                } else if (next == 'r') {
                    sb.append('\r');
                    i++;
                } else if (next == 't') {
                    sb.append('\t');
                    i++;
                } else {
                    sb.append(ch);
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
