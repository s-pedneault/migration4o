package migration4o.util.formatters;

import migration4o.database.DODatabaseDelegate;

public class ValueFormatterTrim implements ValueFormatter {

    public static final ValueFormatterTrim formatter = new ValueFormatterTrim();

    @Override
    public String format(DODatabaseDelegate delegate, FormatterContext context, String value, String parameter) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }

}
