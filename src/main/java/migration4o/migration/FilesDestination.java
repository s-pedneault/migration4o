package migration4o.migration;

/**
 * Controls where exported files are placed relative to the output directory.
 */
public enum FilesDestination {

    /**
     * Each class's data file is written to a sub-folder inside the output
     * branch directory (current default behaviour).
     */
    FOLDER,

    /**
     * Files are embedded inline rather than written as separate folder
     * entries. Exact semantics depend on the format handler.
     */
    EMBED
}
