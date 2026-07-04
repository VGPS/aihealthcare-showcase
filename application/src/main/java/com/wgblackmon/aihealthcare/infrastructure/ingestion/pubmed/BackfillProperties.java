package com.wgblackmon.aihealthcare.infrastructure.ingestion.pubmed;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Externalized configuration for the PubMed backfill harvester.
 *
 * <p>Binds to the {@code aihealthcare.backfill} prefix in {@code application.yml}.
 * Provides default queries, date range, and batch size for historical article retrieval.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "aihealthcare.backfill")
public class BackfillProperties {

    private int fromYear = 2022;
    private int toYear = 2025;
    private int maxPerQuery = 50;
    private List<String> queries = new ArrayList<>();
}
