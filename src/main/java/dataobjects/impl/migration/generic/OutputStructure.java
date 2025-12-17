package dataobjects.impl.migration.generic;

/**
 * Defines the preferred output structure for a format handler.
 * This allows the engine to adapt its calling pattern to match the format's
 * natural structure.
 */
public enum OutputStructure {
    /**
     * Tabular format with rows and columns (like Excel sheets or CSV files).
     * Engine will call methods in a row-by-row fashion.
     */
    TABULAR,

    /**
     * Hierarchical format with nested structures (like XML or JSON).
     * Engine will provide more structural information and allow batch processing.
     */
    HIERARCHICAL,

    /**
     * Flat file format with one file per class (like separate CSV files).
     * Engine will organize output by class boundaries.
     */
    FLAT_FILES
}