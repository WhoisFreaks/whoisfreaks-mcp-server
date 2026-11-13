package com.whoisfreaks.mcp.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reading the list arguments of the bulk tools. */
final class ToolArgs {

    private ToolArgs() {
    }

    /**
     * A list argument as trimmed, de-duplicated strings in their original order: a JSON array, or one
     * string with items separated by commas, spaces or new lines (some clients send lists that way).
     * Empty when the argument is missing.
     */
    static List<String> stringList(Map<String, Object> args, String key) {
        Object value = args.get(key);
        List<Object> raw = new ArrayList<>();
        if (value instanceof Collection<?> items) {
            raw.addAll(items);
        } else if (value != null) {
            raw.add(value);
        }
        Set<String> items = new LinkedHashSet<>();
        for (Object item : raw) {
            if (item == null) continue;
            for (String part : item.toString().split("[,\\s]+")) {
                if (!part.isBlank()) items.add(part.trim());
            }
        }
        return new ArrayList<>(items);
    }
}
