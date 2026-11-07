package com.wgblackmon.aihealthcare.domain.model;

/**
 * Result of an OKF (Open Knowledge Format) wiki bundle export operation.
 *
 * <p>Returned by {@code OkfBundleExporterAdapter} after serializing all wiki pages
 * and contradictions to a directory of markdown files conforming to the OKF v0.2
 * specification. The bundle is consumable by any tool that reads markdown plus
 * YAML frontmatter (Obsidian, MkDocs, Hugo, raw git diff, etc.).
 *
 * @param pageCount          total wiki pages written to the bundle
 * @param contradictionCount total contradiction docs written under {@code CONTRADICTIONS/}
 * @param bundleDir          absolute path to the root of the generated bundle directory
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-07
 * @updated 2026-11-07
 */
public record WikiBundleExportResult(
        int pageCount,
        int contradictionCount,
        String bundleDir
) {}
