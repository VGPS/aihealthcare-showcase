package com.wgblackmon.aihealthcare.web.dto;

import com.wgblackmon.aihealthcare.domain.model.DataFeed;
import com.wgblackmon.aihealthcare.domain.model.DataParameter;

import java.util.List;

/**
 * Response DTO for an enterprise data feed descriptor.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record DataFeedResponse(
        String feedId,
        String label,
        String description,
        String kind,
        List<String> supportedFormats,
        List<DataParameter> parameters,
        int defaultRowLimit,
        int maxRowLimit,
        String chartHint
) {

    public static DataFeedResponse from(DataFeed feed) {
        return new DataFeedResponse(
                feed.feedId(),
                feed.label(),
                feed.description(),
                feed.kind().name(),
                feed.supportedFormats().stream().map(Enum::name).toList(),
                feed.parameters(),
                feed.defaultRowLimit(),
                feed.maxRowLimit(),
                feed.chartHint());
    }
}
