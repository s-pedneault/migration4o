package migration4o.util;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

/**
 * Validates XML files against XSD schemas.
 */
public class XMLValidator {

    /**
     * Validates an XML file against an XSD schema.
     * 
     * @param xmlPath Path to the XML file to validate
     * @param xsdPath Path to the XSD schema file
     * @return true if validation succeeds, false if validation fails
     */
    public static boolean validate(String xmlPath, String xsdPath) {
        File xmlFile = new File(xmlPath);
        String fileName = xmlFile.getName();

        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new File(xsdPath));
            Validator validator = schema.newValidator();

            // Create error handler to collect detailed error information
            final StringBuilder errorDetails = new StringBuilder();
            validator.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(org.xml.sax.SAXParseException e) {
                    errorDetails.append(String.format("  WARNING at line %d, column %d: %s\n",
                            e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
                }

                @Override
                public void error(org.xml.sax.SAXParseException e) {
                    errorDetails.append(String.format("  ERROR at line %d, column %d: %s\n",
                            e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
                }

                @Override
                public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException {
                    errorDetails.append(String.format("  FATAL ERROR at line %d, column %d: %s\n",
                            e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
                    throw e;
                }
            });

            validator.validate(new StreamSource(new File(xmlPath)));
            System.out.println("XML VALIDATION OF \"" + fileName + "\": PASS");
            return true;
        } catch (Exception e) {
            System.out.println("XML VALIDATION OF \"" + fileName + "\": FAIL");
            System.out.println("  " + e.getMessage());
            if (e instanceof org.xml.sax.SAXParseException) {
                org.xml.sax.SAXParseException spe = (org.xml.sax.SAXParseException) e;
                System.out.println("  Location: line " + spe.getLineNumber() + ", column " + spe.getColumnNumber());
            }
            return false;
        }
    }

    /**
     * Validates multiple XML files against an XSD schema.
     * 
     * @param xmlPaths List of XML file paths to validate
     * @param xsdPath  Path to the XSD schema file
     * @return ValidationResult containing success count and error details
     */
    public static ValidationResult validateMultiple(List<String> xmlPaths, String xsdPath) {
        ValidationResult result = new ValidationResult();

        for (String xmlPath : xmlPaths) {
            if (validate(xmlPath, xsdPath)) {
                result.successCount++;
            } else {
                result.failedFiles.add(xmlPath);
            }
        }

        return result;
    }

    /**
     * Result of validating multiple XML files.
     */
    public static class ValidationResult {
        public int successCount = 0;
        public List<String> failedFiles = new ArrayList<>();

        public boolean allValid() {
            return failedFiles.isEmpty();
        }

        public int getTotalCount() {
            return successCount + failedFiles.size();
        }
    }
}
