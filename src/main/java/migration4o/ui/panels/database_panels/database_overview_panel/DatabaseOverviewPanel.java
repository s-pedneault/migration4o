package migration4o.ui.panels.database_panels.database_overview_panel;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import migration4o.database.DODatabaseContext;
import migration4o.ui.main.MainWindow;

public class DatabaseOverviewPanel extends JPanel {

    public DatabaseOverviewPanel(String databasePath, DODatabaseContext dbContext) {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        File dbFile = new File(databasePath);

        JTextArea overviewText = new JTextArea();
        overviewText.setText("Database Overview\n\nDatabase: " + dbFile.getName() + "\nPath: " + dbFile.getAbsolutePath());
        overviewText.setEditable(false);
        overviewText.setMargin(new Insets(10, 10, 10, 10));

        add(new JScrollPane(overviewText), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportAllButton = new JButton("Export all modules");
        exportAllButton.setToolTipText("Export all modules using this database context");
        exportAllButton.addActionListener(e -> {
            MainWindow.getInstance().triggerMigrateAllModules(dbContext);
        });
        buttonPanel.add(exportAllButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }
}
