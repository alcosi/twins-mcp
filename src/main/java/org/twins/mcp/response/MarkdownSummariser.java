package org.twins.mcp.response;

import java.util.List;

/**
 * Helpers that render the markdown summary half of a {@link ToolResponse} (Story 1.5 AC-4).
 *
 * <p>Format rules (architecture §"Markdown summary"): entity lists → GFM tables; single entity →
 * bold-key block; partial lists always carry a pagination hint. CommonMark only, except that GFM
 * tables are accepted for entity lists.
 */
public final class MarkdownSummariser {

    private MarkdownSummariser() {
    }

    /** Renders a GFM table from headers and rows; pipes in cells are escaped. */
    public static String table(List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append('|').append(' ').append(String.join(" | ", escape(headers))).append(' ').append('|').append('\n');
        sb.append("| ");
        for (int i = 0; i < headers.size(); i++) {
            sb.append("---");
            if (i < headers.size() - 1) {
                sb.append(" | ");
            }
        }
        sb.append(" |\n");
        for (List<String> row : rows) {
            sb.append('|').append(' ').append(String.join(" | ", escape(row))).append(' ').append('|').append('\n');
        }
        return sb.toString();
    }

    /** Markdown hint pointing the agent at the next page via the opaque cursor (AC-5). */
    public static String paginationHint(String nextCursor) {
        return "More results exist — next page: pass cursor=" + nextCursor + ".";
    }

    /** Neutral "no matches" note (an empty result is never an error — FR-TM-001). */
    public static String noMatches(String what) {
        return "No " + what + " match.";
    }

    private static List<String> escape(List<String> cells) {
        return cells.stream()
                .map(c -> c == null ? "" : c.replace("|", "\\|").replace("\n", " "))
                .toList();
    }
}
