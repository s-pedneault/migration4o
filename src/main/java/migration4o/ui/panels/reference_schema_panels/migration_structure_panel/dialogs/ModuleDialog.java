package migration4o.ui.panels.reference_schema_panels.migration_structure_panel.dialogs;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.Window;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Dialog for creating or editing migration modules.
 *
 * Provides fields for name, ID, Lucide icon name, tile background color,
 * tile text color, tile icon color, and tile font size.  The three color
 * fields use a JColorChooser swatch button; font-size uses a combo-box.
 */
public class ModuleDialog extends migration4o.ui.common.dialogs.BaseFormDialog {

    // ── Form fields ────────────────────────────────────────────────────────
    private JTextField nameField;
    private JTextField idField;
    private JTextField iconField;
    private JComboBox<String> tileFontSizeCombo;

    // ── Color state (null = "Auto") ────────────────────────────────────────
    private Color tileBgColor = null;
    private Color tileTextColorVal = null;
    private Color tileIconColorVal = null;

    // ── Color swatch buttons ───────────────────────────────────────────────
    private JButton tileBgBtn;
    private JButton tileTextColorBtn;
    private JButton tileIconColorBtn;

    // ── Font-size options ──────────────────────────────────────────────────
    private static final String[] FONT_VALUES = { null, "12", "13", "14", "15", "16" };
    private static final String[] FONT_LABELS = { "Default (14 px)", "12 px", "13 px", "14 px", "15 px", "16 px" };

    // ── Constructors ───────────────────────────────────────────────────────

    public ModuleDialog(Window owner, String title, String initialName, String initialId) {
        this(owner, title, initialName, initialId, null, null, null, null, null);
    }

    public ModuleDialog(Window owner, String title, String initialName, String initialId, String initialIcon) {
        this(owner, title, initialName, initialId, initialIcon, null, null, null, null);
    }

    public ModuleDialog(Window owner, String title, String initialName, String initialId, String initialIcon, String initialTileBg, String initialTileTextColor, String initialTileIconColor, String initialTileFontSize) {
        super(owner, title);

        if (initialName != null)
            nameField.setText(initialName);
        if (initialId != null)
            idField.setText(initialId);
        if (initialIcon != null)
            iconField.setText(initialIcon);

        // Parse initial colors
        tileBgColor = parseHex(initialTileBg);
        tileTextColorVal = parseHex(initialTileTextColor);
        tileIconColorVal = parseHex(initialTileIconColor);
        updateColorButton(tileBgBtn, tileBgColor, "Tile Background");
        updateColorButton(tileTextColorBtn, tileTextColorVal, "Tile Text");
        updateColorButton(tileIconColorBtn, tileIconColorVal, "Tile Icon");

        // Select matching font-size entry
        for (int i = 0; i < FONT_VALUES.length; i++) {
            if (valuesMatch(FONT_VALUES[i], initialTileFontSize)) {
                tileFontSizeCombo.setSelectedIndex(i);
                break;
            }
        }

        SwingUtilities.invokeLater(() -> nameField.requestFocusInWindow());
    }

    // ── Color helpers ──────────────────────────────────────────────────────

