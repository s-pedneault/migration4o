package migration4o.util;

import java.nio.file.Path;
import java.util.List;

import migration4o.migration.OrganizationInfo;

/**
 * Encapsulates all parameters required to render an {@code index.html} welcome page via
 * {@link JsViewerHtmlGenerator#writeWelcomePage(WelcomePageParams)}.
 *
 * @param dbRoot               Destination directory for {@code index.html} and assets.
 * @param dbName               Human-readable export name shown in the page header.
 * @param navItemsJson         Serialised NAV_ITEMS JSON array.
 * @param moduleCount          Total number of exported modules (including sub-modules).
 * @param classCount           Number of exported class data files.
 * @param objectCount          Total number of exported objects; 0 renders an em-dash.
 * @param municipality         Optional client municipality info; may be {@code null}.
 * @param organizations        Organizations to display as tiles (empty list = no tiles).
 */
public record WelcomePageParams(Path dbRoot, String dbName, String navItemsJson, int moduleCount, int classCount, int objectCount, MunicipalityInfo municipality, List<OrganizationInfo> organizations) {

    public WelcomePageParams {
        if (dbRoot == null) {
            throw new IllegalArgumentException("dbRoot must not be null");
        }
        organizations = organizations != null ? List.copyOf(organizations) : List.of();
    }
}
