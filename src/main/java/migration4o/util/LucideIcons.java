package migration4o.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides pre-built Lucide-icon SVG strings for embedding in the HTML viewer's
 * navigation sidebar. Icons are inline SVG — no external files or CDN required.
 *
 * <p>Usage: {@code LucideIcons.getSvg("fire-truck")} returns a ready-to-embed
 * {@code <svg>…</svg>} string, or a default fallback icon if the name is not
 * found.</p>
 *
 * <p>Icon names follow the Lucide naming convention (kebab-case).
 * Users can browse available names at <a href="https://lucide.dev">lucide.dev</a>.</p>
 */
public final class LucideIcons {

    private LucideIcons() {
    }

    private static final String FALLBACK = "layers";

    /** SVG attribute block shared by all icons (20×20, stroke-based). */
    private static final String HDR = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"20\" height=\"20\" " + "viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" " + "stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\">";

    private static final Map<String, String> ICONS = new HashMap<>();

    static {
        // ── Emergency / fire-service ──────────────────────────────────────────
        reg("flame", "<path d=\"M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z\"/>");

        reg("brick-wall-fire", "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-brick-wall-fire-icon lucide-brick-wall-fire\"><path d=\"M16 3v2.107\"/><path d=\"M17 9c1 3 2.5 3.5 3.5 4.5A5 5 0 0 1 22 17a5 5 0 0 1-10 0c0-.3 0-.6.1-.9a2 2 0 1 0 3.3-2C13 11.5 16 9 17 9\"/><path d=\"M21 8.274V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h3.938\"/><path d=\"M3 15h5.253\"/><path d=\"M3 9h8.228\"/><path d=\"M8 15v6\"/><path d=\"M8 3v6\"/></svg>");

        reg("fire-extinguisher", "<path d=\"M15 6v-3\"/>" + "<path d=\"M9 18v-4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H11a2 2 0 0 1-2-2z\"/>" + "<path d=\"M9 10V8a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2\"/>" + "<path d=\"M6 6h3\"/>" + "<circle cx=\"15\" cy=\"3\" r=\"1\"/>");

        reg("siren", "<path d=\"M7 12a5 5 0 0 1 5-5v0a5 5 0 0 1 5 5v6H7v-6z\"/>" + "<path d=\"M5 20h14\"/>" + "<path d=\"M12 7V3\"/>" + "<path d=\"M6.6 5.6 4.5 3.5\"/>" + "<path d=\"M17.4 5.6l2.1-2.1\"/>");

        // ── Organisation / people ─────────────────────────────────────────────
        reg("users", "<path d=\"M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2\"/>" + "<circle cx=\"9\" cy=\"7\" r=\"4\"/>" + "<path d=\"M22 21v-2a4 4 0 0 0-3-3.87\"/>" + "<path d=\"M16 3.13a4 4 0 0 1 0 7.75\"/>");

        reg("user", "<path d=\"M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2\"/>" + "<circle cx=\"12\" cy=\"7\" r=\"4\"/>");

        reg("user-check", "<path d=\"M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2\"/>" + "<circle cx=\"9\" cy=\"7\" r=\"4\"/>" + "<polyline points=\"16 11 18 13 22 9\"/>");

        reg("hard-hat", "<path d=\"M2 18a1 1 0 0 0 1 1h18a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1H3a1 1 0 0 0-1 1v2z\"/>" + "<path d=\"M10 10V5a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1v5\"/>" + "<path d=\"M4 15l-.5-2.5A7 7 0 0 1 12 5a7 7 0 0 1 8.5 7.5L20 15\"/>");

        // ── Building / territory ──────────────────────────────────────────────
        reg("building-2", "<path d=\"M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18\"/>" + "<path d=\"M6 12H4a2 2 0 0 0-2 2v6a2 2 0 0 0 2 2h2\"/>" + "<path d=\"M18 9h2a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-2\"/>" + "<path d=\"M10 6h4\"/>" + "<path d=\"M10 10h4\"/>" + "<path d=\"M10 14h4\"/>" + "<path d=\"M10 18h4\"/>");

        reg("landmark", "<line x1=\"3\" y1=\"22\" x2=\"21\" y2=\"22\"/>" + "<line x1=\"6\" y1=\"18\" x2=\"6\" y2=\"11\"/>" + "<line x1=\"10\" y1=\"18\" x2=\"10\" y2=\"11\"/>" + "<line x1=\"14\" y1=\"18\" x2=\"14\" y2=\"11\"/>" + "<line x1=\"18\" y1=\"18\" x2=\"18\" y2=\"11\"/>" + "<polygon points=\"12 2 20 7 4 7\"/>");

        reg("home", "<path d=\"m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z\"/>" + "<polyline points=\"9 22 9 12 15 12 15 22\"/>");

        // ── Map / territory ────────────────────────────────────────────────
        reg("map", "<polygon points=\"3 6 9 3 15 6 21 3 21 18 15 21 9 18 3 21\"/>" + "<line x1=\"9\" y1=\"3\" x2=\"9\" y2=\"18\"/>" + "<line x1=\"15\" y1=\"6\" x2=\"15\" y2=\"21\"/>");

        reg("map-pin", "<path d=\"M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0z\"/>" + "<circle cx=\"12\" cy=\"10\" r=\"3\"/>");

        // ── Operations / intervention ─────────────────────────────────────────
        reg("clipboard-list", "<rect x=\"8\" y=\"2\" width=\"8\" height=\"4\" rx=\"1\" ry=\"1\"/>" + "<path d=\"M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2\"/>" + "<path d=\"M12 11h4\"/>" + "<path d=\"M12 16h4\"/>" + "<path d=\"M8 11h.01\"/>" + "<path d=\"M8 16h.01\"/>");

        reg("file-text", "<path d=\"M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z\"/>" + "<polyline points=\"14 2 14 8 20 8\"/>" + "<line x1=\"16\" y1=\"13\" x2=\"8\" y2=\"13\"/>" + "<line x1=\"16\" y1=\"17\" x2=\"8\" y2=\"17\"/>" + "<polyline points=\"10 9 9 9 8 9\"/>");

        reg("activity", "<polyline points=\"22 12 18 12 15 21 9 3 6 12 2 12\"/>");

        reg("alert-triangle", "<path d=\"m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3z\"/>" + "<path d=\"M12 9v4\"/>" + "<path d=\"M12 17h.01\"/>");

        // ── Calendar / planning ────────────────────────────────────────────
        reg("calendar", "<path d=\"M8 2v4\"/>" + "<path d=\"M16 2v4\"/>" + "<rect x=\"3\" y=\"4\" width=\"18\" height=\"18\" rx=\"2\"/>" + "<path d=\"M3 10h18\"/>");

        reg("clock", "<circle cx=\"12\" cy=\"12\" r=\"10\"/>" + "<polyline points=\"12 6 12 12 16 14\"/>");

        // ── Vehicles / equipment ───────────────────────────────────────────
        reg("truck", "<path d=\"M5 17H3a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v9a2 2 0 0 1-2 2h-3\"/>" + "<circle cx=\"7.5\" cy=\"17.5\" r=\"2.5\"/>" + "<circle cx=\"17.5\" cy=\"17.5\" r=\"2.5\"/>");

        reg("car", "<path d=\"M19 17H5v-3a7 7 0 0 1 14 0v3z\"/>" + "<path d=\"M5 17H3a2 2 0 0 1-2-2v-1.5\"/>" + "<path d=\"M19 17h2a2 2 0 0 0 2-2v-1.5\"/>" + "<circle cx=\"7.5\" cy=\"17.5\" r=\"1.5\"/>" + "<circle cx=\"16.5\" cy=\"17.5\" r=\"1.5\"/>");

        reg("wrench", "<path d=\"M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z\"/>");

        // ── Prevention / safety ────────────────────────────────────────────
        reg("shield", "<path d=\"M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z\"/>");

        reg("shield-check", "<path d=\"M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z\"/>" + "<path d=\"m9 12 2 2 4-4\"/>");

        reg("lock", "<rect x=\"3\" y=\"11\" width=\"18\" height=\"11\" rx=\"2\" ry=\"2\"/>" + "<path d=\"M7 11V7a5 5 0 0 1 10 0v4\"/>");

        // ── Resources / material ───────────────────────────────────────────
        reg("box", "<path d=\"M21 8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16Z\"/>" + "<path d=\"m3.3 7 8.7 5 8.7-5\"/>" + "<path d=\"M12 22V12\"/>");

        reg("package", "<path d=\"M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z\"/>" + "<path d=\"M12 22V12\"/>" + "<path d=\"m3.3 7 8.7 5 8.7-5\"/>" + "<path d=\"m7.5 4.27 9 5.15\"/>");

        reg("layers", "<polygon points=\"12 2 2 7 12 12 22 7 12 2\"/>" + "<polyline points=\"2 17 12 22 22 17\"/>" + "<polyline points=\"2 12 12 17 22 12\"/>");

        // ── Documents ─────────────────────────────────────────────────────
        reg("folder", "<path d=\"M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z\"/>");

        reg("archive", "<rect x=\"2\" y=\"4\" width=\"20\" height=\"5\" rx=\"2\"/>" + "<path d=\"M4 9v9a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9\"/>" + "<path d=\"M10 13h4\"/>");

        // ── Communication / messaging ─────────────────────────────────────
        reg("mail", "<rect x=\"2\" y=\"4\" width=\"20\" height=\"16\" rx=\"2\"/>" + "<path d=\"m22 7-8.97 5.7a1.94 1.94 0 0 1-2.06 0L2 7\"/>");

        reg("bell", "<path d=\"M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9\"/>" + "<path d=\"M10.3 21a1.94 1.94 0 0 0 3.4 0\"/>");

        reg("message-square", "<path d=\"M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z\"/>");

        // ── Data / reports ─────────────────────────────────────────────────
        reg("database", "<ellipse cx=\"12\" cy=\"5\" rx=\"9\" ry=\"3\"/>" + "<path d=\"M3 5V19a9 3 0 0 0 18 0V5\"/>" + "<path d=\"M3 12a9 3 0 0 0 18 0\"/>");

        reg("bar-chart-2", "<line x1=\"18\" y1=\"20\" x2=\"18\" y2=\"10\"/>" + "<line x1=\"12\" y1=\"20\" x2=\"12\" y2=\"4\"/>" + "<line x1=\"6\" y1=\"20\" x2=\"6\" y2=\"14\"/>");

        reg("briefcase", "<rect x=\"2\" y=\"7\" width=\"20\" height=\"14\" rx=\"2\" ry=\"2\"/>" + "<path d=\"M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16\"/>");

        // ── Settings / administration ──────────────────────────────────────
        reg("settings", "<circle cx=\"12\" cy=\"12\" r=\"3\"/>" + "<path d=\"M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z\"/>");

        reg("sliders", "<line x1=\"4\" y1=\"21\" x2=\"4\" y2=\"14\"/>" + "<line x1=\"4\" y1=\"10\" x2=\"4\" y2=\"3\"/>" + "<line x1=\"12\" y1=\"21\" x2=\"12\" y2=\"12\"/>" + "<line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"3\"/>" + "<line x1=\"20\" y1=\"21\" x2=\"20\" y2=\"16\"/>" + "<line x1=\"20\" y1=\"12\" x2=\"20\" y2=\"3\"/>" + "<line x1=\"1\" y1=\"14\" x2=\"7\" y2=\"14\"/>" + "<line x1=\"9\" y1=\"8\" x2=\"15\" y2=\"8\"/>" + "<line x1=\"17\" y1=\"16\" x2=\"23\" y2=\"16\"/>");

        reg("key", "<circle cx=\"7.5\" cy=\"15.5\" r=\"5.5\"/>" + "<path d=\"m21 2-9.6 9.6\"/>" + "<path d=\"m15.5 7.5 3 3L22 7l-3-3\"/>");

        // ── Miscellaneous ─────────────────────────────────────────────────
        reg("list", "<line x1=\"8\" y1=\"6\" x2=\"21\" y2=\"6\"/>" + "<line x1=\"8\" y1=\"12\" x2=\"21\" y2=\"12\"/>" + "<line x1=\"8\" y1=\"18\" x2=\"21\" y2=\"18\"/>" + "<line x1=\"3\" y1=\"6\" x2=\"3.01\" y2=\"6\"/>" + "<line x1=\"3\" y1=\"12\" x2=\"3.01\" y2=\"12\"/>" + "<line x1=\"3\" y1=\"18\" x2=\"3.01\" y2=\"18\"/>");

        reg("grid", "<rect x=\"3\" y=\"3\" width=\"7\" height=\"7\"/>" + "<rect x=\"14\" y=\"3\" width=\"7\" height=\"7\"/>" + "<rect x=\"14\" y=\"14\" width=\"7\" height=\"7\"/>" + "<rect x=\"3\" y=\"14\" width=\"7\" height=\"7\"/>");

        reg("star", "<polygon points=\"12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2\"/>");

        reg("tag", "<path d=\"M12 2H2v10l9.29 9.29c.94.94 2.48.94 3.42 0l6.58-6.58c.94-.94.94-2.48 0-3.42L12 2Z\"/>" + "<path d=\"M7 7h.01\"/>");

        reg("search", "<circle cx=\"11\" cy=\"11\" r=\"8\"/>" + "<path d=\"m21 21-4.35-4.35\"/>");

        reg("zap", "<polygon points=\"13 2 3 14 12 14 11 22 21 10 12 10 13 2\"/>");

        reg("info", "<circle cx=\"12\" cy=\"12\" r=\"10\"/>" + "<path d=\"M12 16v-4\"/>" + "<path d=\"M12 8h.01\"/>");

        reg("phone", "<path d=\"M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.15 12a19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 3 1.18h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L7.09 9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 21 16.92z\"/>");
    }

    private static void reg(String name, String paths) {
        ICONS.put(name, HDR + paths + "</svg>");
    }

    /**
     * Returns the inline SVG string for the given Lucide icon name.
     * Falls back to the {@value #FALLBACK} icon if the name is not found.
     *
     * @param name Lucide icon name in kebab-case (e.g. {@code "fire-truck"})
     * @return A complete {@code <svg>…</svg>} HTML string
     */
    public static String getSvg(String name) {
        if (name != null && !name.isBlank()) {
            String svg = ICONS.get(name.trim().toLowerCase());
            if (svg != null)
                return svg;
        }
        return ICONS.getOrDefault(FALLBACK, HDR + "<polygon points=\"12 2 2 7 12 12 22 7 12 2\"/>" + "<polyline points=\"2 17 12 22 22 17\"/><polyline points=\"2 12 12 17 22 12\"/></svg>");
    }

    /**
     * Returns true if the given icon name is in the bundled set.
     */
    public static boolean isKnown(String name) {
        return name != null && ICONS.containsKey(name.trim().toLowerCase());
    }

    /**
     * Returns an unmodifiable view of all available icon names.
     */
    public static java.util.Set<String> availableNames() {
        return java.util.Collections.unmodifiableSet(ICONS.keySet());
    }
}
