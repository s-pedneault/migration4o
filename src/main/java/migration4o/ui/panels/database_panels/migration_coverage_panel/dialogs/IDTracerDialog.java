package migration4o.ui.panels.database_panels.migration_coverage_panel.dialogs;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.*;
import java.util.List;

import migration4o.models.schema.DOSchema;
import migration4o.models.schema.DOSchemaClass;
import migration4o.models.schema.DOSchemaReference;
import migration4o.schema.DOSchemaService;
import migration4o.util.ModuleUtil;

/**
 * Dialog for tracing object ID containment relationships.
 * Uses IDTracerDataService singleton to access cached object ID data.
 */
public class IDTracerDialog extends JFrame {
    private final JTextField searchField;
    private final JTree resultTree;
    private final DefaultTreeModel treeModel;
    private final JTree referenceTree;
    private final DefaultTreeModel referenceTreeModel;
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

        // Containment tree panel
        JPanel containmentPanel = new JPanel(new BorderLayout());
        containmentPanel.setBorder(BorderFactory.createTitledBorder("Containment Tree"));

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Search results will appear here");
        treeModel = new DefaultTreeModel(rootNode);
        resultTree = new JTree(treeModel);
        resultTree.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultTree.setRootVisible(true);

        JScrollPane treeScrollPane = new JScrollPane(resultTree);
        containmentPanel.add(treeScrollPane, BorderLayout.CENTER);

