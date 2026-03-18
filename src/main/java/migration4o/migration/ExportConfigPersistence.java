package migration4o.migration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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
import migration4o.util.JsonUtil;

/**
 * Saves and loads {@link ExportConfig} as {@code export-config.json} in the
 * database file's parent folder. Uses {@link JsonUtil} for JSON primitives — no
 * external dependencies.
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
        sb.append("  \"exportMode\": ").append(JsonUtil.jsonString(c.getExportMode().name())).append(",\n");
        sb.append("  \"maxObjectsPerClass\": ").append(c.getMaxObjectsPerClass()).append(",\n");
        sb.append("  \"exportNativeIds\": ").append(c.isExportNativeIds()).append(",\n");
        sb.append("  \"fullTracking\": ").append(c.isFullTracking()).append(",\n");
        sb.append("  \"applyUserSelectedFieldExclusions\": ").append(c.isApplyUserSelectedFieldExclusions()).append(",\n");
        sb.append("  \"applySkipWhenConditions\": ").append(c.isApplySkipWhenConditions()).append(",\n");
        sb.append("  \"applyExportCriteriaFilters\": ").append(c.isApplyExportCriteriaFilters()).append(",\n");
        sb.append("  \"skipObjectsWithoutExportableFields\": ").append(c.isSkipObjectsWithoutExportableFields()).append(",\n");
        sb.append("  \"outputOptions\": ").append(JsonUtil.jsonStringArray(c.getOutputOptions())).append(",\n");
        sb.append("  \"selectedSkipOptionNames\": ").append(JsonUtil.jsonStringArray(c.getSelectedSkipOptionNames())).append(",\n");
        sb.append("  \"seeds\": ").append(seedsToJson(c.getSeeds())).append(",\n");
        sb.append("  \"seedMaxPerClass\": ").append(c.getSeedMaxPerClass()).append(",\n");
        sb.append("  \"outputBranch\": ").append(JsonUtil.jsonString(c.getOutputBranch())).append(",\n");
        sb.append("  \"exportLanguage\": ").append(JsonUtil.jsonString(c.getExportLanguage())).append("\n");
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
            sb.append("      \"className\": ").append(JsonUtil.jsonString(q.getClassName())).append(",\n");
            sb.append("      \"conditions\": ");
            List<SeedCondition> conds = q.getConditions();
            if (conds == null || conds.isEmpty()) {
                sb.append("[]");
            } else {
                sb.append("[\n");
                for (int j = 0; j < conds.size(); j++) {
                    SeedCondition cond = conds.get(j);
                    sb.append("        { \"fieldPath\": ").append(JsonUtil.jsonString(cond.getFieldPath()));
                    sb.append(", \"operator\": ").append(JsonUtil.jsonString(cond.getOperator().name()));
                    sb.append(", \"value\": ").append(JsonUtil.jsonString(cond.getValue())).append(" }");
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

    // ── JSON deserialization ─────────────────────────────────────────────────

    private static ExportConfig fromJson(String json) {
        ExportConfig c = new ExportConfig();
        c.setExportMode(JsonUtil.readEnum(json, "exportMode", ExportMode.class, ExportMode.ALL_OBJECTS));
        c.setMaxObjectsPerClass(JsonUtil.readInt(json, "maxObjectsPerClass", 50));
        c.setExportNativeIds(JsonUtil.readBool(json, "exportNativeIds", false));
        c.setFullTracking(JsonUtil.readBool(json, "fullTracking", true));
        c.setApplyUserSelectedFieldExclusions(JsonUtil.readBool(json, "applyUserSelectedFieldExclusions", true));
        c.setApplySkipWhenConditions(JsonUtil.readBool(json, "applySkipWhenConditions", true));
        c.setApplyExportCriteriaFilters(JsonUtil.readBool(json, "applyExportCriteriaFilters", true));
        c.setSkipObjectsWithoutExportableFields(JsonUtil.readBool(json, "skipObjectsWithoutExportableFields", true));
        c.setOutputOptions(JsonUtil.readStringList(json, "outputOptions"));
        c.setSelectedSkipOptionNames(JsonUtil.readStringList(json, "selectedSkipOptionNames"));
        c.setSeeds(parseSeeds(json));
        c.setSeedMaxPerClass(JsonUtil.readInt(json, "seedMaxPerClass", 50));
        c.setOutputBranch(JsonUtil.readString(json, "outputBranch"));
        String lang = JsonUtil.readString(json, "exportLanguage");
        if (lang != null && !lang.isBlank()) {
            c.setExportLanguage(lang);
        }
        return c;
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
        int close = JsonUtil.findMatchingBracket(json, open);
        if (close < 0)
            return seeds;
        String arr = json.substring(open + 1, close);

        int i = 0;
        while (i < arr.length()) {
            int objStart = arr.indexOf('{', i);
            if (objStart < 0)
                break;
            int objEnd = JsonUtil.findMatchingBrace(arr, objStart);
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
        String className = JsonUtil.readString(json, "className");
        if (className == null)
            return null;
        SeedQuery q = new SeedQuery(className);

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
        int close = JsonUtil.findMatchingBracket(json, open);
        if (close < 0)
            return q;
        String arr = json.substring(open + 1, close);
        int i = 0;
        while (i < arr.length()) {
            int cs = arr.indexOf('{', i);
            if (cs < 0)
                break;
            int ce = JsonUtil.findMatchingBrace(arr, cs);
            if (ce < 0)
                break;
            String condStr = arr.substring(cs, ce + 1);
            String fp = JsonUtil.readString(condStr, "fieldPath");
            String op = JsonUtil.readString(condStr, "operator");
            String val = JsonUtil.readString(condStr, "value");
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
}
