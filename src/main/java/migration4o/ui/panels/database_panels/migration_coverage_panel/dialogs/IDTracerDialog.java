package migration4o.ui.panels.database_panels.migration_coverage_panel.dialogs;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Dialog for tracing object ID containment relationships.
 * Uses IDTracerDataService singleton to access cached object ID data.
 */
public class IDTracerDialog extends JFrame {
    private final JTextField searchField;
    private final JTree resultTree;
    private final DefaultTreeModel treeModel;
    private final JLabel statusLabel;
    private final IDTracerDataService dataService;

    public IDTracerDialog() {
        super("Object ID Tracer");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);

        // Get the singleton data service
        dataService = IDTracerDataService.getInstance();

        // Create main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Search panel at top
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        searchPanel.setBorder(BorderFactory.createTitledBorder("Search for Object ID"));

        JPanel searchInputPanel = new JPanel(new BorderLayout(5, 5));
        searchField = new JTextField();
        searchField.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JButton traceButton = new JButton("Trace");
        traceButton.addActionListener(e -> traceObjectId());
        searchField.addActionListener(e -> traceObjectId());

        searchInputPanel.add(new JLabel("Object ID: "), BorderLayout.WEST);
        searchInputPanel.add(searchField, BorderLayout.CENTER);
        searchInputPanel.add(traceButton, BorderLayout.EAST);

        searchPanel.add(searchInputPanel, BorderLayout.NORTH);

        statusLabel = new JLabel("Loading all-object-ids.txt...");
        statusLabel.setForeground(Color.BLUE);
        searchPanel.add(statusLabel, BorderLayout.SOUTH);

        mainPanel.add(searchPanel, BorderLayout.NORTH);

        // Tree panel for results
        JPanel treePanel = new JPanel(new BorderLayout());
        treePanel.setBorder(BorderFactory.createTitledBorder("Containment Tree"));

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Search results will appear here");
        treeModel = new DefaultTreeModel(rootNode);
        resultTree = new JTree(treeModel);
        resultTree.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultTree.setRootVisible(true);

        JScrollPane treeScrollPane = new JScrollPane(resultTree);
        treePanel.add(treeScrollPane, BorderLayout.CENTER);