        // Add mouse listener for double-click navigation
        resultTree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleTreeDoubleClick(resultTree);
                }
            }
        });

        // Reference tree panel
        JPanel referencePanel = new JPanel(new BorderLayout());
        referencePanel.setBorder(BorderFactory.createTitledBorder("Reference Tree"));

        DefaultMutableTreeNode referenceRoot = new DefaultMutableTreeNode("Reference investigation will appear here");
        referenceTreeModel = new DefaultTreeModel(referenceRoot);
        referenceTree = new JTree(referenceTreeModel);
        referenceTree.setFont(new Font("Monospaced", Font.PLAIN, 12));
        referenceTree.setRootVisible(true);

        JScrollPane referenceScrollPane = new JScrollPane(referenceTree);
        referencePanel.add(referenceScrollPane, BorderLayout.CENTER);

        referenceTree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    handleTreeDoubleClick(referenceTree);
                }
            }
        });

        JTabbedPane resultTabs = new JTabbedPane();
        resultTabs.addTab("Containment Tree", containmentPanel);
        resultTabs.addTab("Reference Tree", referencePanel);

        mainPanel.add(resultTabs, BorderLayout.CENTER);

        // Bottom panel with help text and button
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));

        JTextArea helpText = new JTextArea("Enter an object ID and click Trace to find all objects that contain it.\n" + "Use 'Containment Tree' for object-level paths and 'Reference Tree' for schema-level branch diagnostics. Double-click a class to view in schema.");
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
            JOptionPane.showMessageDialog(this, "Please enter an object ID", "Invalid Input", JOptionPane.WARNING_MESSAGE);
            return;
        }

        long targetId;
        try {
            targetId = Long.parseLong(searchText);
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid object ID: " + searchText, "Invalid Input", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Check if ID exists
        if (!dataService.containsObjectId(targetId)) {
            DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Object ID " + targetId + " not found in exported IDs");
            treeModel.setRoot(rootNode);
            referenceTreeModel.setRoot(new DefaultMutableTreeNode("Object ID " + targetId + " not found in exported IDs"));
            statusLabel.setText("Object ID " + targetId + " not found");
            statusLabel.setForeground(Color.RED);
            return;
        }

        // Show all class types for this ID
        Set<String> allClasses = dataService.getAllClassNames(targetId);
        String leafClass = dataService.getLeafClassName(targetId);

        // Find all paths to this object
        List<List<Long>> paths = findContainmentPaths(targetId);
        buildReferenceTree(targetId, allClasses, leafClass, paths);

        // Build root node with type information
        String typeInfo = allClasses.size() > 1 ? String.format(" [%s] (also appears as: %s)", leafClass, String.join(", ", allClasses.stream().filter(c -> !c.equals(leafClass)).toArray(String[]::new))) : String.format(" [%s]", leafClass);

        if (paths.isEmpty()) {
            DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Object ID " + targetId + typeInfo + " has no containers (it's a root object)");
            treeModel.setRoot(rootNode);
            boolean hasExportedClass = hasAnyExportedClassInTrace(targetId, paths);
            if (hasExportedClass) {
                statusLabel.setText("No containers found for object " + targetId + " (type participates in export modules)");
                statusLabel.setForeground(new Color(0, 128, 0));
            } else {
                statusLabel.setText("No containers found and no traced class is exported by any module");
                statusLabel.setForeground(new Color(180, 90, 0));
            }
        } else {
            DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Containment paths for object " + targetId + typeInfo + " (" + paths.size() + " found)");

            for (List<Long> path : paths) {
                buildTreePath(rootNode, path, targetId);
            }

            treeModel.setRoot(rootNode);
            expandAllNodes(resultTree, 0, resultTree.getRowCount());
            boolean hasExportedClass = hasAnyExportedClassInTrace(targetId, paths);
            boolean onlyNonExportedRelationships = isTargetOnlyViaNonExportedRelationships(paths, targetId);
            if (hasExportedClass && onlyNonExportedRelationships) {
                statusLabel.setText("Found " + paths.size() + " path(s), but target is only encountered through non-exported/filtered relationships");
                statusLabel.setForeground(new Color(180, 90, 0));
            } else if (hasExportedClass) {
                statusLabel.setText("Found " + paths.size() + " containment path(s) to object " + targetId);
                statusLabel.setForeground(new Color(0, 128, 0));
            } else {
                statusLabel.setText("Found " + paths.size() + " path(s), but no class in trace is exported by any module");
                statusLabel.setForeground(new Color(180, 90, 0));
            }
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

    private void findPathsRecursive(long currentId, List<Long> currentPath, List<List<Long>> allPaths, Set<Long> visited, int depth) {
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
            String classLabel = formatClassWithExportInfo(leafClass);
            String reachLabel = dataService.isReachedObjectId(objectId) ? "• reached" : "• unreached";

            String nodeText;
            if (i < path.size() - 1) {
                long nextId = path.get(i + 1);
                String edgeDiagnostic = formatEdgeDiagnostic(objectId, nextId);
                nodeText = classLabel.isEmpty() ? String.format("Object %d [%s] %s%s (contains object %d)", objectId, leafClass, reachLabel, formatObjectDecisionNotes(objectId), nextId) : String.format("Object %d [%s] %s %s (contains object %d)", objectId, leafClass, classLabel, reachLabel, nextId);
                if (!classLabel.isEmpty()) {
                    nodeText = nodeText + formatObjectDecisionNotes(objectId);
                }
                if (!edgeDiagnostic.isEmpty()) {
                    nodeText = nodeText + " " + edgeDiagnostic;
                }
            } else {
                String edgeDiagnostic = formatEdgeDiagnostic(objectId, targetId);
                nodeText = classLabel.isEmpty() ? String.format("Object %d [%s] %s%s (contains target %d)", objectId, leafClass, reachLabel, formatObjectDecisionNotes(objectId), targetId) : String.format("Object %d [%s] %s %s (contains target %d)", objectId, leafClass, classLabel, reachLabel, targetId);
                if (!classLabel.isEmpty()) {
                    nodeText = nodeText + formatObjectDecisionNotes(objectId);
                }
                if (!edgeDiagnostic.isEmpty()) {
                    nodeText = nodeText + " " + edgeDiagnostic;
                }
            }

            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(nodeText);
            currentNode.add(childNode);
            currentNode = childNode;
        }

        // Add the target node at the end
        String targetLeafClass = dataService.getLeafClassName(targetId);
        String targetClassLabel = formatClassWithExportInfo(targetLeafClass);
        String targetReachLabel = dataService.isReachedObjectId(targetId) ? "• reached" : "• unreached";
        DefaultMutableTreeNode targetNode = new DefaultMutableTreeNode(targetClassLabel.isEmpty() ? String.format("Object %d [%s] %s%s ← TARGET", targetId, targetLeafClass, targetReachLabel, formatObjectDecisionNotes(targetId)) : String.format("Object %d [%s] %s %s ← TARGET", targetId, targetLeafClass, targetClassLabel, targetReachLabel));
        if (!targetClassLabel.isEmpty()) {
            targetNode.setUserObject(targetNode.getUserObject().toString() + formatObjectDecisionNotes(targetId));
        }
        currentNode.add(targetNode);
    }

    private static class SchemaReferenceEdge {
        final String referrerClass;
        final String fieldName;

        SchemaReferenceEdge(String referrerClass, String fieldName) {
            this.referrerClass = referrerClass;
            this.fieldName = fieldName;
        }
    }

    private static class ReferenceDiagnosis {
        final String referrerClass;
        final String fieldName;
        final boolean referrerExported;
        final boolean classEdgeSampleFound;
        final boolean directTargetContainerFound;
        final String reason;
        final int priority;

        ReferenceDiagnosis(String referrerClass, String fieldName, boolean referrerExported, boolean classEdgeSampleFound, boolean directTargetContainerFound, String reason, int priority) {
            this.referrerClass = referrerClass;
            this.fieldName = fieldName;
            this.referrerExported = referrerExported;
            this.classEdgeSampleFound = classEdgeSampleFound;
            this.directTargetContainerFound = directTargetContainerFound;
            this.reason = reason;
            this.priority = priority;
        }
    }

    private static class ReferenceChain {
        final List<SchemaReferenceEdge> edges;
        final String exportedClass;
        final String exportedInfo;

        ReferenceChain(List<SchemaReferenceEdge> edges, String exportedClass, String exportedInfo) {
            this.edges = edges;
            this.exportedClass = exportedClass;
            this.exportedInfo = exportedInfo;
        }
    }

    private void buildReferenceTree(long targetId, Set<String> allClasses, String leafClass, List<List<Long>> containmentPaths) {
        DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
        if (referenceSchema == null || referenceSchema.getClasses() == null) {
            referenceTreeModel.setRoot(new DefaultMutableTreeNode("Reference schema not loaded"));
            return;
        }

        Map<String, List<SchemaReferenceEdge>> referencedByIndex = buildReferencedByIndex(referenceSchema);

        String summary = allClasses.size() > 1 ? "Reference investigation for object " + targetId + " [" + leafClass + "] (also appears as: " + String.join(", ", allClasses.stream().filter(c -> !c.equals(leafClass)).toArray(String[]::new)) + ")" : "Reference investigation for object " + targetId + " [" + leafClass + "]";

        DefaultMutableTreeNode root = new DefaultMutableTreeNode(summary);

        DefaultMutableTreeNode overall = new DefaultMutableTreeNode("Focused analysis (concise)");
        overall.add(new DefaultMutableTreeNode("Containment paths found: " + (containmentPaths != null ? containmentPaths.size() : 0)));
        overall.add(new DefaultMutableTreeNode("Classes for target ID: " + String.join(", ", allClasses)));
        root.add(overall);

        List<String> startClasses = new ArrayList<>(allClasses);
        startClasses.sort(String::compareTo);

        List<ReferenceDiagnosis> globalBreakpoints = new ArrayList<>();
        for (String startClass : startClasses) {
            DefaultMutableTreeNode classNode = new DefaultMutableTreeNode("Target typed as " + startClass + formatExportSuffix(startClass));
            root.add(classNode);

            List<SchemaReferenceEdge> edges = referencedByIndex.getOrDefault(startClass, Collections.emptyList());
            if (edges.isEmpty()) {
                classNode.add(new DefaultMutableTreeNode("No 'Referenced by' schema entries for this class"));
                continue;
            }

            List<ReferenceChain> chainsToExported = findReferenceChainsToExported(startClass, referencedByIndex, 6, 8);
            if (!chainsToExported.isEmpty()) {
                DefaultMutableTreeNode upstream = new DefaultMutableTreeNode("Upstream paths reaching exported classes (top " + Math.min(5, chainsToExported.size()) + ")");
                for (int i = 0; i < Math.min(5, chainsToExported.size()); i++) {
                    upstream.add(new DefaultMutableTreeNode(formatReferenceChain(startClass, chainsToExported.get(i))));
                }
                classNode.add(upstream);
            }

            Map<String, List<ReferenceChain>> firstHopChains = indexChainsByFirstHop(chainsToExported);
            List<ReferenceDiagnosis> diagnoses = analyzeFirstHopBreakpoints(startClass, edges, targetId, containmentPaths, firstHopChains);

            int exportedReferrers = 0;
            int classEdgeSamples = 0;
            int directTargetContainers = 0;
            for (ReferenceDiagnosis diagnosis : diagnoses) {
                if (diagnosis.referrerExported) {
                    exportedReferrers++;
                }
                if (diagnosis.classEdgeSampleFound) {
                    classEdgeSamples++;
                }
                if (diagnosis.directTargetContainerFound) {
                    directTargetContainers++;
                }
            }

            classNode.add(new DefaultMutableTreeNode("Schema referrers: " + edges.size()));
            classNode.add(new DefaultMutableTreeNode("Referrers exported by modules: " + exportedReferrers));
            classNode.add(new DefaultMutableTreeNode("Class-edge samples found in exported ID graph: " + classEdgeSamples + " / " + edges.size()));
            classNode.add(new DefaultMutableTreeNode("Direct containers for this exact target: " + directTargetContainers + " / " + edges.size()));

            diagnoses.sort(Comparator.comparingInt((ReferenceDiagnosis d) -> d.priority).reversed().thenComparing(d -> d.referrerClass + "." + (d.fieldName != null ? d.fieldName : "")));

            DefaultMutableTreeNode likelyBreaks = new DefaultMutableTreeNode("Likely breakpoints (top " + Math.min(5, diagnoses.size()) + ")");
            for (int i = 0; i < Math.min(5, diagnoses.size()); i++) {
                ReferenceDiagnosis diagnosis = diagnoses.get(i);
                String fieldLabel = (diagnosis.fieldName == null || diagnosis.fieldName.isBlank()) ? "<unknownField>" : diagnosis.fieldName;
                likelyBreaks.add(new DefaultMutableTreeNode(diagnosis.referrerClass + "." + fieldLabel + formatExportSuffix(diagnosis.referrerClass) + " | " + diagnosis.reason));
            }
            classNode.add(likelyBreaks);

            globalBreakpoints.addAll(diagnoses);
        }

        if (!globalBreakpoints.isEmpty()) {
            globalBreakpoints.sort(Comparator.comparingInt((ReferenceDiagnosis d) -> d.priority).reversed().thenComparing(d -> d.referrerClass + "." + (d.fieldName != null ? d.fieldName : "")));

            DefaultMutableTreeNode summaryNode = new DefaultMutableTreeNode("Most likely global breakpoints (top " + Math.min(8, globalBreakpoints.size()) + ")");
            for (int i = 0; i < Math.min(8, globalBreakpoints.size()); i++) {
                ReferenceDiagnosis diagnosis = globalBreakpoints.get(i);
                String fieldLabel = (diagnosis.fieldName == null || diagnosis.fieldName.isBlank()) ? "<unknownField>" : diagnosis.fieldName;
                summaryNode.add(new DefaultMutableTreeNode(diagnosis.referrerClass + "." + fieldLabel + formatExportSuffix(diagnosis.referrerClass) + " | " + diagnosis.reason));
            }
            root.add(summaryNode);
        }

        referenceTreeModel.setRoot(root);
        expandAllNodes(referenceTree, 0, referenceTree.getRowCount());
    }

    private Map<String, List<SchemaReferenceEdge>> buildReferencedByIndex(DOSchema schema) {
        Map<String, List<SchemaReferenceEdge>> index = new HashMap<>();
        for (DOSchemaClass schemaClass : schema.getClasses()) {
            if (schemaClass == null || schemaClass.source == null || schemaClass.schemaReferences == null) {
                continue;
            }

            for (DOSchemaReference reference : schemaClass.schemaReferences) {
                if (reference == null || reference.className == null || reference.className.isBlank()) {
                    continue;
                }
                index.computeIfAbsent(schemaClass.source, key -> new ArrayList<>()).add(new SchemaReferenceEdge(reference.className, reference.fieldName));
            }
        }

        for (List<SchemaReferenceEdge> edges : index.values()) {
            edges.sort(Comparator.comparing(edge -> edge.referrerClass + "." + (edge.fieldName != null ? edge.fieldName : "")));
        }
        return index;
    }

    private List<ReferenceDiagnosis> analyzeFirstHopBreakpoints(String targetClass, List<SchemaReferenceEdge> edges, long targetId, List<List<Long>> containmentPaths, Map<String, List<ReferenceChain>> firstHopChains) {
        List<ReferenceDiagnosis> diagnoses = new ArrayList<>();

        for (SchemaReferenceEdge edge : edges) {
            IDTracerDataService.ClassContainmentSample sample = dataService.findContainmentSampleForClasses(edge.referrerClass, targetClass);
            boolean classEdgeSampleFound = sample != null;
            boolean directTargetContainerFound = hasDirectContainerInClass(targetId, edge.referrerClass, containmentPaths);
            boolean referrerExported = !formatClassWithExportInfo(edge.referrerClass).isBlank();
            List<ReferenceChain> upstreamChains = firstHopChains.getOrDefault(edgeKey(edge), Collections.emptyList());

            String reason;
            int priority;

            if (!classEdgeSampleFound && referrerExported) {
                reason = "schema says it can reference target class, but no class-edge sample exists in exported ID graph";
                priority = 100;
            } else if (classEdgeSampleFound && !directTargetContainerFound && referrerExported) {
                reason = "branch exists for class, but not for this object ID (likely filtered/criteria/limits on this instance path)";
                priority = 90;
            } else if (!referrerExported && !upstreamChains.isEmpty()) {
                String upstreamHint = summarizeUpstreamHint(upstreamChains.get(0));
                if (classEdgeSampleFound && directTargetContainerFound) {
                    reason = "not directly exported, but this branch is covered via upstream exported path: " + upstreamHint;
                    priority = 20;
                } else {
                    reason = "not directly exported, but upstream exported path exists: " + upstreamHint;
                    priority = 55;
                }
            } else if (!referrerExported) {
                reason = "referrer class is not exported by any module";
                priority = 70;
            } else if (!directTargetContainerFound) {
                reason = "no direct container object of this class for the exact target ID";
                priority = 60;
            } else {
                reason = "direct branch exists";
                priority = 10;
            }

            diagnoses.add(new ReferenceDiagnosis(edge.referrerClass, edge.fieldName, referrerExported, classEdgeSampleFound, directTargetContainerFound, reason, priority));
        }

        return diagnoses;
    }

    private List<ReferenceChain> findReferenceChainsToExported(String startClass, Map<String, List<SchemaReferenceEdge>> referencedByIndex, int maxDepth, int maxChains) {
        List<ReferenceChain> chains = new ArrayList<>();
        Deque<SchemaReferenceEdge> path = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        visited.add(startClass);
        collectReferenceChains(startClass, referencedByIndex, maxDepth, maxChains, visited, path, chains);
        return chains;
    }

    private void collectReferenceChains(String currentClass, Map<String, List<SchemaReferenceEdge>> referencedByIndex, int maxDepth, int maxChains, Set<String> visited, Deque<SchemaReferenceEdge> path, List<ReferenceChain> chains) {
        if (path.size() >= maxDepth || chains.size() >= maxChains) {
            return;
        }

        List<SchemaReferenceEdge> edges = referencedByIndex.getOrDefault(currentClass, Collections.emptyList());
        for (SchemaReferenceEdge edge : edges) {
            if (edge.referrerClass == null || edge.referrerClass.isBlank()) {
                continue;
            }
            if (!visited.add(edge.referrerClass)) {
                continue;
            }

            path.addLast(edge);
            String exportInfo = formatClassWithExportInfo(edge.referrerClass);
            if (!exportInfo.isBlank()) {
                chains.add(new ReferenceChain(new ArrayList<>(path), edge.referrerClass, exportInfo));
                if (chains.size() >= maxChains) {
                    path.removeLast();
                    visited.remove(edge.referrerClass);
                    return;
                }
            }

            collectReferenceChains(edge.referrerClass, referencedByIndex, maxDepth, maxChains, visited, path, chains);

            path.removeLast();
            visited.remove(edge.referrerClass);
            if (chains.size() >= maxChains) {
                return;
            }
        }
    }

    private Map<String, List<ReferenceChain>> indexChainsByFirstHop(List<ReferenceChain> chains) {
        Map<String, List<ReferenceChain>> index = new HashMap<>();
        for (ReferenceChain chain : chains) {
            if (chain.edges == null || chain.edges.isEmpty()) {
                continue;
            }
            SchemaReferenceEdge first = chain.edges.get(0);
            index.computeIfAbsent(edgeKey(first), key -> new ArrayList<>()).add(chain);
        }
        return index;
    }

    private String formatReferenceChain(String startClass, ReferenceChain chain) {
        StringBuilder builder = new StringBuilder(startClass);
        if (chain.edges != null) {
            for (SchemaReferenceEdge edge : chain.edges) {
                String fieldLabel = (edge.fieldName == null || edge.fieldName.isBlank()) ? "<unknownField>" : edge.fieldName;
                builder.append(" <- ").append(edge.referrerClass).append('.').append(fieldLabel);
            }
        }
        if (chain.exportedInfo != null && !chain.exportedInfo.isBlank()) {
            builder.append(" [").append(chain.exportedInfo).append(']');
        }
        return builder.toString();
    }

    private String summarizeUpstreamHint(ReferenceChain chain) {
        if (chain.edges == null || chain.edges.isEmpty()) {
            return chain.exportedClass;
        }

        if (chain.edges.size() == 1) {
            return chain.edges.get(0).referrerClass + formatExportSuffix(chain.edges.get(0).referrerClass);
        }

        SchemaReferenceEdge nextHop = chain.edges.get(1);
        String fieldLabel = (nextHop.fieldName == null || nextHop.fieldName.isBlank()) ? "<unknownField>" : nextHop.fieldName;
        return nextHop.referrerClass + "." + fieldLabel + formatExportSuffix(nextHop.referrerClass);
    }

    private String edgeKey(SchemaReferenceEdge edge) {
        String fieldName = edge.fieldName != null ? edge.fieldName : "";
        return edge.referrerClass + "::" + fieldName;
    }

    private boolean hasDirectContainerInClass(long targetId, String className, List<List<Long>> containmentPaths) {
        if (containmentPaths == null || containmentPaths.isEmpty()) {
            return false;
        }

        for (List<Long> path : containmentPaths) {
            if (path.isEmpty()) {
                continue;
            }
            long directContainer = path.get(path.size() - 1);
            Set<String> directContainerClasses = dataService.getAllClassNames(directContainer);
            if (directContainerClasses.contains(className)) {
                return true;
            }
        }
        return false;
    }

    private String formatExportSuffix(String className) {
        String export = formatClassWithExportInfo(className);
        if (export == null || export.isBlank()) {
            return "";
        }
        return " [" + export + "]";
    }

    private String formatObjectDecisionNotes(long objectId) {
        Set<String> notes = dataService.getObjectDecisionNotes(objectId);
        if (notes.isEmpty()) {
            return "";
        }
        return " | decisions: " + String.join("; ", notes);
    }

    private String formatEdgeDiagnostic(long parentObjectId, long childObjectId) {
        Set<String> exportedNotes = dataService.getExportedRelationshipNotes(parentObjectId, childObjectId);
        Set<String> skippedNotes = dataService.getSkippedRelationshipNotes(parentObjectId, childObjectId);
        if (exportedNotes.isEmpty() && skippedNotes.isEmpty()) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        if (!exportedNotes.isEmpty()) {
            parts.add("edge exported: " + String.join("; ", exportedNotes));
        }
        if (!skippedNotes.isEmpty()) {
            parts.add("edge skipped: " + String.join("; ", skippedNotes));
        }
        return "| " + String.join(" | ", parts);
    }

    private boolean isTargetOnlyViaNonExportedRelationships(List<List<Long>> paths, long targetId) {
        if (paths == null || paths.isEmpty()) {
            return false;
        }

        boolean sawAnySkippedRelationship = false;

        for (List<Long> path : paths) {
            boolean allEdgesExported = true;

            for (int i = 0; i < path.size() - 1; i++) {
                long parentId = path.get(i);
                long childId = path.get(i + 1);
                if (!dataService.isRelationshipExported(parentId, childId)) {
                    allEdgesExported = false;
                }
                if (!dataService.getSkippedRelationshipNotes(parentId, childId).isEmpty()) {
                    sawAnySkippedRelationship = true;
                }
            }

            long finalParentId = path.get(path.size() - 1);
            if (!dataService.isRelationshipExported(finalParentId, targetId)) {
                allEdgesExported = false;
            }
            if (!dataService.getSkippedRelationshipNotes(finalParentId, targetId).isEmpty()) {
                sawAnySkippedRelationship = true;
            }

            if (allEdgesExported) {
                return false;
            }
        }

        return sawAnySkippedRelationship;
    }

    private String formatClassWithExportInfo(String className) {
        if (className == null || className.isBlank() || "Unknown".equals(className)) {
            return "";
        }

        DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
        if (referenceSchema == null) {
            return "";
        }

        DOSchemaClass schemaClass = referenceSchema.findClassByName(className);
        if (schemaClass == null) {
            return "";
        }

        boolean exported = ModuleUtil.isClassListedInAnyModule(schemaClass);
        if (!exported) {
            return "";
        }

        String moduleName = ModuleUtil.findModuleForClass(schemaClass);
        if (moduleName == null || moduleName.isBlank()) {
            return "✓ exported";
        }
        return "✓ exported by " + moduleName;
    }

    private boolean hasAnyExportedClassInTrace(long targetId, List<List<Long>> paths) {
        Set<Long> idsInTrace = new HashSet<>();
        idsInTrace.add(targetId);
        for (List<Long> path : paths) {
            idsInTrace.addAll(path);
        }

        DOSchema referenceSchema = DOSchemaService.getInstance().getReferenceSchema();
        if (referenceSchema == null) {
            return false;
        }

        for (Long objectId : idsInTrace) {
            Set<String> classes = dataService.getAllClassNames(objectId);
            for (String className : classes) {
                DOSchemaClass schemaClass = referenceSchema.findClassByName(className);
                if (schemaClass != null && ModuleUtil.isClassListedInAnyModule(schemaClass)) {
                    return true;
                }
            }
        }
        return false;
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

    private void handleTreeDoubleClick(JTree sourceTree) {
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) sourceTree.getLastSelectedPathComponent();
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
            JOptionPane.showMessageDialog(this, "No classes found in current trace results.\nPlease perform a trace first.", "No Data", JOptionPane.INFORMATION_MESSAGE);
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
