package migration4o.ui.panels.database_panels.database_export_panel;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Insets;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.AbstractButton;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

import migration4o.database.DODatabaseContext;
import migration4o.migration.ExportConfigPersistence;
import migration4o.migration.ExportOutputOption;
import migration4o.models.schema.DOSchemaField;
import migration4o.models.ui.ExportConfig;
import migration4o.models.ui.ExportConfig.ExportMode;
import migration4o.models.ui.SeedQuery;
import migration4o.schema.DOSchemaService;
import migration4o.schema.modules.DOModuleService;
import migration4o.models.schema.DOSchemaModule;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.ExportOptions;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.MigrationServiceCallback;
import migration4o.ui.panels.reference_schema_panels.migration_structure_panel.MigrationStructurePanelUtil;
import migration4o.util.SchemaUtil;

/**
 * Export configuration panel that replaces the old DatabaseOverviewPanel.
 * Embeds all export options (previously in ExportConfirmationDialog) plus
 * seed-based selection configuration. Persists config to export-config.json.
 */
public class DatabaseExportPanel extends JPanel {

    private final String databasePath;
    private final DODatabaseContext dbContext;
    private ExportConfig config;

    // Section B: Export Mode
    private JRadioButton allObjectsRadio;
    private JRadioButton maxPerClassRadio;
    private JRadioButton seedBasedRadio;
    private JSpinner maxSpinner;
    private JTextField outputBranchField;

    // Section C: Seeds
    private DefaultListModel<SeedQuery> seedListModel;
    private JList<SeedQuery> seedList;
    private JButton addSeedButton;
    private JButton editSeedButton;
    private JButton removeSeedButton;
    private JSpinner seedMaxSpinner;
    private JPanel seedsPanel;

    // Section D: Additional Options
    private JCheckBox exportNativeIdsCheckbox;
    private JCheckBox fullTrackingCheckbox;
    private JComboBox<String> languageCombo;
    private JCheckBox applyUserSelectedFieldExclusionsCheckbox;
    private JCheckBox applySkipWhenConditionsCheckbox;
    private JCheckBox applyExportCriteriaFiltersCheckbox;
    private JCheckBox skipObjectsWithoutExportableFieldsCheckbox;
    private List<DOSchemaField> availableSkipOptions;
    private Map<DOSchemaField, JCheckBox> skipOptionCheckboxes = new HashMap<>();

    // Section E: Output Options
    private final Map<String, JCheckBox> outputOptionCheckboxes = new HashMap<>();

    // Export orchestrator
    private MigrationServiceCallback exportOrchestrator;

    public DatabaseExportPanel(String databasePath, DODatabaseContext dbContext) {
        this.databasePath = databasePath;
        this.dbContext = dbContext;
        this.config = ExportConfigPersistence.load(databasePath);
        this.exportOrchestrator = new MigrationServiceCallback(this);

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        contentPanel.add(createDatabaseInfoSection());
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(createExportModeSection());
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(createSeedsSection());
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(createAdditionalOptionsSection());
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(createOutputOptionsSection());

        add(new JScrollPane(contentPanel), BorderLayout.CENTER);
        add(createActionButtons(), BorderLayout.SOUTH);

        restoreFromConfig();
    }

    // ── Section A: Database Info ─────────────────────────────────────────────

    private JPanel createDatabaseInfoSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        File dbFile = new File(databasePath);
        JLabel nameLabel = new JLabel("Database: " + dbFile.getName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(nameLabel);

        JLabel pathLabel = new JLabel("Path: " + dbFile.getAbsolutePath());
        pathLabel.setFont(pathLabel.getFont().deriveFont(11f));
        pathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(pathLabel);

        return panel;
    }

    // ── Section B: Export Mode ───────────────────────────────────────────────

    private JPanel createExportModeSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Export Mode"));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        ButtonGroup group = new ButtonGroup();

        allObjectsRadio = new JRadioButton("All objects");
        allObjectsRadio.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(allObjectsRadio);
        panel.add(allObjectsRadio);

