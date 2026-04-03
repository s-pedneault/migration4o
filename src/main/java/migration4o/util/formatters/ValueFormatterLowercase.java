package migration4o.util.formatters;

import migration4o.database.DODatabaseDelegate;

public class ValueFormatterLowercase implements ValueFormatter {

    public static final ValueFormatterLowercase formatter = new ValueFormatterLowercase();

    @Override
    public String format(DODatabaseDelegate delegate, FormatterContext context, String value, String parameter) {
        if (value == null) {
            return null;
        }
        return value.toLowerCase();
    }

}
