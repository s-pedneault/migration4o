package migration4o.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generates {@code doc/style-guide.html} — a self-contained HTML viewer populated with
 * crafted sample data that exercises every rendering path and data structure in the JS viewer.
 *
 * <p>Run {@code main()} from the project root to (re)generate the file.
 *
 * <h2>Coverage checklist</h2>
 * <ul>
 *   <li>Primitive field types: string, number, boolean, date string, long epoch</li>
 *   <li>Empty / null fields (should render as dash)</li>
 *   <li>IDEntite reference in detail view: with nav href (dark-blue link) and without (plain text)</li>
 *   <li>IDEntite reference column in collection table (regression: was rendering as yellow badge)</li>
 *   <li>_{@code _id} / {@code _summary} sibling columns hidden from table headers</li>
 *   <li>Collection of strings (joined with separator)</li>
 *   <li>Collection of flat objects rendered as table</li>
 *   <li>Collection with IDEntite columns including cross-page deep-link</li>
 *   <li>Nested collection (objects containing arrays)</li>
 *   <li>Embedded object: popup mode and section mode</li>
 *   <li>Layout nodes: section (collapsible + non), divider, columns, table, tabs / tab, field</li>
 *   <li>Format specs: {@code bool:}, {@code date:}, {@code longdate:}, {@code num:} with unit suffix</li>
 *   <li>Back-references (CROSS_REFS): compact single-group and tabbed multi-group</li>
 *   <li>Schema-driven field titles via SCHEMA_FIELDS (schemaTitleByName)</li>
 *   <li>Cross-page deep-links via pointsToByPath + navHrefByDestName</li>
 * </ul>
 */
public final class StyleGuideGenerator {

    private StyleGuideGenerator() {
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        Path output = Paths.get("doc/style-guide.html");
        Path result = generate(output);
        System.out.println("[StyleGuide] Written: " + result.toAbsolutePath());
    }

    public static Path generate(Path outputPath) throws IOException {
        Path result = JsViewerHtmlGenerator.writeRawViewer(outputPath, "Style Guide \u2014 Viewer JS", "StyleGuideRecord", buildNavJson(), "./", buildDetailLayout(), buildClassLayouts(), buildSchemaFields(), "null", buildCrossRefs(), buildDataScript());
        postProcessDebugMode(result);
        return result;
    }

    // ── Debug overlay ─────────────────────────────────────────────────────────

    /**
     * Injects the debug-mode toggle button, CSS, and script just before {@code </body>}.
     * When the button is clicked it adds {@code sg-debug} to {@code <body>}, which
     * activates colored outlines and corner-badge labels on every rendered structure.
     */
    private static void postProcessDebugMode(Path htmlPath) throws IOException {
        String html = new String(Files.readAllBytes(htmlPath), StandardCharsets.UTF_8);
        html = html.replace("</body>", buildDebugBlock() + "\n</body>");
        Files.write(htmlPath, html.getBytes(StandardCharsets.UTF_8));
    }

    private static String buildDebugBlock() {
        String css = debugCss();
        String js = "(function(){" + "var btn=document.getElementById('sg-debug-btn');" + "if(!btn)return;" + "btn.addEventListener('click',function(){" + "var on=document.body.classList.toggle('sg-debug');" + "btn.classList.toggle('sg-debug-active',on);" + "btn.textContent=on?'\u25cf Debug ON':'\u25cb Debug';" + "});" + "})();";
        return "<style id=\"sg-debug-style\">\n" + css + "</style>\n" + "<button id=\"sg-debug-btn\" type=\"button\">&#9675; Debug</button>\n" + "<script>" + js + "</script>\n";
    }