        panel.add(Box.createVerticalStrut(6));

        JPanel maxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        maxPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        maxPerClassRadio = new JRadioButton("Max");
        group.add(maxPerClassRadio);
        maxPanel.add(maxPerClassRadio);
        SpinnerNumberModel spinnerModel = new SpinnerNumberModel(50, 1, 999999, 10);
        maxSpinner = new JSpinner(spinnerModel);
        maxSpinner.setPreferredSize(new Dimension(80, 25));
        maxSpinner.setEnabled(false);
        maxPanel.add(maxSpinner);
        maxPanel.add(new JLabel("objects per class"));
        panel.add(maxPanel);

        panel.add(Box.createVerticalStrut(6));

        seedBasedRadio = new JRadioButton("Seed-based selection");
        seedBasedRadio.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(seedBasedRadio);
        panel.add(seedBasedRadio);

        panel.add(Box.createVerticalStrut(8));

        // Output branch
        JPanel branchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        branchPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        branchPanel.add(new JLabel("Output branch: "));
        outputBranchField = new JTextField("all", 20);
        outputBranchField.setToolTipText("Folder name under output/<database>/ for this export");
        branchPanel.add(outputBranchField);
        panel.add(branchPanel);

        // Wire mode switching
        allObjectsRadio.addActionListener(e -> onModeChanged());
        maxPerClassRadio.addActionListener(e -> onModeChanged());
        seedBasedRadio.addActionListener(e -> onModeChanged());
        maxSpinner.addChangeListener(e -> {
            if (maxPerClassRadio.isSelected()) {
                outputBranchField.setText("max" + maxSpinner.getValue());
            }
        });

        allObjectsRadio.setSelected(true);

