package migration4o.ui.panels.reference_schema_panels.migration_structure_panel;

import migration4o.models.ui.ClassExportConfig;
import migration4o.models.ui.ClassNode;
import migration4o.models.schema.DOSchemaModule;

import org.jdesktop.swingx.treetable.AbstractTreeTableModel;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.text.NumberFormat;
import java.util.Enumeration;
import java.util.Locale;

/**
 * TreeTableModel for displaying migration structure with pricing columns.
 * Columns: Tree (Name), ID, Description, Unit Cost, Cost, Sub-total, Total
 */
public class ExportTreeTableModel extends AbstractTreeTableModel {

    private String priceListKey = ""; // Current price list selection (empty =
                                      // "Default")
    private static final NumberFormat MONEY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US);

    private static final String[] COLUMN_NAMES = { "Name", "ID", "Description", "Unit Cost", "Cost", "Sub-total", "Total" };

    private static final Class<?>[] COLUMN_TYPES = { String.class, // Name (tree
                                                                   // column)
            String.class, // ID
            String.class, // Description
            String.class, // Unit Cost (formatted as money)
            String.class, // Cost (formatted as money)
            String.class, // Sub-total (formatted as money)
            String.class // Total (formatted as money)
    };

    public ExportTreeTableModel(Object root) {
        super(root);
    }

    public void setPriceListKey(String priceListKey) {
        this.priceListKey = priceListKey != null ? priceListKey : "";
        modelSupport.fireNewRoot();
    }

    public String getPriceListKey() {
        return priceListKey;
    }

    /**
     * Reload the entire tree structure.
     */
    public void reloadTree() {
        modelSupport.fireNewRoot();
    }

    /**
     * Notify that a specific node changed.
     */
    public void nodeChanged(TreePath path) {
        modelSupport.firePathChanged(path);
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return COLUMN_TYPES[column];
    }

    @Override
    public Object getValueAt(Object node, int column) {
        if (!(node instanceof DefaultMutableTreeNode)) {
            return null;
        }

        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) node;
        Object userObject = treeNode.getUserObject();

        switch (column) {
        case 0: // Name (tree column)
            if (userObject instanceof DOSchemaModule) {
                return ((DOSchemaModule) userObject).toString();
            } else if (userObject instanceof ClassNode) {
                return ((ClassNode) userObject).toString();
            } else {
                return userObject != null ? userObject.toString() : "";
            }

        case 1: // ID (modules only)
            if (userObject instanceof DOSchemaModule) {
                String id = ((DOSchemaModule) userObject).id;
                return id != null ? id : "";
            }
            return "";

        case 2: // Description
            if (userObject instanceof ClassNode) {
                ClassExportConfig config = ((ClassNode) userObject).getExportConfig();
                if (config != null && config.getDescription() != null) {
                    return config.getDescription();
                }
            }
            return "";

        case 3: // Unit Cost
            if (userObject instanceof ClassNode) {
                ClassExportConfig config = ((ClassNode) userObject).getExportConfig();
                if (config != null) {
                    float unitCost = config.getUnitCost(priceListKey);
                    if (unitCost > 0) {
                        return MONEY_FORMAT.format(unitCost);
                    }
                }
            }
            return "";

        case 4: // Cost (for classes only)
            if (userObject instanceof ClassNode) {
                ClassNode classNode = (ClassNode) userObject;
                ClassExportConfig config = classNode.getExportConfig();
                if (config != null) {
                    float unitCost = config.getUnitCost(priceListKey);
                    // Use filtered object count (applies criteria if
                    // configured)
                    int objectCount = classNode.getObjectCount();
                    if (unitCost > 0 && objectCount > 0) {
                        float cost = unitCost * objectCount;
                        return MONEY_FORMAT.format(cost);
                    }
                }
            }
            return "";

        case 5: // Sub-total (for modules only)
            if (userObject instanceof DOSchemaModule) {
                return MONEY_FORMAT.format(calculateModuleSubtotal(treeNode));
            }
            return "";

        case 6: // Total (for root node only)
            // Only show total for the root node
            if (treeNode == root) {
                return MONEY_FORMAT.format(calculateGrandTotal((DefaultMutableTreeNode) root));
            }
            return "";

        default:
            return null;
        }
    }

    /**
     * Calculate the sub-total cost for a module by summing all child class
     * costs and child module subtotals recursively.
     */
    private float calculateModuleSubtotal(DefaultMutableTreeNode moduleNode) {
        float subtotal = 0.0f;

        Enumeration<?> children = moduleNode.children();
        while (children.hasMoreElements()) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) children.nextElement();
            Object userObject = childNode.getUserObject();

            if (userObject instanceof ClassNode) {
                ClassNode classNode = (ClassNode) userObject;
                ClassExportConfig config = classNode.getExportConfig();
                if (config != null) {
                    float unitCost = config.getUnitCost(priceListKey);
                    // Use filtered object count (applies criteria if
                    // configured)
                    int objectCount = classNode.getObjectCount();
                    if (unitCost > 0 && objectCount > 0) {
                        subtotal += unitCost * objectCount;
                    }
                }
            } else if (userObject instanceof DOSchemaModule) {
                // Recursively add child module subtotals
                subtotal += calculateModuleSubtotal(childNode);
            }
        }

        return subtotal;
    }

    /**
     * Calculate the grand total cost for the entire tree.
     */
    private float calculateGrandTotal(DefaultMutableTreeNode rootNode) {
        return calculateModuleSubtotal(rootNode);
    }

    @Override
    public Object getChild(Object parent, int index) {
        if (parent instanceof DefaultMutableTreeNode) {
            return ((DefaultMutableTreeNode) parent).getChildAt(index);
        }
        return null;
    }

    @Override
    public int getChildCount(Object parent) {
        if (parent instanceof DefaultMutableTreeNode) {
            return ((DefaultMutableTreeNode) parent).getChildCount();
        }
        return 0;
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (parent instanceof DefaultMutableTreeNode && child instanceof DefaultMutableTreeNode) {
            return ((DefaultMutableTreeNode) parent).getIndex((DefaultMutableTreeNode) child);
        }
        return -1;
    }

    @Override
    public boolean isLeaf(Object node) {
        if (node instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) node;
            // Module nodes are not leaves, class nodes are leaves
            return treeNode.getUserObject() instanceof ClassNode;
        }
        return true;
    }

    @Override
    public boolean isCellEditable(Object node, int column) {
        return false; // All cells read-only
    }
}