    /** Returns the full CSS for the debug overlay, scoped to {@code body.sg-debug}. */
    private static String debugCss() {
        // Color palette per structure type
        // Format: { cssClass, label, hexColor, outline-style }
        String[][] structs = {
                // top-level scroll container + detail header
                { ".detail-scroll", "detail-scroll", "#475569", "dashed" }, { ".detail-header", "detail-header", "#334155", "solid" },
                // section anatomy (detail-section contains section-body; <summary> contains summary-title + summary-meta)
                { ".detail-section", "detail-section", "#1d4ed8", "solid" }, { ".section-body", "section-body", "#3b82f6", "dotted" }, { ".summary-title", "summary-title", "#1e40af", "solid" }, // inline <span> — forced inline-block below
                { ".summary-meta", "summary-meta", "#1e3a8a", "dotted" }, // inline <span> — forced inline-block below
                { ".layout-section-title", "section-title", "#2563eb", "dotted" },
                // columns
                { ".layout-columns", "columns", "#15803d", "solid" }, { ".layout-column", "column", "#16a34a", "dashed" },
                // tabs
                { ".layout-tabs", "tabs", "#7c3aed", "solid" }, { ".layout-tabs-header", "tabs-header", "#5b21b6", "dotted" }, { ".tab-bar", "tab-bar", "#6d28d9", "dashed" }, { ".tab-panel", "tab-panel", "#a855f7", "dashed" },
                // field primitives
                { ".field-group", "field-group", "#c2410c", "solid" }, { ".field-group-subtitle", "group-subtitle", "#ea580c", "dotted" }, { ".field-pair", "field-pair", "#b45309", "dashed" }, { ".field-columns-2", "field-columns-2", "#d97706", "dashed" }, { ".field-columns-3", "field-columns-3", "#d97706", "dashed" }, { ".field-row", "field-row", "#be123c", "solid" }, { ".field-empty-group", "empty-fields", "#6b7280", "dashed" },
                // collection toolbar + pager + table
                { ".collection-toolbar", "coll-toolbar", "#065f46", "solid" }, { ".collection-pager", "coll-pager", "#047857", "dashed" }, { ".collection-table", "coll-table", "#0f766e", "solid" },
                // preview container (renderPreview default size)
                { ".preview-inline", "preview-inline", "#78350f", "solid" },
                // back-refs
                { ".back-ref-section", "back-ref-section", "#0f766e", "solid" }, { ".back-ref-header", "back-ref-header", "#0d9488", "dotted" }, { ".back-ref-list", "back-ref-list", "#0e7490", "dashed" }, { ".back-ref-row", "back-ref-row", "#0369a1", "solid" }, { ".back-ref-id", "back-ref-id", "#0ea5e9", "dashed" }, { ".back-ref-summary", "back-ref-summary", "#0284c7", "dashed" }, };

        StringBuilder sb = new StringBuilder();

        // ── Toggle button ──────────────────────────────────────────────────
        sb.append("#sg-debug-btn{" + "position:fixed;bottom:20px;right:20px;z-index:99999;" + "background:#1e293b;color:#94a3b8;border:1.5px solid #334155;" + "border-radius:6px;padding:6px 14px;font-size:11px;" + "font-family:monospace;font-weight:700;cursor:pointer;" + "letter-spacing:.05em;text-transform:uppercase;" + "box-shadow:0 2px 8px rgba(0,0,0,.4);" + "transition:background .15s,color .15s;}\n");
        sb.append("#sg-debug-btn.sg-debug-active{" + "background:#7c3aed;color:#fff;border-color:#6d28d9;}\n");
        sb.append("#sg-debug-btn:hover{opacity:.85;}\n\n");

        // ── Shared: make every annotated element a positioning context ─────
        sb.append("body.sg-debug ");
        for (int i = 0; i < structs.length; i++) {
            if (i > 0)
                sb.append(",\nbody.sg-debug ");
            sb.append(structs[i][0]);
        }
        sb.append("{position:relative !important;}\n\n");

        // ── Shared badge base style ────────────────────────────────────────
        sb.append("body.sg-debug ");
        for (int i = 0; i < structs.length; i++) {
            if (i > 0)
                sb.append(",\nbody.sg-debug ");
            sb.append(structs[i][0]).append("::before");
        }
        sb.append("{position:absolute;top:0;right:0;" + "font-size:9px;font-family:monospace;font-weight:800;" + "line-height:1;padding:2px 5px;z-index:9998;" + "pointer-events:none;border-radius:0 0 0 4px;" + "white-space:nowrap;color:#fff;opacity:.9;}\n\n");

        // ── Per-structure color + label ────────────────────────────────────
        for (String[] s : structs) {
            String sel = s[0];
            String label = s[1];
            String color = s[2];
            String style = s[3];
            sb.append("body.sg-debug ").append(sel).append("{outline:2px ").append(style).append(' ').append(color).append(" !important;}\n");
            sb.append("body.sg-debug ").append(sel).append("::before").append("{content:\"").append(label).append("\";" + "background:").append(color).append(";}\n");
        }

        // ── Divider (hr — void element, no ::before badge possible) ───────
        sb.append("body.sg-debug hr.layout-divider" + "{outline:2px solid #92400e !important;}\n");

        // ── Inline <span> elements: force inline-block so position:relative + ::before badge works ──
        sb.append("body.sg-debug .summary-title," + "body.sg-debug .summary-meta" + "{display:inline-block !important;}\n");

        return sb.toString();
    }

