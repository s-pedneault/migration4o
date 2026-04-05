package migration4o.util.formatters;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import migration4o.database.DODatabaseDelegate;

public class ValueFormatterDate implements ValueFormatter {

    public static final ValueFormatterDate formatter = new ValueFormatterDate();

    public static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public String format(DODatabaseDelegate delegate, FormatterContext context, String value, String parameter) {
        if (value == null) {
            return null;
        }
        try {
            long timestamp = Long.parseLong(value);
            Date date = new Date(timestamp);
            return dateFormat.format(date);

        } catch (Exception e) {
            try {
                Date date = DateFormat.getInstance().parse(value);
                return dateFormat.format(date);

            } catch (Exception e2) {
                // If any error occurs during date formatting, return the original value
                return value;
            }
        }
    }

}
