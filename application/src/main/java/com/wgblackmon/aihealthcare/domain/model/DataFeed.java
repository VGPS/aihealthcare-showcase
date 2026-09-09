package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Describes a registered enterprise data feed — one entry in the feed registry.
 *
 * <p>Each {@link com.wgblackmon.aihealthcare.domain.port.outbound.EnterpriseDataSourcePort}
 * adapter returns a {@code DataFeed} from its {@code describe()} method.
 * The console renders feeds as selectable cards, and the parameter list
 * drives the dynamic form.
 *
 * @param feedId            stable identifier (e.g. "legislation", "articles")
 * @param label             human-readable feed name
 * @param description       one-paragraph explanation shown on the console
 * @param kind              data source family
 * @param supportedFormats  export formats this feed can produce
 * @param parameters        parameters the feed accepts (drives the form)
 * @param defaultRowLimit   default row cap when the request omits a limit
 * @param maxRowLimit       hard ceiling for this feed
 * @param chartHint         NONE, TIME_SERIES, or CATEGORY_BAR — drives the preview chart
 * @param active            whether the feed is available for new jobs
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record DataFeed(
        String feedId,
        String label,
        String description,
        DataSourceKind kind,
        List<ExportFormat> supportedFormats,
        List<DataParameter> parameters,
        int defaultRowLimit,
        int maxRowLimit,
        String chartHint,
        boolean active
) {
    public DataFeed {
        supportedFormats = supportedFormats == null ? List.of() : List.copyOf(supportedFormats);
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
}