    // ── JSON helper micro-DSL ─────────────────────────────────────────────────

    /** Wraps a string in JSON double-quotes with minimal escaping. */
    private static String s(String v) {
        if (v == null)
            return "null";
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"";
    }

    /** JSON boolean literal. */
    private static String b(boolean v) {
        return String.valueOf(v);
    }

    /** JSON number from a double (omits decimal point when value is whole). */
    private static String n(double v) {
        long lv = (long) v;
        return v == lv ? String.valueOf(lv) : String.valueOf(v);
    }

    /** JSON number from a long. */
    private static String l(long v) {
        return String.valueOf(v);
    }

    /** Builds {@code "key": value} with a pre-serialised value. */
    private static String kv(String key, String value) {
        return "\"" + key + "\":" + value;
    }

    /** Builds {@code "key": "stringValue"}. */
    private static String ks(String key, String value) {
        return kv(key, s(value));
    }

    /** Builds a JSON object from alternating key/value-or-pair strings. */
    private static String obj(String... parts) {
        return "{" + String.join(",", parts) + "}";
    }

    /** Builds a JSON array from element strings. */
    private static String arr(String... items) {
        return "[" + String.join(",", items) + "]";
    }

    // ── Nav items ─────────────────────────────────────────────────────────────

    private static String buildNavJson() {
        return arr(obj(ks("label", "Style Guide"), kv("children", arr(obj(ks("label", "Enregistrements"), ks("href", "./style-guide.html")), obj(ks("label", "Type de syst\u00e8me"), ks("href", "./TypeSysteme.html")), obj(ks("label", "Type de contact"), ks("href", "./TypeContact.html")), obj(ks("label", "Auteur"), ks("href", "./Auteur.html"))))));
    }

    // ── Detail layout ─────────────────────────────────────────────────────────
    //
    // Exercises every layout node type:
    //   section (non-collapsible)  ← informations générales
    //   field with IDEntite ref    ← typeSysteme
    //   field with array value     ← tags  → renderValue
    //   divider (full)
    //   columns (2-column)
    //   field with format specs    ← num:, date:, longdate:, bool:
    //   section (collapsible+ref)  ← adresse
    //   tabs / tab                 ← données spatiales
    //   divider (small)
    //   table with columnTitles    ← contacts   (IDEntite column regression)
    //   table                      ← interventions (IDEntite auteur column)

