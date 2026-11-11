package storage;

/** Turns fields into one tab-separated line and back, escaping anything that would break the format. */
public final class TextCodec {
    private TextCodec() {
    }

    public static String join(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append('\t');
            sb.append(escape(fields[i]));
        }
        return sb.toString();
    }

    public static String[] split(String line) {
        String[] parts = line.split("\t", -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = unescape(parts[i]);
        }
        return parts;
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private static String unescape(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(++i);
                if (next == 't') sb.append('\t');
                else if (next == 'n') sb.append('\n');
                else if (next == 'r') sb.append('\r');
                else sb.append(next);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}