    private static Color parseHex(String hex) {
        if (hex == null || hex.isBlank())
            return null;
        try {
            return Color.decode(hex.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String colorToHex(Color c) {
        if (c == null)
            return null;
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * Updates a swatch button to reflect the given color.
     * When color is null the button shows "Auto" with a neutral background.
     */
    private static void updateColorButton(JButton btn, Color c, String label) {
        if (c == null) {
            btn.setBackground(new Color(0xE0E0E0));
            btn.setForeground(Color.DARK_GRAY);
            btn.setText("Auto");
        } else {
            btn.setBackground(c);
            double luma = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
            btn.setForeground(luma > 128 ? Color.BLACK : Color.WHITE);
            btn.setText(colorToHex(c));
        }
    }

    /**
     * Creates a flow panel with a swatch button (opens JColorChooser) and a
     * "Reset" button to clear back to "Auto".
     */
    private JPanel makeColorRow(String chooserTitle, JButton swatchBtn, Runnable onReset) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        row.setOpaque(false);

        swatchBtn.setPreferredSize(new Dimension(130, 24));
        swatchBtn.setOpaque(true);
        swatchBtn.setBorderPainted(true);
        swatchBtn.setBorder(BorderFactory.createLineBorder(new Color(0xAAAAAA)));
        swatchBtn.setFocusPainted(false);

        swatchBtn.addActionListener(e -> {
            String currentHex = swatchBtn.getText();
            Color current = parseHex(currentHex.startsWith("#") ? currentHex : null);
            Color chosen = JColorChooser.showDialog(swatchBtn, chooserTitle, current != null ? current : Color.WHITE);
            if (chosen != null) {
                if (swatchBtn == tileBgBtn) {
                    tileBgColor = chosen;
                    updateColorButton(tileBgBtn, tileBgColor, "Tile Background");
                } else if (swatchBtn == tileTextColorBtn) {
                    tileTextColorVal = chosen;
                    updateColorButton(tileTextColorBtn, tileTextColorVal, "Tile Text");
                } else if (swatchBtn == tileIconColorBtn) {
                    tileIconColorVal = chosen;
                    updateColorButton(tileIconColorBtn, tileIconColorVal, "Tile Icon");
                }
            }
        });

        JButton resetBtn = new JButton("Reset");
        resetBtn.setFont(resetBtn.getFont().deriveFont(10f));
        resetBtn.setPreferredSize(new Dimension(55, 24));
        resetBtn.setFocusPainted(false);
        resetBtn.addActionListener(e -> onReset.run());

        row.add(swatchBtn);
        row.add(resetBtn);
        return row;
    }

    // ── Form ───────────────────────────────────────────────────────────────

    @Override
    protected JPanel buildFormPanel() {
        JPanel panel = createGridBagFormPanel();
        GridBagConstraints gbc = createFormConstraints();

        nameField = new JTextField("", 20);
        idField = new JTextField("", 20);
        iconField = new JTextField("", 20);

        addFormRow(panel, gbc, "Module Name:", nameField);
        addFormRow(panel, gbc, "Module ID:", idField);
        addFormRow(panel, gbc, "Icon (Lucide name):", iconField);

        // Icon hint
        JLabel hintLabel = new JLabel("<html><font color='gray' size='-2'>e.g. flame, building-2, clipboard-list \u2014 browse at lucide.dev</font></html>");
        GridBagConstraints hintGbc = (GridBagConstraints) gbc.clone();
        hintGbc.gridx = 1;
        hintGbc.gridy++;
        panel.add(hintLabel, hintGbc);

        // ── Tile color buttons ─────────────────────────────────────────────
        tileBgBtn = new JButton("Auto");
        tileTextColorBtn = new JButton("Auto");
        tileIconColorBtn = new JButton("Auto");

        updateColorButton(tileBgBtn, null, "Tile Background");
        updateColorButton(tileTextColorBtn, null, "Tile Text");
        updateColorButton(tileIconColorBtn, null, "Tile Icon");

        addFormRow(panel, gbc, "Tile Background:", makeColorRow("Choose tile background color", tileBgBtn, () -> {
            tileBgColor = null;
            updateColorButton(tileBgBtn, null, "Tile Background");
        }));

        addFormRow(panel, gbc, "Tile Text Color:", makeColorRow("Choose tile text/label color", tileTextColorBtn, () -> {
            tileTextColorVal = null;
            updateColorButton(tileTextColorBtn, null, "Tile Text");
        }));

        addFormRow(panel, gbc, "Tile Icon Color:", makeColorRow("Choose tile icon color", tileIconColorBtn, () -> {
            tileIconColorVal = null;
            updateColorButton(tileIconColorBtn, null, "Tile Icon");
        }));

        // ── Font size combo ────────────────────────────────────────────────
        tileFontSizeCombo = new JComboBox<>(FONT_LABELS);
        tileFontSizeCombo.setSelectedIndex(0);
        addFormRow(panel, gbc, "Label Font Size:", tileFontSizeCombo);

        return panel;
    }

    // ── Validation ─────────────────────────────────────────────────────────

    @Override
    protected boolean validateInput() {
        if (nameField.getText().trim().isEmpty()) {
            showValidationError("Module name cannot be empty", nameField);
            return false;
        }
        if (idField.getText().trim().isEmpty()) {
            showValidationError("Module ID cannot be empty", idField);
            return false;
        }
        return true;
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public String getModuleName() {
        return nameField.getText().trim();
    }

    public String getModuleId() {
        return idField.getText().trim();
    }

    /** Returns the Lucide icon name, or null if empty. */
    public String getIcon() {
        String v = iconField.getText().trim();
        return v.isEmpty() ? null : v;
    }

    /** Returns the hex tile background color (e.g. "#e8f0fe") or null for auto. */
    public String getTileBg() {
        return colorToHex(tileBgColor);
    }

    /** Returns the hex tile text/label color or null for auto. */
    public String getTileTextColor() {
        return colorToHex(tileTextColorVal);
    }

    /** Returns the hex tile icon color or null for auto. */
    public String getTileIconColor() {
        return colorToHex(tileIconColorVal);
    }

    /** Returns the tile font-size string (e.g. "14"), or null for default. */
    public String getTileFontSize() {
        int idx = tileFontSizeCombo.getSelectedIndex();
        return (idx >= 0 && idx < FONT_VALUES.length) ? FONT_VALUES[idx] : null;
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private static boolean valuesMatch(String a, String b) {
        if (a == null && b == null)
            return true;
        if (a == null || b == null)
            return false;
        return a.equals(b);
    }
}
