package migration4o.ui.panels.database_panels.cost_panel;

import migration4o.migration.OrganizationInfo;

import java.awt.*;
import java.util.List;

/**
 * Describes the dynamic column structure of the cost table.
 * <p>
 * Layout: {@code Class | Unit Cost | [Général | Subtotal] | [Org1 | Subtotal] | … | [Total | Total cost]}
 * <p>
 * Each "pair" is two columns: a units column and its corresponding subtotal. Pair 0 = Général, pairs 1..N = per-organization, last pair = totals.
 */
final class CostColumnLayout {

    private static final int FIXED_COLUMN_COUNT = 2; // Class + Unit Cost
    private static final Color LIGHT_GREY_BG = new Color(245, 245, 245);
    private static final Color WHITE_BG = Color.WHITE;
    private static final Color LIGHT_BLUE_BG = new Color(230, 240, 255);

    private final List<OrganizationInfo> organizations;

    CostColumnLayout(List<OrganizationInfo> organizations) {
        this.organizations = organizations;
    }

    /** Number of column pairs: 1 (Général) + N (organizations) + 1 (Total). */
    int pairCount() {
        return 1 + organizations.size() + 1;
    }

    /** Total number of table columns. */
    int columnCount() {
        return FIXED_COLUMN_COUNT + pairCount() * 2;
    }

    /** Returns the column name for the given column index. */
    String columnName(int col) {
        if (col == 0)
            return "Class";
        if (col == 1)
            return "Unit Cost";
        PairPosition pos = resolvePair(col);
        if (pos.isGeneralPair()) {
            return pos.isSubtotal ? "Subtotal" : "Général";
        } else if (pos.isOrganizationPair()) {
            return pos.isSubtotal ? "Subtotal" : organizations.get(pos.pairIndex - 1).name();
        } else {
            return pos.isSubtotal ? "Total cost" : "Total";
        }
    }

    /** Returns the background colour for a given column index. */
    Color columnBackground(int col) {
        if (col < FIXED_COLUMN_COUNT)
            return WHITE_BG;
        PairPosition pos = resolvePair(col);
        if (pos.isTotalPair())
            return LIGHT_BLUE_BG;
        return (pos.pairIndex % 2 == 0) ? LIGHT_GREY_BG : WHITE_BG;
    }

    /**
     * Resolves a column index (≥ {@value FIXED_COLUMN_COUNT}) into its pair position: which pair, and whether it is the subtotal column of the pair.
     */
    PairPosition resolvePair(int col) {
        int offset = col - FIXED_COLUMN_COUNT;
        int pairIndex = offset / 2;
        boolean isSubtotal = offset % 2 == 1;
        return new PairPosition(pairIndex, isSubtotal, pairCount());
    }

    /**
     * Identifies a column's position within the pair layout.
     */
    record PairPosition(int pairIndex, boolean isSubtotal, int totalPairs) {
        boolean isGeneralPair() {
            return pairIndex == 0;
        }

        boolean isOrganizationPair() {
            return pairIndex > 0 && pairIndex < totalPairs - 1;
        }

        boolean isTotalPair() {
            return pairIndex == totalPairs - 1;
        }

        /** Returns the organization index (0-based) for org pairs. */
        int organizationIndex() {
            return pairIndex - 1;
        }
    }
}
