package migration4o.ui.theme;

import java.awt.Color;
import java.awt.Window;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Manages application-wide light/dark theme.
 * <p>
 * Applies overrides to {@link UIManager} so that all Swing components pick up
 * the correct palette, then notifies registered listeners so that panels with
 * hard-coded colours can react.
 */
public class ThemeManager {

    private static final ThemeManager INSTANCE = new ThemeManager();
    private static final String PREFS_FILE = "local/.ui-prefs.properties";

    private boolean dark = false;
    private final List<Runnable> listeners = new ArrayList<>();

    private ThemeManager() {
        Properties prefs = new Properties();
        try (Reader r = new FileReader(PREFS_FILE)) {
            prefs.load(r);
            dark = "dark".equals(prefs.getProperty("theme", "light"));
        } catch (IOException ignored) {
            // No saved prefs — stay in light mode
        }
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    // ── State ─────────────────────────────────────────────────────────────────

    public boolean isDark() {
        return dark;
    }

    /**
     * Switches the theme, refreshes all open windows, and notifies listeners.
     * Safe to call from any thread; UI work is dispatched to the EDT.
     */
    public void setDark(boolean dark) {
        this.dark = dark;
        savePreference();
        applyToUIManager();
        // Refresh every open window so UIManager-backed components update
        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
        // Then let panels with hard-coded colours fix themselves
        listeners.forEach(Runnable::run);
    }

    /**
     * Applies the current theme's UIManager overrides — call this once before
     * any UI is created for startup initialisation.
     */
    public void applyToUIManager() {
        if (dark) {
            applyDark();
        } else {
            applyLight();
        }
    }

    /** Registers a callback invoked every time the theme changes. */
    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    // ── Palette helpers ───────────────────────────────────────────────────────

    public Color getBackground() {
        return dark ? new Color(30, 30, 30) : Color.WHITE;
    }

    public Color getSurface() {
        return dark ? new Color(43, 43, 43) : new Color(244, 244, 245);
    }

    public Color getForeground() {
        return dark ? new Color(212, 212, 212) : new Color(11, 18, 32);
    }

    public Color getSubtleForeground() {
        return dark ? new Color(130, 130, 130) : new Color(100, 116, 139);
    }

    public Color getInputBackground() {
        return dark ? new Color(50, 50, 52) : Color.WHITE;
    }

    public Color getBorder() {
        return dark ? new Color(68, 68, 68) : new Color(200, 200, 200);
    }

    public Color getSelectionBackground() {
        return dark ? new Color(38, 79, 120) : new Color(37, 99, 235);
    }

    public Color getAccent() {
        return new Color(37, 99, 235);
    }

    // ── UIManager palettes ────────────────────────────────────────────────────

    private void applyDark() {
        Color bg = new Color(30, 30, 30);
        Color surface = new Color(43, 43, 43);
        Color fg = new Color(212, 212, 212);
        Color input = new Color(50, 50, 52);
        Color sel = new Color(38, 79, 120);
        Color border = new Color(68, 68, 68);
        Color accent = new Color(37, 99, 235);
        Color disabled = new Color(110, 110, 110);

        UIManager.put("Panel.background", surface);
        UIManager.put("Panel.foreground", fg);
        UIManager.put("Label.foreground", fg);
        UIManager.put("Label.disabledForeground", disabled);
        UIManager.put("Button.background", input);
        UIManager.put("Button.foreground", fg);
        UIManager.put("Button.select", sel);
        UIManager.put("Button.disabledText", disabled);
        UIManager.put("ToggleButton.background", input);
        UIManager.put("ToggleButton.foreground", fg);
        UIManager.put("ToggleButton.select", sel);
        UIManager.put("CheckBox.background", surface);
        UIManager.put("CheckBox.foreground", fg);
        UIManager.put("RadioButton.background", surface);
        UIManager.put("RadioButton.foreground", fg);
        UIManager.put("TextField.background", input);
        UIManager.put("TextField.foreground", fg);
        UIManager.put("TextField.caretForeground", fg);
        UIManager.put("TextField.selectionBackground", sel);
        UIManager.put("TextField.selectionForeground", fg);
        UIManager.put("TextField.inactiveForeground", disabled);
        UIManager.put("TextArea.background", input);
        UIManager.put("TextArea.foreground", fg);
        UIManager.put("TextArea.caretForeground", fg);
        UIManager.put("TextArea.selectionBackground", sel);
        UIManager.put("TextArea.selectionForeground", fg);
        UIManager.put("FormattedTextField.background", input);
        UIManager.put("FormattedTextField.foreground", fg);
        UIManager.put("ComboBox.background", input);
        UIManager.put("ComboBox.foreground", fg);
        UIManager.put("ComboBox.selectionBackground", sel);
        UIManager.put("ComboBox.selectionForeground", fg);
        UIManager.put("ComboBox.disabledForeground", disabled);
        UIManager.put("List.background", input);
        UIManager.put("List.foreground", fg);
        UIManager.put("List.selectionBackground", sel);
        UIManager.put("List.selectionForeground", fg);
        UIManager.put("Table.background", input);
        UIManager.put("Table.foreground", fg);
        UIManager.put("Table.selectionBackground", sel);
        UIManager.put("Table.selectionForeground", fg);
        UIManager.put("Table.gridColor", border);
        UIManager.put("Table.focusCellBackground", sel);
        UIManager.put("TableHeader.background", surface);
        UIManager.put("TableHeader.foreground", fg);
        UIManager.put("Tree.background", input);
        UIManager.put("Tree.foreground", fg);
        UIManager.put("Tree.selectionBackground", sel);
        UIManager.put("Tree.selectionForeground", fg);
        UIManager.put("Tree.textBackground", input);
        UIManager.put("Tree.textForeground", fg);
        UIManager.put("ScrollPane.background", surface);
        UIManager.put("Viewport.background", input);
        UIManager.put("ScrollBar.background", bg);
        UIManager.put("ScrollBar.thumb", border);
        UIManager.put("ScrollBar.track", bg);
        UIManager.put("TabbedPane.background", surface);
        UIManager.put("TabbedPane.foreground", fg);
        UIManager.put("TabbedPane.selected", input);
        UIManager.put("TabbedPane.contentAreaColor", surface);
        UIManager.put("TabbedPane.shadow", border);
        UIManager.put("TabbedPane.darkShadow", border);
        UIManager.put("TabbedPane.light", surface);
        UIManager.put("TabbedPane.highlight", input);
        UIManager.put("ToolBar.background", surface);
        UIManager.put("ToolBar.foreground", fg);
        UIManager.put("ProgressBar.background", input);
        UIManager.put("ProgressBar.foreground", accent);
        UIManager.put("ProgressBar.selectionBackground", fg);
        UIManager.put("ProgressBar.selectionForeground", input);
        UIManager.put("Spinner.background", input);
        UIManager.put("Spinner.foreground", fg);
        UIManager.put("SplitPane.background", surface);
        UIManager.put("OptionPane.background", surface);
        UIManager.put("OptionPane.messageForeground", fg);
        UIManager.put("TitledBorder.titleColor", fg);
        UIManager.put("MenuBar.background", surface);
        UIManager.put("MenuBar.foreground", fg);
        UIManager.put("Menu.background", surface);
        UIManager.put("Menu.foreground", fg);
        UIManager.put("Menu.selectionBackground", sel);
        UIManager.put("Menu.selectionForeground", fg);
        UIManager.put("MenuItem.background", surface);
        UIManager.put("MenuItem.foreground", fg);
        UIManager.put("MenuItem.selectionBackground", sel);
        UIManager.put("MenuItem.selectionForeground", fg);
        UIManager.put("PopupMenu.background", surface);
        UIManager.put("PopupMenu.foreground", fg);
        UIManager.put("FileChooser.background", surface);
    }

    private void applyLight() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void savePreference() {
        Properties prefs = new Properties();
        prefs.setProperty("theme", dark ? "dark" : "light");
        try {
            new File("local").mkdirs();
            try (Writer w = new FileWriter(PREFS_FILE)) {
                prefs.store(w, "Migration4o UI preferences");
            }
        } catch (IOException ignored) {
        }
    }
}
