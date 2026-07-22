package com.wgblackmon.aihealthcare.infrastructure.ingestion.regulatory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for regulatory event harvesting.
 *
 * <p>Bound to the {@code aihealthcare.regulatory} prefix in
 * {@code application.yml}. Controls scheduling, lookback window,
 * and AI-relevance keyword filtering.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Component
@ConfigurationProperties(prefix = "aihealthcare.regulatory")
public class RegulatoryHarvestProperties {

    private boolean enabled = true;
    private String schedule = "0 30 4 * * *";
    private int lookbackDays = 7;
    private List<String> aiKeywords = new ArrayList<>(List.of(
            "artificial intelligence",
            "machine learning",
            "SaMD",
            "software as medical device",
            "clinical decision support",
            "CADe",
            "CADx",
            "algorithm",
            "deep learning",
            "neural network"
    ));

    public boolean isEnabled()                              { return enabled; }
    public void setEnabled(boolean enabled)                 { this.enabled = enabled; }

    public String getSchedule()                             { return schedule; }
    public void setSchedule(String schedule)                { this.schedule = schedule; }

    public int getLookbackDays()                             { return lookbackDays; }
    public void setLookbackDays(int lookbackDays)           { this.lookbackDays = lookbackDays; }

    public List<String> getAiKeywords()                     { return aiKeywords; }
    public void setAiKeywords(List<String> aiKeywords)      { this.aiKeywords = aiKeywords; }
}
