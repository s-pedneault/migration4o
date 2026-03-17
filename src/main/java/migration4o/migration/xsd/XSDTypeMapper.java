package migration4o.migration.xsd;

import migration4o.util.TypeUtil;

/**
 * Static utilities for mapping Java types to XSD types and escaping XML
 * content.
 */
final class XSDTypeMapper {

    private XSDTypeMapper() {
    }

    /**
     * Checks whether a type name designates a primitive or well-known Java
     * type. Delegates to {@link TypeUtil#isPrimitiveType} and additionally
     * recognises {@code java.lang.Class}/{@code Class}.
     */
    static boolean isPrimitiveType(String typeName) {
        return TypeUtil.isPrimitiveType(typeName) || "java.lang.Class".equals(typeName) || "Class".equals(typeName);
    }

    /**
     * Maps a Java type name to the corresponding XSD type string.
     * <p>
     * Array types (except {@code byte[]}) are mapped by their component type.
     * Boolean is mapped to {@code xs:boolean} (Java {@code Boolean.toString()}
     * outputs valid {@code xs:boolean} values). Date is mapped to
     * {@code xs:dateTime} (the export formats dates as ISO 8601).
     */
    static String getXSDType(String javaType) {
        if (javaType == null || javaType.isEmpty()) {
            throw new IllegalArgumentException("XSD type mapping error: null or empty Java type");
        }

        String normalizedType = javaType;
        boolean isArrayType = normalizedType.endsWith("[]");

        // Keep byte[] as base64, but map other primitive arrays to their
        // component type
        if (isArrayType && !normalizedType.equals("byte[]")) {
            normalizedType = normalizedType.replaceAll("\\[\\]", "");
        }

        if (normalizedType.equals("java.lang.String") || normalizedType.equals("string"))
            return "xs:string";
        if (normalizedType.equals("java.lang.Integer") || normalizedType.equals("int"))
            return "xs:int";
        if (normalizedType.equals("java.lang.Long") || normalizedType.equals("long"))
            return "xs:long";
        if (normalizedType.equals("java.lang.Boolean") || normalizedType.equals("boolean"))
            return "xs:boolean";
        if (normalizedType.equals("java.lang.Double") || normalizedType.equals("double"))
            return "xs:double";
        if (normalizedType.equals("java.lang.Float") || normalizedType.equals("float"))
            return "xs:float";
        if (normalizedType.equals("java.lang.Byte") || normalizedType.equals("byte"))
            return "xs:byte";
        if (normalizedType.equals("java.lang.Short") || normalizedType.equals("short"))
            return "xs:short";
        if (normalizedType.equals("java.util.Date") || normalizedType.equals("date"))
            return "xs:dateTime";
        if (normalizedType.equals("java.lang.Object") || normalizedType.equals("Object") || normalizedType.equals("object"))
            return "xs:anyType";
        if (normalizedType.equals("java.lang.Class") || normalizedType.equals("Class"))
            return "xs:string";
        if (javaType.equals("byte[]"))
            return "xs:base64Binary";
        return "xs:string";
    }

    /** Escapes XML special characters in text content. */
    static String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