    private static String buildDetailLayout() {
        String infoSection = obj(ks("type", "section"), kv("props", obj(ks("title", "Informations g\u00e9n\u00e9rales"))), kv("children", arr(field("prenom", "Pr\u00e9nom", null), field("nom", "Nom", null), field("codeAcces", "Code d\u2019acc\u00e8s", null), field("actif", "Actif", "bool:Oui,Non"), field("description", "Description", null), field("typeSysteme", "Type de syst\u00e8me", null), // IDEntite ref with nav link
                field("tags", "\u00c9tiquettes", null) // string[] → renderValue
        )));

        String dividerFull = obj(ks("type", "divider"), kv("props", obj()));

        String columnsNode = obj(ks("type", "columns"), kv("props", obj(ks("sizes", "55,45"))), kv("children", arr(obj(ks("type", "column"), kv("props", obj()), kv("children", arr(field("nombreInterventions", "Nbre interventions", "num:#,##0"), field("tauxCompletion", "Taux de compl\u00e9tion", "num:#,##0.0 %"), field("notes", "Notes", null)))), obj(ks("type", "column"), kv("props", obj()), kv("children", arr(field("dateCreation", "Date de cr\u00e9ation", "date:yyyy-MM-dd"), field("dateDerniereModif", "Derni\u00e8re modification", "longdate:yyyy-MM-dd HH:mm")))))));

        // collapsible + ref: renders inline (< 15 fields) or as popup
        String adresseSection = obj(ks("type", "section"), kv("props", obj(ks("title", "Adresse"), ks("collapsible", "true"), ks("ref", "adresse"))), kv("children", arr(field("adresse.rue", "Rue", null), field("adresse.ville", "Ville", null), field("adresse.codePostal", "Code postal", null), field("adresse.province", "Province", null))));

        String spatialTabs = obj(ks("type", "tabs"), kv("props", obj(ks("title", "Donn\u00e9es spatiales"))), kv("children", arr(obj(ks("type", "tab"), kv("props", obj(ks("title", "Coordonn\u00e9es g\u00e9o"))), kv("children", arr(field("coordonnees.latitude", "Latitude", null), field("coordonnees.longitude", "Longitude", null), field("coordonnees.altitude", "Altitude (m)", "num:#,##0.0 m")))), obj(ks("type", "tab"), kv("props", obj(ks("title", "Dimensions"))), kv("children", arr(field("dimensions.longueur", "Longueur (m)", "num:#,##0.0 m"), field("dimensions.largeur", "Largeur (m)", "num:#,##0.0 m"), field("dimensions.profondeur", "Profondeur (m)", "num:#,##0.0 m"), field("dimensions.surface", "Surface (m\u00b2)", "num:#,##0.0 m\u00b2")))))));

        String dividerSmall = obj(ks("type", "divider"), kv("props", obj(ks("style", "small"))));

        // table: contacts has IDEntite typeContact column — regression for yellow-badge bug
        String contactsTable = obj(ks("type", "table"), kv("props", obj(ks("ref", "contacts"), ks("columns", "nom,telephone,courriel,typeContact"), ks("columnTitles", "Nom,T\u00e9l\u00e9phone,Courriel,Type de contact"))));

        // table: interventions with IDEntite auteur column
        String interventionsTable = obj(ks("type", "table"), kv("props", obj(ks("ref", "interventions"), ks("columns", "dateAction,typeAction,auteur,description"), ks("columnTitles", "Date,Type,Auteur,Description"))));

        // collapsible + ref: SECOND address adjacent to adresse → both become a tab group
        // via _renderCollapsibleSectionsAsTabs (fires when ≥2 adjacent collapsible+ref sections)
        String adresseLivraisonSection = obj(ks("type", "section"), kv("props", obj(ks("title", "Adresse de livraison"), ks("collapsible", "true"), ks("ref", "adresseLivraison"))), kv("children", arr(field("adresseLivraison.rue", "Rue", null), field("adresseLivraison.ville", "Ville", null), field("adresseLivraison.codePostal", "Code postal", null), field("adresseLivraison.province", "Province", null))));

        // collapsible section WITHOUT ref → always renders as <details> accordion
        String remarquesSection = obj(ks("type", "section"), kv("props", obj(ks("title", "Remarques"), ks("collapsible", "true"))), kv("children", arr(field("notes", "Notes", null), field("priorite", "Priorit\u00e9", null))));

        String statsSection = obj(ks("type", "section"), kv("props", obj(ks("title", "Statistiques"))), kv("children", arr(columnsNode)));

        return arr(infoSection, dividerFull, statsSection, adresseSection, adresseLivraisonSection, remarquesSection, spatialTabs, dividerSmall, contactsTable, interventionsTable);
    }

