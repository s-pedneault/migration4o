package migration4o.models.ui.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete detail layout for a class export.
 * Contains top-level layout nodes that define the record detail view structure.
 */
public class DetailLayout {
    public List<LayoutNode> nodes = new ArrayList<>();

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    /** Serialize to JSON for embedding in HTML exports. */
    public String toJson() {
        if (nodes.isEmpty())
            return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0)
                sb.append(',');
            nodes.get(i).appendJson(sb);
        }
        sb.append(']');
        return sb.toString();
    }
}
