package migration4o.models.ui;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;

import migration4o.models.schema.DOSchemaClass;

/**
 * Transferable wrapper for DOSchemaClass to support drag-and-drop operations.
 * 
 * This class enables DOSchemaClass objects to be transferred via Java's
 * drag-and-drop (DnD) API. It implements the Transferable interface which
 * is required by the DnD framework to identify and transfer data between
 * UI components.
 * 
 * Used primarily in the migration structure panel where users can drag
 * classes between the available classes tree and the export modules tree.
 */
public class ClassTransferable implements Transferable {

    /**
     * DataFlavor identifying DOSchemaClass objects in drag-and-drop operations.
     * This flavor is checked when accepting drops to ensure type compatibility.
     */
    public static final DataFlavor CLASS_FLAVOR = new DataFlavor(DOSchemaClass.class, "Schema Class");

    private final DOSchemaClass schemaClass;

    /**
     * Creates a transferable wrapper for a DOSchemaClass.
     * 
     * @param schemaClass The schema class to wrap for DnD transfer
     */
    public ClassTransferable(DOSchemaClass schemaClass) {
        this.schemaClass = schemaClass;
    }

    /**
     * Returns the data flavors supported by this transferable.
     * Only supports CLASS_FLAVOR for DOSchemaClass transfer.
     */
    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { CLASS_FLAVOR };
    }

    /**
     * Checks if the specified data flavor is supported.
     * 
     * @param flavor The flavor to check
     * @return true if the flavor is CLASS_FLAVOR, false otherwise
     */
    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return CLASS_FLAVOR.equals(flavor);
    }

    /**
     * Returns the transfer data for the specified flavor.
     * 
     * @param flavor The requested data flavor
     * @return The DOSchemaClass object
     * @throws UnsupportedFlavorException if the flavor is not CLASS_FLAVOR
     */
    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
        if (!CLASS_FLAVOR.equals(flavor)) {
            throw new UnsupportedFlavorException(flavor);
        }
        return schemaClass;
    }
}
