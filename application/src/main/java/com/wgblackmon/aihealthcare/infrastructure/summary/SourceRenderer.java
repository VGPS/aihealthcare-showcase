package com.wgblackmon.aihealthcare.infrastructure.summary;

import java.util.List;

/**
 * Formats a list of {@link SourceDoc}s into the numbered block that the extraction
 * prompt embeds between its instruction header and the topic line.
 *
 * <p>Each source becomes:
 * <pre>
 * [S1] (NEWS_ARTICLE, 2026-08-14) Title — https://...
 * content text here
 * </pre>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-29
 * @updated 2026-09-29
 */
public final class SourceRenderer {

    private SourceRenderer() {}

    public static String render(List<SourceDoc> sources) {
        StringBuilder sb = new StringBuilder();
        for (SourceDoc s : sources) {
            sb.append("[").append(s.citeId()).append("] (")
              .append(s.type());
            if (s.publishedOn() != null) {
                sb.append(", ").append(s.publishedOn());
            }
            if (s.dbRef() != null) {
                sb.append(", ").append(s.dbRef());
            }
            sb.append(") ").append(s.title() != null ? s.title() : "(no title)");
            if (s.url() != null) {
                sb.append(" — ").append(s.url());
            }
            sb.append("\n");
            if (s.content() != null && !s.content().isBlank()) {
                sb.append(s.content().trim()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
