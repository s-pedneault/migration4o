package migration4o.migration.recipes;

import java.io.IOException;

import migration4o.migration.XMLWriter;
import migration4o.util.XMLUtil;

/**
 * Recipe for writing XML error markers. Provides consistent error formatting in
 * XML output for debugging.
 */
public class XMLErrorWriter {

    /**
     * Writes an XML comment indicating an export error. Used to mark failures in
     * the XML output while allowing export to continue.
     * 
     * @param xmlWriter   XML writer
     * @param objectId    The object ID that failed to export
     * @param errorMsg    The error message
     * @param indentLevel Indentation level for the error comment
     * @throws IOException if write fails
     */
    public static void writeErrorMarker(XMLWriter xmlWriter, long objectId, String errorMsg, int indentLevel) throws IOException {
        xmlWriter.writeIndent(indentLevel);
        xmlWriter.write("<!-- ERROR exporting object " + objectId + ": " + XMLUtil.xmlEscape(errorMsg) + " -->\n");
    }

    /**
     * Writes an XML comment indicating an export error with exception details.
     * 
     * @param xmlWriter   XML writer
     * @param objectId    The object ID that failed to export
     * @param e           The exception that occurred
     * @param indentLevel Indentation level for the error comment
     * @throws IOException if write fails
     */
    public static void writeErrorMarker(XMLWriter xmlWriter, long objectId, Exception e, int indentLevel) throws IOException {
        String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        writeErrorMarker(xmlWriter, objectId, errorMsg, indentLevel);
    }
}