        // Add mouse listener for double-click navigation
        resultTree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleTreeDoubleClick();
                }
            }
        });

        mainPanel.add(treePanel, BorderLayout.CENTER);

        // Bottom panel with help text and button
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));

        JTextArea helpText = new JTextArea(
                "Enter an object ID and click Trace to find all objects that contain it.\n" +
                        "The tree shows the containment hierarchy from root objects down to the target ID. Double-click a class to view in schema.");
        helpText.setEditable(false);
        helpText.setBackground(mainPanel.getBackground());
        helpText.setFont(new Font("Arial", Font.ITALIC, 11));
        helpText.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        bottomPanel.add(helpText, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton viewCoverageButton = new JButton("View Coverage");
        viewCoverageButton.setToolTipText("Navigate to Coverage tab and show all classes found in the trace");
        viewCoverageButton.addActionListener(e -> viewCoverage());
        buttonPanel.add(viewCoverageButton);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);

        // Load data in background
        loadDataInBackground();
    }

    private void loadDataInBackground() {
        searchField.setEnabled(false);
        statusLabel.setText("Loading all-object-ids.txt...");
        statusLabel.setForeground(Color.BLUE);

        boolean alreadyLoaded = dataService.ensureDataLoaded(() -> {
            // This callback runs when loading is complete
            SwingUtilities.invokeLater(() -> {
                statusLabel.setText("Ready. Loaded " + dataService.getObjectCount() + " object IDs.");
                statusLabel.setForeground(new Color(0, 128, 0));
                searchField.setEnabled(true);
                searchField.requestFocusInWindow();
            });
        });

        if (alreadyLoaded) {
            // Data was already loaded, update UI immediately
            statusLabel.setText("Ready. Loaded " + dataService.getObjectCount() + " object IDs.");
            statusLabel.setForeground(new Color(0, 128, 0));
            searchField.setEnabled(true);
            searchField.requestFocusInWindow();
        }
    }

    private void traceObjectId() {
        String searchText = searchField.getText().trim();
        if (searchText.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter an object ID", "Invalid Input",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        long targetId;
        try {
            targetId = Long.parseLong(searchText);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid object ID: " + searchText, "Invalid Input",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Check if ID exists
        if (!dataService.containsObjectId(targetId)) {
            DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(
                    "Object ID " + targetId + " not found in exported IDs");
            treeModel.setRoot(rootNode);
            statusLabel.setText("Object ID " + targetId + " not found");
            statusLabel.setForeground(Color.RED);
            return;
        }

        // Show all class types for this ID
        Set<String> allClasses = dataService.getAllClassNames(targetId);
        String leafClass = dataService.getLeafClassName(targetId);

        // Find all paths to this object
        List<List<Long>> paths = findContainmentPaths(targetId);

        // Build root node with type information
        String typeInfo = allClasses.size() > 1
                ? String.format(" [%s] (also appears as: %s)", leafClass, String.join(", ",
                        allClasses.stream().filter(c -> !c.equals(leafClass)).toArray(String[]::new)))
                : String.format(" [%s]", leafClass);

        if (paths.isEmpty()) {
            DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(
                    "Object ID " + targetId + typeInfo + " has no containers (it's a root object)");
            treeModel.setRoot(rootNode);
            statusLabel.setText("No containers found for object " + targetId);
            statusLabel.setForeground(new Color(0, 128, 0));
        } else {
            DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(
                    "Containment paths for object " + targetId + typeInfo + " (" + paths.size() + " found)");

            for (List<Long> path : paths) {
                buildTreePath(rootNode, path, targetId);
            }

            treeModel.setRoot(rootNode);
            expandAllNodes(resultTree, 0, resultTree.getRowCount());
            statusLabel.setText("Found " + paths.size() + " containment path(s) to object " + targetId);
            statusLabel.setForeground(new Color(0, 128, 0));
        }
    }

    private List<List<Long>> findContainmentPaths(long targetId) {
        List<List<Long>> allPaths = new ArrayList<>();
        Set<Long> visited = new HashSet<>();

        // Find all objects that directly contain the target
        List<Long> directContainers = dataService.findDirectContainers(targetId);

        if (directContainers.isEmpty()) {
            return allPaths; // No containers found
        }

        // For each direct container, find paths to it recursively
        for (long containerId : directContainers) {
            List<Long> currentPath = new ArrayList<>();
            currentPath.add(containerId);
            findPathsRecursive(containerId, currentPath, allPaths, visited, 0);
        }

        return allPaths;
    }

    private void findPathsRecursive(long currentId, List<Long> currentPath,
            List<List<Long>> allPaths, Set<Long> visited, int depth) {
        // Prevent infinite loops and excessive depth
        if (depth > 50 || visited.contains(currentId)) {
            allPaths.add(new ArrayList<>(currentPath));
            return;
        }

        visited.add(currentId);

        // Find what contains the current object
        List<Long> containers = dataService.findDirectContainers(currentId);

        if (containers.isEmpty()) {
            // Reached a root object - save this path
            allPaths.add(new ArrayList<>(currentPath));
        } else {
            // Continue searching up the containment tree
            for (long containerId : containers) {
                if (!currentPath.contains(containerId)) { // Avoid cycles
                    List<Long> newPath = new ArrayList<>(currentPath);
                    newPath.add(0, containerId); // Add to beginning (root first)
                    findPathsRecursive(containerId, newPath, allPaths, new HashSet<>(visited), depth + 1);
                }
            }
        }

        visited.remove(currentId);
    }

    private void buildTreePath(DefaultMutableTreeNode parentNode, List<Long> path, long targetId) {
        DefaultMutableTreeNode currentNode = parentNode;

        for (int i = 0; i < path.size(); i++) {
            long objectId = path.get(i);
            String leafClass = dataService.getLeafClassName(objectId);
            Set<String> allClasses = dataService.getAllClassNames(objectId);

            String nodeText;
            if (i < path.size() - 1) {
                long nextId = path.get(i + 1);
                nodeText = String.format("Object %d [%s] (contains object %d)",
                        objectId, leafClass, nextId);
            } else {
                nodeText = String.format("Object %d [%s] (contains target %d)",
                        objectId, leafClass, targetId);
            }

            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(nodeText);
            currentNode.add(childNode);
            currentNode = childNode;
        }

        // Add the target node at the end
        String targetLeafClass = dataService.getLeafClassName(targetId);
        DefaultMutableTreeNode targetNode = new DefaultMutableTreeNode(
                String.format("Object %d [%s] ← TARGET", targetId, targetLeafClass));
        currentNode.add(targetNode);
    }

    private void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
        for (int i = startingIndex; i < rowCount; i++) {
            tree.expandRow(i);
        }

        if (tree.getRowCount() != rowCount) {
            expandAllNodes(tree, rowCount, tree.getRowCount());
        }
    }

    /**
     * Sets the search field to the specified object ID and triggers the trace.
     * Waits for data to be loaded before executing the trace.
     */
    public void setSearchId(long objectId) {
        searchField.setText(String.valueOf(objectId));

        // Ensure data is loaded before tracing
        dataService.ensureDataLoaded(() -> {
            // Execute trace on EDT
            SwingUtilities.invokeLater(() -> {
                traceObjectId();
            });
        });
    }

    private void handleTreeDoubleClick() {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) resultTree.getLastSelectedPathComponent();
        if (selectedNode == null) {
            return;
        }

        String nodeText = selectedNode.getUserObject().toString();

        // Extract class name from node text (format: "Object 12345 [package.ClassName]
        // ...")
        int bracketStart = nodeText.indexOf('[');
        int bracketEnd = nodeText.indexOf(']');
        if (bracketStart > 0 && bracketEnd > bracketStart) {
            String className = nodeText.substring(bracketStart + 1, bracketEnd);

            // Navigate to the class in the reference schema
            migration4o.ui.main.MainWindow mainWindow = migration4o.ui.main.MainWindow.getInstance();
            if (mainWindow != null) {
                mainWindow.navigateToReferenceSchemaClass(className);
            }
        }
    }

    /**
     * Collect all class names from the current trace results and navigate to the
     * coverage tab.
     */
    private void viewCoverage() {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        if (root == null) {
            return;
        }

        // Collect all object IDs from the trace
        Set<Long> objectIds = new HashSet<>();
        collectObjectIdsFromNode(root, objectIds);

        // For each object ID, get ALL its class types (including ancestors)
        Set<String> classNames = new HashSet<>();
        for (Long objectId : objectIds) {
            Set<String> allClasses = dataService.getAllClassNames(objectId);
            classNames.addAll(allClasses);
        }

        if (classNames.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No classes found in current trace results.\nPlease perform a trace first.",
                    "No Data",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Navigate to coverage panel
        migration4o.ui.main.MainWindow mainWindow = migration4o.ui.main.MainWindow.getInstance();
        if (mainWindow != null) {
            mainWindow.navigateToCoverageWithFilter(classNames);
        }
    }

    /**
     * Recursively collect all object IDs from tree nodes.
     */
    private void collectObjectIdsFromNode(DefaultMutableTreeNode node, Set<Long> objectIds) {
        String nodeText = node.getUserObject().toString();

        // Extract object ID from node text (format: "Object 12345 [package.ClassName]
        // ...")
        if (nodeText.startsWith("Object ")) {
            int spaceIndex = nodeText.indexOf(' ', 7);
            if (spaceIndex > 7) {
                String idStr = nodeText.substring(7, spaceIndex);
                try {
                    long objectId = Long.parseLong(idStr);
                    objectIds.add(objectId);
                } catch (NumberFormatException e) {
                    // Skip invalid IDs
                }
            }
        }

        // Process children
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            collectObjectIdsFromNode(child, objectIds);
        }
    }
}
