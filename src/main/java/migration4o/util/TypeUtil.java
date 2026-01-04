package migration4o.util;

import migration4o.models.database.DODatabaseField;

public class TypeUtil {

    public static boolean isPrimitiveType(DODatabaseField field) {
        if (field.isPrimitive())
            return true;
        String typeName = field.getTypeName();
        return isPrimitiveType(typeName);

    }

    public static boolean isPrimitiveType(String typeName) {
        if (typeName == null) {
            return false;
        }
        // Java primitive types
        if (typeName.equals("boolean") || typeName.equals("byte") || typeName.equals("char") ||
                typeName.equals("short") || typeName.equals("int") || typeName.equals("long") ||
                typeName.equals("float") || typeName.equals("double")) {
            return true;
        }

        // Common Java standard library types that are considered "primitive" for our
        // purposes
        return typeName.equals("java.lang.String") ||
                typeName.equals("java.lang.Integer") ||
                typeName.equals("java.lang.Long") ||
                typeName.equals("java.lang.Double") ||
                typeName.equals("java.lang.Float") ||
                typeName.equals("java.lang.Boolean") ||
                typeName.equals("java.lang.Character") ||
                typeName.equals("java.lang.Byte") ||
                typeName.equals("java.lang.Short") ||
                typeName.equals("java.math.BigDecimal") ||
                typeName.equals("java.math.BigInteger") ||
                typeName.equals("java.util.Date") ||
                typeName.equals("java.sql.Date") ||
                typeName.equals("java.sql.Time") ||
                typeName.equals("java.sql.Timestamp") ||
                typeName.equals("java.time.LocalDate") ||
                typeName.equals("java.time.LocalTime") ||
                typeName.equals("java.time.LocalDateTime") ||
                typeName.equals("java.time.ZonedDateTime") ||
                typeName.equals("java.util.UUID");
    }

}
