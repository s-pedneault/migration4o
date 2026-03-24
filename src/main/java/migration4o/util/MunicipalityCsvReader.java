package migration4o.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code schema/municipalities.csv} and resolves client municipality info
 * by {@code mcode} (the database folder name, e.g. {@code "54060"}).
 *
 * <p>The CSV uses RFC 4180 quoting (double-quoted fields, embedded quotes
 * doubled). The reader handles multi-line fields defensively: if a line ends
 * inside an open quote it is joined with the next line.
 *
 * <p>Column layout (0-based indices used internally):
 * <pre>
 *  0  mcode       5-digit municipality code
 *  1  munnom      municipality name
 *  2  madr1       civic address line 1
 *  6  mcodpos     postal code
 *  7  mcourriel   e-mail
 *  8  mweb        web site
 * 14  regadm      administrative region
 * 16  mrc         MRC
 * 22  mpopul      population
 * </pre>
 */
public final class MunicipalityCsvReader {

    private static final String DEFAULT_CSV_PATH = "schema/municipalities.csv";

    private static final int COL_CODE = 0;
    private static final int COL_NAME = 1;
    private static final int COL_ADDR1 = 2;
    private static final int COL_CODPOS = 6;
    private static final int COL_EMAIL = 7;
    private static final int COL_WEB = 8;
    private static final int COL_REGION = 14;
    private static final int COL_MRC = 16;
    private static final int COL_POPUL = 22;

    private MunicipalityCsvReader() {
    }

    /**
     * Looks up the municipality matching {@code mcode} in the default CSV
     * ({@code schema/municipalities.csv} relative to the working directory).
     *
     * @param mcode the municipality code, e.g. {@code "54060"}
     * @return the matching {@link MunicipalityInfo}, or {@code null} if not found
     *         or if the CSV cannot be read
     */
    public static MunicipalityInfo lookup(String mcode) {
        return lookup(mcode, Paths.get(DEFAULT_CSV_PATH));
    }

    /**
     * Looks up the municipality matching {@code mcode} in the given CSV file.
     *
     * @param mcode   the municipality code, e.g. {@code "54060"}
     * @param csvPath path to the municipalities CSV
     * @return the matching {@link MunicipalityInfo}, or {@code null} if not found
     */
    public static MunicipalityInfo lookup(String mcode, Path csvPath) {
        if (mcode == null || mcode.isBlank())
            return null;
        if (!Files.isReadable(csvPath))
            return null;

        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            boolean firstLine = true;
            String partial = null;

            String line;
            while ((line = reader.readLine()) != null) {
                // Join continuation lines if a quoted field spans multiple lines
                if (partial != null) {
                    line = partial + "\n" + line;
                    partial = null;
                }

                // Detect unclosed quote (odd number of quotes means multi-line field)
                if (countQuotes(line) % 2 != 0) {
                    partial = line;
                    continue;
                }

                if (firstLine) {
                    firstLine = false;
                    continue; // skip header
                }

                List<String> cols = parseRow(line);
                if (cols.size() <= COL_CODE)
                    continue;

                String code = unquote(cols.get(COL_CODE));
                if (!mcode.equals(code))
                    continue;

                // Found the matching row — extract the fields we need
                String name = col(cols, COL_NAME);
                String addr1 = col(cols, COL_ADDR1);
                String codpos = col(cols, COL_CODPOS);
                String email = col(cols, COL_EMAIL);
                String web = col(cols, COL_WEB);
                String region = col(cols, COL_REGION);
                String mrc = col(cols, COL_MRC);
                String popul = col(cols, COL_POPUL);

                // Build address: "10, rue des Églises O.  J0E1B0"
                String address = addr1.isBlank() ? codpos : codpos.isBlank() ? addr1 : addr1 + "  " + codpos;

                return new MunicipalityInfo(code, name, mrc, region, address, email, web, popul);
            }
        } catch (IOException e) {
            System.err.println("MunicipalityCsvReader: could not read " + csvPath + ": " + e.getMessage());
        }
        return null;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private static String col(List<String> cols, int idx) {
        if (idx >= cols.size())
            return "";
        return unquote(cols.get(idx));
    }

    /** Counts the number of double-quote characters in a line. */
    private static int countQuotes(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"')
                count++;
        }
        return count;
    }

    /**
     * Parses one CSV row into raw field tokens (including surrounding quotes).
     * Handles RFC 4180: fields may be quoted; {@code ""} inside a quoted field
     * represents a literal double-quote.
     */
    private static List<String> parseRow(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int len = line.length();

        for (int i = 0; i < len; i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++; // skip doubled quote
                    } else {
                        inQuotes = false;
                        field.append('"');
                    }
                } else {
                    field.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                    field.append('"');
                } else if (c == ',') {
                    fields.add(field.toString());
                    field.setLength(0);
                } else {
                    field.append(c);
                }
            }
        }
        fields.add(field.toString());
        return fields;
    }

    /**
     * Strips surrounding double-quotes and un-escapes {@code ""} → {@code "}.
     */
    private static String unquote(String raw) {
        String s = raw.trim();
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1, s.length() - 1).replace("\"\"", "\"");
        }
        return s;
    }
}
