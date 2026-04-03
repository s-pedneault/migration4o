package migration4o.util.formatters;

import migration4o.database.DODatabaseDelegate;

public class ValueFormatterUppercase implements ValueFormatter {

    public static final ValueFormatterUppercase formatter = new ValueFormatterUppercase();

    @Override
    public String format(DODatabaseDelegate delegate, FormatterContext context, String value, String parameter) {
        if (value == null) {
            return null;
        }
        return value.toUpperCase();
    }

}
