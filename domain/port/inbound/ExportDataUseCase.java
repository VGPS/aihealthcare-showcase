package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.DataExportRequest;
import com.wgblackmon.aihealthcare.domain.model.DataExportResult;

/**
 * Inbound port for exporting data in various formats (CSV, JSON, PDF)
 * with optional white-label branding.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
public interface ExportDataUseCase {

    DataExportResult export(DataExportRequest request);
}