    /** Shorthand to build a {@code field} layout node. */
    private static String field(String ref, String label, String format) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"field\",\"props\":{");
        sb.append(ks("ref", ref)).append(',').append(ks("label", label));
        if (format != null)
            sb.append(',').append(ks("format", format));
        sb.append("}}");
        return sb.toString();
    }

    // ── Class layouts ─────────────────────────────────────────────────────────
    // Empty: popup objects fall back to automatic rendering.
    // Add entries here to test the CLASS_LAYOUTS popup-with-layout code path.

    private static String buildClassLayouts() {
        return "{}";
    }

    // ── Schema fields ─────────────────────────────────────────────────────────
    //
    // Produces SCHEMA_FIELDS consumed by viewer-schema.js:
    //   • schemaTitleByName  → field labels in results table and detail view
    //   • idEntiteFieldSet   → IDEntite inline rendering
    //   • pointsToByPath     → cross-page deep-link target entity name

    private static String buildSchemaFields() {
        return arr(sf("nom", "nom", "string", false, "Nom", null, null), sf("prenom", "prenom", "string", false, "Pr\u00e9nom", null, null), sf("actif", "actif", "bool", false, "Actif", null, null), sf("nombreInterventions", "nombreInterventions", "number", false, "Nombre d\u2019interventions", null, null), sf("description", "description", "string", false, "Description", null, null), sf("codeAcces", "codeAcces", "string", false, "Code d\u2019acc\u00e8s", null, null), sf("dateCreation", "dateCreation", "date", false, "Date de cr\u00e9ation", null, null), sf("dateDerniereModif", "dateDerniereModif", "date", false, "Derni\u00e8re modification", null, null), sf("tauxCompletion", "tauxCompletion", "number", false, "Taux de compl\u00e9tion (%)", null, null), sf("notes", "notes", "string", false, "Notes", null, null), sf("priorite", "priorite", "string", false, "Priorit\u00e9", null, null),
                // IDEntite field: pointsTo "TypeSysteme" — nav item href = ./TypeSysteme.html
                sf("typeSysteme", "typeSysteme", "reference", false, "Type de syst\u00e8me", "TypeSysteme", null),
                // Embedded address with named children so titles resolve by path
                sfEmbedded("adresse", "adresse", "Adresse", arr(sf("rue", "adresse.rue", "string", false, "Rue", null, null), sf("ville", "adresse.ville", "string", false, "Ville", null, null), sf("codePostal", "adresse.codePostal", "string", false, "Code postal", null, null), sf("province", "adresse.province", "string", false, "Province", null, null))), sfEmbedded("adresseLivraison", "adresseLivraison", "Adresse de livraison", arr(sf("rue", "adresseLivraison.rue", "string", false, "Rue", null, null), sf("ville", "adresseLivraison.ville", "string", false, "Ville", null, null), sf("codePostal", "adresseLivraison.codePostal", "string", false, "Code postal", null, null), sf("province", "adresseLivraison.province", "string", false, "Province", null, null))), sf("coordonnees", "coordonnees", "embedded", false, "Coordonn\u00e9es g\u00e9ographiques", null, null), sf("dimensions", "dimensions", "embedded", false, "Dimensions", null, null), sf("tags", "tags", "string", true, "\u00c9tiquettes", null, null),
                // Collection: contacts — typeContact is IDEntite with cross-link
                sfCollection("contacts", "contacts", "Contacts", arr(sf("nom", "contacts.nom", "string", false, "Nom", null, null), sf("telephone", "contacts.telephone", "string", false, "T\u00e9l\u00e9phone", null, null), sf("courriel", "contacts.courriel", "string", false, "Courriel", null, null), sf("typeContact", "contacts.typeContact", "reference", false, "Type de contact", "TypeContact", null))),
                // Collection: interventions — auteur is IDEntite with cross-link
                sfCollection("interventions", "interventions", "Interventions", arr(sf("dateAction", "interventions.dateAction", "string", false, "Date", null, null), sf("typeAction", "interventions.typeAction", "string", false, "Type", null, null), sf("auteur", "interventions.auteur", "reference", false, "Auteur", "Auteur", null), sf("description", "interventions.description", "string", false, "Description", null, null))));
    }

    /**
     * Builds a SCHEMA_FIELDS entry. {@code pointsTo} and {@code children} may be null.
     * When {@code pointsTo} is set the field is also flagged {@code idEntite:true}.
     */
    private static String sf(String name, String path, String type, boolean collection, String title, String pointsTo, String childrenJson) {
        StringBuilder sb = new StringBuilder("{");
        sb.append(ks("name", name)).append(',');
        sb.append(ks("path", path)).append(',');
        sb.append(ks("type", type)).append(',');
        sb.append(kv("collection", b(collection)));
        if (title != null)
            sb.append(',').append(ks("title", title));
        if (pointsTo != null)
            sb.append(",\"idEntite\":true,").append(ks("pointsTo", pointsTo));
        if (childrenJson != null)
            sb.append(',').append(kv("children", childrenJson));
        sb.append('}');
        return sb.toString();
    }

    private static String sfEmbedded(String name, String path, String title, String childrenJson) {
        return sf(name, path, "embedded", false, title, null, childrenJson);
    }

    private static String sfCollection(String name, String path, String title, String childrenJson) {
        return sf(name, path, "collection", true, title, null, childrenJson);
    }

    // ── Cross-refs ────────────────────────────────────────────────────────────
    //
    // Back-refs for record 1001: two entity types → tabbed section
    // Back-refs for record 1003: single entity type, two entries → single-group section

    private static String buildCrossRefs() {
        String ref1001 = arr(backRef("TypeSysteme", "Type de syst\u00e8me", "./TypeSysteme.html", "2001", "Syst\u00e8me principal"), backRef("TypeContact", "Type de contact", "./TypeContact.html", "3001", "Principal"), backRef("TypeContact", "Type de contact", "./TypeContact.html", "3002", "Secondaire"));
        String ref1003 = arr(backRef("Auteur", "Auteur", "./Auteur.html", "4001", "admin"), backRef("Auteur", "Auteur", "./Auteur.html", "4003", "tech01"));
        return obj(kv("1001", ref1001), kv("1003", ref1003));
    }

    private static String backRef(String entity, String label, String href, String id, String summary) {
        return obj(ks("entity", entity), ks("label", label), ks("href", href), ks("id", id), ks("summary", summary));
    }

    // ── Sample data ───────────────────────────────────────────────────────────

    private static String buildDataScript() {
        return "window.__m4o=" + buildDataJson() + ";";
    }

    private static String buildDataJson() {
        return obj(kv("export", obj(kv("objects", arr(record1(), record2(), record3())))));
    }

    /**
     * Record 1 — full data: every field present, all types exercised.
     * Opens 3 contacts (IDEntite typeContact) and 3 interventions (IDEntite auteur).
     */
    private static String record1() {
        return obj(ks("_class", "StyleGuideRecord"), ks("_id", "1001"), ks("_summary", "Alice Tremblay \u2014 ADM001"), ks("nom", "Tremblay"), ks("prenom", "Alice"), kv("actif", b(true)), kv("nombreInterventions", n(42)), ks("description", "Responsable principale du secteur A. G\u00e8re la planification des interventions de routine."), ks("codeAcces", "ADM001"), ks("dateCreation", "2023-01-15"), kv("dateDerniereModif", l(1699920000000L)), // long epoch ms ~ 2023-11-13
                kv("tauxCompletion", n(87.5)), ks("notes", "Responsable principale confirm\u00e9e pour le secteur A."), ks("priorite", "Haute"),
                // IDEntite reference WITH matching nav href → becomes dark-blue link button
                kv("typeSysteme", idEntite("2001", "Syst\u00e8me principal")), kv("adresse", obj(ks("rue", "123, rue Principale"), ks("ville", "Montr\u00e9al"), ks("codePostal", "H1A 1A1"), ks("province", "Qu\u00e9bec"))),
                // second address adjacent to adresse in layout → _renderCollapsibleSectionsAsTabs fires
                kv("adresseLivraison", obj(ks("rue", "55, boulevard Industriel"), ks("ville", "Longueuil"), ks("codePostal", "J4G 2M4"), ks("province", "Qu\u00e9bec"))), kv("coordonnees", obj(kv("latitude", n(45.5088)), kv("longitude", n(-73.5878)), kv("altitude", n(21.3)))), kv("dimensions", obj(kv("longueur", n(150.5)), kv("largeur", n(25.0)), kv("profondeur", n(3.2)), kv("surface", n(3762.5)))), kv("tags", arr(s("important"), s("urgent"), s("planifi\u00e9"))), kv("contacts", arr(contact("Jean Dupont", "514-555-0001", "jean@example.com", "3001", "Principal"), contact("Marie C\u00f4t\u00e9", "514-555-0002", "marie@example.com", "3002", "Secondaire"), contact("Paul Martin", "514-555-0003", null, "3001", "Principal"))), kv("interventions", arr(intervention("2023-03-10", "CREATION", "4001", "admin", "Intervention initiale de v\u00e9rification du syst\u00e8me."), intervention("2023-06-21", "MODIFICATION", "4002", "user01", "Mise \u00e0 jour des param\u00e8tres de configuration."), intervention("2024-02-14", "INSPECTION", "4001", "admin", "Inspection annuelle compl\u00e9t\u00e9e avec succ\u00e8s."))));
    }

    /**
     * Record 2 — sparse data: many absent/empty fields to confirm dash rendering
     * and graceful no-op for missing embedded objects / empty collections.
     */
    private static String record2() {
        return obj(ks("_class", "StyleGuideRecord"), ks("_id", "1002"), ks("_summary", "B\u00e2timent B \u2014 OPR002"), ks("nom", "B\u00e2timent B"), kv("actif", b(false)), kv("nombreInterventions", n(0)), ks("codeAcces", "OPR002"), ks("dateCreation", "2022-08-30"), kv("tauxCompletion", n(0)),
                // no typeSysteme, no adresseLivraison, no priorite → those sections suppressed (empty body)
                kv("adresse", obj(ks("rue", "456, avenue du Parc"), ks("ville", "Qu\u00e9bec"), ks("codePostal", "G1A 1A1"), ks("province", "Qu\u00e9bec"))),
                // no coordonnees → tab shows empty state
                kv("dimensions", obj(kv("longueur", n(45.0)), kv("largeur", n(12.5)), kv("surface", n(562.5))
                // profondeur absent → tab field renders as —
                )), kv("tags", arr()), // empty array
                kv("contacts", arr()), // empty collection → no table
                kv("interventions", arr(intervention("2022-09-01", "CREATION", "4001", "admin", "Saisie initiale."))));
    }

    /**
     * Record 3 — IDEntite regression record: contacts table has typeContact IDEntite column
     * that MUST render as dark-blue link buttons, not yellow badges.
     * Also exercises cross-refs (back-ref section shown at bottom of detail).
     */
    private static String record3() {
        return obj(ks("_class", "StyleGuideRecord"), ks("_id", "1003"), ks("_summary", "Point de contr\u00f4le \u2014 r\u00e9seau B"), ks("nom", "Point B-03"), kv("actif", b(true)), kv("nombreInterventions", n(7)), ks("description", "Point de contr\u00f4le sur le r\u00e9seau B. Plusieurs types d\u2019interventions document\u00e9es."), ks("codeAcces", "RES003"), ks("dateCreation", "2021-05-12"), kv("dateDerniereModif", l(1714950000000L)), // long epoch ms ~ 2024-05-06
                kv("tauxCompletion", n(63.2)), ks("notes", "V\u00e9rification trimestrielle requise."), ks("priorite", "Moyenne"),
                // IDEntite WITHOUT a matching nav href (TypeSysteme2 not in nav) → plain text fallback
                kv("typeSysteme", idEntite("2002", "Syst\u00e8me secondaire")), kv("adresse", obj(ks("rue", "789, boulevard des Sources"), ks("ville", "Laval"), ks("codePostal", "H7V 3Y3"), ks("province", "Qu\u00e9bec"))), kv("coordonnees", obj(kv("latitude", n(45.5623)), kv("longitude", n(-73.7451)), kv("altitude", n(15.8)))), kv("dimensions", obj(kv("longueur", n(82.5)), kv("largeur", n(18.0)), kv("profondeur", n(2.1)), kv("surface", n(1485.0)))), kv("tags", arr(s("contr\u00f4le"), s("r\u00e9seau-B"), s("haute-priorit\u00e9"))),
                // contacts: typeContact IDEntite column with deep-links (regression test)
                kv("contacts", arr(contact("Sophie Laforce", "418-555-0011", "sophie@example.com", "3003", "Technique"), contact("\u00c9ric B\u00e9langer", "418-555-0022", "eric@example.com", "3002", "Secondaire"))), kv("interventions", arr(intervention("2024-01-05", "INSPECTION", "4003", "tech01", "Premier contr\u00f4le trimestriel de 2024."), intervention("2024-04-10", "REPARATION", "4003", "tech01", "Remplacement de la vanne principale."), intervention("2023-11-22", "INSPECTION", "4001", "admin", "Contr\u00f4le de fin d\u2019ann\u00e9e."))));
    }

    // ── Data helpers ──────────────────────────────────────────────────────────

    /** Builds an IDEntite reference object with {@code _id}, {@code _summary}, {@code _label}. */
    private static String idEntite(String id, String label) {
        return obj(ks("_id", id), ks("_summary", label), ks("_label", label));
    }

    /** Builds a contact object with an IDEntite typeContact field. */
    private static String contact(String nom, String telephone, String courriel, String typeId, String typeLabel) {
        StringBuilder sb = new StringBuilder("{");
        sb.append(ks("nom", nom)).append(',');
        sb.append(ks("telephone", telephone)).append(',');
        if (courriel != null)
            sb.append(ks("courriel", courriel)).append(',');
        sb.append(kv("typeContact", idEntite(typeId, typeLabel)));
        sb.append('}');
        return sb.toString();
    }

    /** Builds an intervention object with an IDEntite auteur field. */
    private static String intervention(String date, String type, String auteurId, String auteurLabel, String description) {
        return obj(ks("dateAction", date), ks("typeAction", type), kv("auteur", idEntite(auteurId, auteurLabel)), ks("description", description));
    }
}