        return panel;
    }

    private void onModeChanged() {
        maxSpinner.setEnabled(maxPerClassRadio.isSelected());
        boolean seedMode = seedBasedRadio.isSelected();
        setSeedsEnabled(seedMode);
        // Auto-update output branch
        if (allObjectsRadio.isSelected()) {
            outputBranchField.setText("all");
        } else if (maxPerClassRadio.isSelected()) {
            outputBranchField.setText("max" + maxSpinner.getValue());
        } else {
            outputBranchField.setText("custom");
        }
    }

    // ── Section C: Seeds Configuration ──────────────────────────────────────

    private JPanel createSeedsSection() {
        seedsPanel = new JPanel();
        seedsPanel.setLayout(new BoxLayout(seedsPanel, BoxLayout.Y_AXIS));
        seedsPanel.setBorder(BorderFactory.createTitledBorder("Seed Queries"));
        seedsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        seedListModel = new DefaultListModel<>();
        seedList = new JList<>(seedListModel);
        seedList.setVisibleRowCount(4);
        JScrollPane listScroll = new JScrollPane(seedList);
        listScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        listScroll.setPreferredSize(new Dimension(500, 100));
        seedsPanel.add(listScroll);

        seedsPanel.add(Box.createVerticalStrut(4));

        JPanel seedButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        seedButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
        addSeedButton = new JButton("Add Seed Query");
        editSeedButton = new JButton("Edit");
        removeSeedButton = new JButton("Remove");
        seedButtons.add(addSeedButton);
        seedButtons.add(editSeedButton);
        seedButtons.add(removeSeedButton);
        seedsPanel.add(seedButtons);

        addSeedButton.addActionListener(e -> addSeedQuery());
        editSeedButton.addActionListener(e -> editSeedQuery());
        removeSeedButton.addActionListener(e -> removeSeedQuery());

        seedsPanel.add(Box.createVerticalStrut(6));

        JPanel seedMaxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        seedMaxPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        seedMaxPanel.add(new JLabel("Max objects per related class: "));
        SpinnerNumberModel seedMaxModel = new SpinnerNumberModel(50, 1, 999999, 10);
        seedMaxSpinner = new JSpinner(seedMaxModel);
        seedMaxSpinner.setPreferredSize(new Dimension(80, 25));
        seedMaxPanel.add(seedMaxSpinner);
        seedsPanel.add(seedMaxPanel);

        setSeedsEnabled(false);

        return seedsPanel;
    }

    private void setSeedsEnabled(boolean enabled) {
        seedList.setEnabled(enabled);
        addSeedButton.setEnabled(enabled);
        editSeedButton.setEnabled(enabled);
        removeSeedButton.setEnabled(enabled);
        seedMaxSpinner.setEnabled(enabled);
    }

    private void addSeedQuery() {
        Frame frame = (Frame) SwingUtilities.getWindowAncestor(this);
        SeedQueryDialog dialog = new SeedQueryDialog(frame, null);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            seedListModel.addElement(dialog.getSeedQuery());
        }
    }

    private void editSeedQuery() {
        int idx = seedList.getSelectedIndex();
        if (idx < 0) {
            JOptionPane.showMessageDialog(this, "Select a seed query to edit.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        SeedQuery existing = seedListModel.get(idx);
        Frame frame = (Frame) SwingUtilities.getWindowAncestor(this);
        SeedQueryDialog dialog = new SeedQueryDialog(frame, existing);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            seedListModel.set(idx, dialog.getSeedQuery());
        }
    }

    private void removeSeedQuery() {
        int idx = seedList.getSelectedIndex();
        if (idx >= 0) {
            seedListModel.remove(idx);
        }
    }

    // ── Section D: Additional Options ───────────────────────────────────────

    private JPanel createAdditionalOptionsSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Additional Options"));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Export language
        JPanel langPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        langPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        langPanel.add(new JLabel("Export language: "));
        languageCombo = new JComboBox<>(new String[] { "Fran\u00e7ais", "English" });
        languageCombo.setSelectedIndex(0);
        languageCombo.setPreferredSize(new Dimension(120, 25));
        langPanel.add(languageCombo);
        panel.add(langPanel);

        panel.add(Box.createVerticalStrut(8));

        exportNativeIdsCheckbox = new JCheckBox("Export native object IDs (adds DB4O id attribute to XML)");
        exportNativeIdsCheckbox.setSelected(false);
        exportNativeIdsCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(exportNativeIdsCheckbox);

        panel.add(Box.createVerticalStrut(8));

        fullTrackingCheckbox = new JCheckBox("Full tracking & analysis (enables coverage panel; slower on large databases)");
        fullTrackingCheckbox.setSelected(true);
        fullTrackingCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(fullTrackingCheckbox);

        panel.add(Box.createVerticalStrut(8));
        panel.add(createFieldExclusionsSection());
        panel.add(Box.createVerticalStrut(8));
        panel.add(createConditionalExclusionsSection());

        return panel;
    }

    private JPanel createFieldExclusionsSection() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel actions = createBulkActionsPanel(this::checkAllFieldExclusions, this::uncheckAllFieldExclusions);
        content.add(actions);

        availableSkipOptions = SchemaUtil.collectSkipUserOptions(DOSchemaService.getInstance().getReferenceSchema());
        if (availableSkipOptions != null && !availableSkipOptions.isEmpty()) {
            for (DOSchemaField field : availableSkipOptions) {
                String label = field.skipUserOption;
                JCheckBox skipCheckbox = new JCheckBox(label);
                skipCheckbox.setSelected(false);
                skipCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
                content.add(skipCheckbox);
                skipOptionCheckboxes.put(field, skipCheckbox);
            }
        } else {
            JLabel emptyLabel = new JLabel("No user-selectable field exclusions found.");
            emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            emptyLabel.setFont(emptyLabel.getFont().deriveFont(11f));
            content.add(emptyLabel);
        }

        return createCollapsibleSection("Field exclusions", content, true);
    }

    private JPanel createConditionalExclusionsSection() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel actions = createBulkActionsPanel(this::checkAllConditionalExclusions, this::uncheckAllConditionalExclusions);
        content.add(actions);

        applyUserSelectedFieldExclusionsCheckbox = new JCheckBox("Apply user-selected field exclusions");
        applyUserSelectedFieldExclusionsCheckbox.setSelected(true);
        applyUserSelectedFieldExclusionsCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(applyUserSelectedFieldExclusionsCheckbox);

        applySkipWhenConditionsCheckbox = new JCheckBox("Apply schema skipWhen conditions (NULL, ZERO, MINUS_ONE, EMPTY_*, FALSE, DEFAULT)");
        applySkipWhenConditionsCheckbox.setSelected(true);
        applySkipWhenConditionsCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(applySkipWhenConditionsCheckbox);

        applyExportCriteriaFiltersCheckbox = new JCheckBox("Apply class export criteria filters");
        applyExportCriteriaFiltersCheckbox.setSelected(true);
        applyExportCriteriaFiltersCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(applyExportCriteriaFiltersCheckbox);

        skipObjectsWithoutExportableFieldsCheckbox = new JCheckBox("Skip objects with no exportable fields");
        skipObjectsWithoutExportableFieldsCheckbox.setSelected(true);
        skipObjectsWithoutExportableFieldsCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(skipObjectsWithoutExportableFieldsCheckbox);

        return createCollapsibleSection("Conditional exclusions", content, true);
    }

    // ── Section E: Output Options ───────────────────────────────────────────

    private JPanel createOutputOptionsSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Output Options"));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        for (String option : ExportOutputOption.allOptions()) {
            JCheckBox cb = new JCheckBox(option);
            cb.setSelected(ExportOutputOption.XML_XSD.equals(option));
            cb.setAlignmentX(Component.LEFT_ALIGNMENT);
            outputOptionCheckboxes.put(option, cb);
            panel.add(cb);
        }

        // HTML requires XML linkage
        JCheckBox htmlCb = outputOptionCheckboxes.get(ExportOutputOption.HTML_JS);
        JCheckBox xmlCb = outputOptionCheckboxes.get(ExportOutputOption.XML_XSD);
        if (htmlCb != null && xmlCb != null) {
            htmlCb.addActionListener(e -> {
                if (htmlCb.isSelected() && !xmlCb.isSelected()) {
                    xmlCb.setSelected(true);
                }
            });
            xmlCb.addActionListener(e -> {
                if (!xmlCb.isSelected() && htmlCb.isSelected()) {
                    htmlCb.setSelected(false);
                }
            });
        }

        return panel;
    }

    // ── Section F: Action Buttons ───────────────────────────────────────────

    private JPanel createActionButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));

        JButton saveButton = new JButton("Save Config");
        saveButton.addActionListener(e -> saveConfig());
        panel.add(saveButton);

        JButton exportButton = new JButton("Export All Modules");
        exportButton.addActionListener(e -> triggerExport());
        panel.add(exportButton);

        return panel;
    }

    // ── Config persistence ──────────────────────────────────────────────────

    private void saveConfig() {
        buildConfigFromUI();
        try {
            ExportConfigPersistence.save(config, databasePath);
            JOptionPane.showMessageDialog(this, "Export configuration saved.", "Config Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to save config: " + e.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void buildConfigFromUI() {
        if (allObjectsRadio.isSelected()) {
            config.setExportMode(ExportMode.ALL_OBJECTS);
        } else if (maxPerClassRadio.isSelected()) {
            config.setExportMode(ExportMode.MAX_PER_CLASS);
        } else {
            config.setExportMode(ExportMode.SEED_BASED);
        }
        config.setMaxObjectsPerClass((Integer) maxSpinner.getValue());
        config.setSeedMaxPerClass((Integer) seedMaxSpinner.getValue());
        config.setExportNativeIds(exportNativeIdsCheckbox.isSelected());
        config.setFullTracking(fullTrackingCheckbox.isSelected());
        config.setApplyUserSelectedFieldExclusions(applyUserSelectedFieldExclusionsCheckbox.isSelected());
        config.setApplySkipWhenConditions(applySkipWhenConditionsCheckbox.isSelected());
        config.setApplyExportCriteriaFilters(applyExportCriteriaFiltersCheckbox.isSelected());
        config.setSkipObjectsWithoutExportableFields(skipObjectsWithoutExportableFieldsCheckbox.isSelected());

        // Output options
        List<String> selectedOutput = new ArrayList<>();
        for (Map.Entry<String, JCheckBox> entry : outputOptionCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedOutput.add(entry.getKey());
            }
        }
        config.setOutputOptions(ExportOutputOption.normalize(selectedOutput));

        // Skip option names
        List<String> skipNames = new ArrayList<>();
        for (Map.Entry<DOSchemaField, JCheckBox> entry : skipOptionCheckboxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                skipNames.add(entry.getKey().skipUserOption);
            }
        }
        config.setSelectedSkipOptionNames(skipNames);

        // Output branch
        config.setOutputBranch(outputBranchField.getText().trim());

        // Export language
        config.setExportLanguage(languageCombo.getSelectedIndex() == 1 ? "en" : "fr");

        // Seeds
        List<SeedQuery> seeds = new ArrayList<>();
        for (int i = 0; i < seedListModel.size(); i++) {
            seeds.add(seedListModel.get(i));
        }
        config.setSeeds(seeds);
    }

    private void restoreFromConfig() {
        // Mode
        switch (config.getExportMode()) {
        case MAX_PER_CLASS:
            maxPerClassRadio.setSelected(true);
            break;
        case SEED_BASED:
            seedBasedRadio.setSelected(true);
            break;
        default:
            allObjectsRadio.setSelected(true);
            break;
        }
        maxSpinner.setValue(config.getMaxObjectsPerClass());
        seedMaxSpinner.setValue(config.getSeedMaxPerClass());
        onModeChanged();

        // Output branch — restore saved value or use default
        String branch = config.getOutputBranch();
        if (branch != null && !branch.isBlank()) {
            outputBranchField.setText(branch);
        }

        // Additional options
        exportNativeIdsCheckbox.setSelected(config.isExportNativeIds());
        fullTrackingCheckbox.setSelected(config.isFullTracking());
        applyUserSelectedFieldExclusionsCheckbox.setSelected(config.isApplyUserSelectedFieldExclusions());
        applySkipWhenConditionsCheckbox.setSelected(config.isApplySkipWhenConditions());
        applyExportCriteriaFiltersCheckbox.setSelected(config.isApplyExportCriteriaFilters());
        skipObjectsWithoutExportableFieldsCheckbox.setSelected(config.isSkipObjectsWithoutExportableFields());

        // Export language
        languageCombo.setSelectedIndex("en".equals(config.getExportLanguage()) ? 1 : 0);

        // Output options
        for (Map.Entry<String, JCheckBox> entry : outputOptionCheckboxes.entrySet()) {
            entry.getValue().setSelected(config.getOutputOptions().contains(entry.getKey()));
        }

        // Skip options — match by name
        List<String> savedNames = config.getSelectedSkipOptionNames();
        for (Map.Entry<DOSchemaField, JCheckBox> entry : skipOptionCheckboxes.entrySet()) {
            entry.getValue().setSelected(savedNames.contains(entry.getKey().skipUserOption));
        }

        // Seeds
        seedListModel.clear();
        for (SeedQuery seed : config.getSeeds()) {
            seedListModel.addElement(seed);
        }
    }

    // ── Export trigger ───────────────────────────────────────────────────────

    private void triggerExport() {
        buildConfigFromUI();

        // Validate output options
        List<String> selectedOutput = config.getOutputOptions();
        if (selectedOutput == null || selectedOutput.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select at least one output option.", "Output Options", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Validate seed-based has seeds
        if (config.getExportMode() == ExportMode.SEED_BASED && config.getSeeds().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Seed-based mode requires at least one seed query.\nPlease add a seed query or choose a different export mode.", "No Seeds", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Auto-save config before export
        try {
            ExportConfigPersistence.save(config, databasePath);
        } catch (Exception e) {
            // Non-fatal
            System.err.println("[DatabaseExportPanel] Auto-save failed: " + e.getMessage());
        }

        // Collect all modules
        List<DOSchemaModule> modules = DOModuleService.getInstance().getModules();
        if (modules.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No modules found in the migration structure.", "No Modules", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Build ModuleExportInfo list
        List<MigrationStructurePanelUtil.ModuleExportInfo> modulesToExport = new ArrayList<>();
        for (DOSchemaModule module : modules) {
            modulesToExport.add(new MigrationStructurePanelUtil.ModuleExportInfo(module.name, module));
        }

        // Build ExportOptions from the persisted config — single source of
        // truth
        ExportOptions exportOptions = ExportOptions.fromConfig(config);

        exportOrchestrator.exportModulesAsync(dbContext, modulesToExport, exportOptions);
    }

    // ── UI helpers ──────────────────────────────────────────────────────────

    private JPanel createCollapsibleSection(String title, JPanel contentPanel, boolean collapsedByDefault) {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setAlignmentX(Component.LEFT_ALIGNMENT);

        JToggleButton toggle = new JToggleButton();
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggle.setFocusPainted(false);
        toggle.setBorderPainted(false);
        toggle.setContentAreaFilled(false);

        contentPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(2, 16, 4, 0));
        contentPanel.setVisible(!collapsedByDefault);

        toggle.setSelected(!collapsedByDefault);
        updateCollapsibleTitle(toggle, title);
        toggle.addActionListener(e -> {
            contentPanel.setVisible(toggle.isSelected());
            updateCollapsibleTitle(toggle, title);
            revalidate();
        });

        container.add(toggle);
        container.add(contentPanel);
        return container;
    }

    private void updateCollapsibleTitle(AbstractButton button, String title) {
        button.setText((button.isSelected() ? "\u25BE " : "\u25B8 ") + title);
    }

    private JPanel createBulkActionsPanel(Runnable checkAll, Runnable uncheckAll) {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton checkAllBtn = new JButton("Check all");
        checkAllBtn.setMargin(new Insets(1, 6, 1, 6));
        checkAllBtn.setFont(checkAllBtn.getFont().deriveFont(11f));
        checkAllBtn.addActionListener(e -> checkAll.run());

        JButton uncheckAllBtn = new JButton("Uncheck all");
        uncheckAllBtn.setMargin(new Insets(1, 6, 1, 6));
        uncheckAllBtn.setFont(uncheckAllBtn.getFont().deriveFont(11f));
        uncheckAllBtn.addActionListener(e -> uncheckAll.run());

        actions.add(checkAllBtn);
        actions.add(uncheckAllBtn);
        return actions;
    }

    private void checkAllFieldExclusions() {
        for (JCheckBox cb : skipOptionCheckboxes.values())
            cb.setSelected(true);
    }

    private void uncheckAllFieldExclusions() {
        for (JCheckBox cb : skipOptionCheckboxes.values())
            cb.setSelected(false);
    }

    private void checkAllConditionalExclusions() {
        applyUserSelectedFieldExclusionsCheckbox.setSelected(true);
        applySkipWhenConditionsCheckbox.setSelected(true);
        applyExportCriteriaFiltersCheckbox.setSelected(true);
        skipObjectsWithoutExportableFieldsCheckbox.setSelected(true);
    }

    private void uncheckAllConditionalExclusions() {
        applyUserSelectedFieldExclusionsCheckbox.setSelected(false);
        applySkipWhenConditionsCheckbox.setSelected(false);
        applyExportCriteriaFiltersCheckbox.setSelected(false);
        skipObjectsWithoutExportableFieldsCheckbox.setSelected(false);
    }
}
