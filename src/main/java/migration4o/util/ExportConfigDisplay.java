package migration4o.util;

import java.util.List;

/**
 * Read-only summary of the export configuration for display on the welcome page.
 *
 * @param fullContents   {@code true} when exporting all objects (no cap or seed filter).
 * @param filesInFolder  {@code true} when file attachments go into a {@code file/} folder.
 * @param singleService  {@code true} when exporting as a single service (not per-org).
 * @param exclusions     Available field exclusion options with their selected state.
 */
public record ExportConfigDisplay(boolean fullContents, boolean filesInFolder, boolean singleService, List<ExclusionOption> exclusions) {

    /**
     * One user-facing field exclusion toggle.
     *
     * @param label    Human-readable label (e.g. "Exclude employee birthdays?").
     * @param selected {@code true} when this exclusion was active during the export.
     */
    public record ExclusionOption(String label, boolean selected) {
    }

    public ExportConfigDisplay {
        exclusions = exclusions != null ? List.copyOf(exclusions) : List.of();
    }
}
