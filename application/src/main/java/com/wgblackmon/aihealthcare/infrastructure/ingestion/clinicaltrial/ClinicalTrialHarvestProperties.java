package com.wgblackmon.aihealthcare.infrastructure.ingestion.clinicaltrial;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for clinical trial harvesting from ClinicalTrials.gov.
 *
 * <p>Bound to the {@code aihealthcare.clinical-trials} prefix in
 * {@code application.yml}. Controls scheduling, lookback window,
 * maximum result count, and AI-relevance keyword filtering.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
@Component
@ConfigurationProperties(prefix = "aihealthcare.clinical-trials")
public class ClinicalTrialHarvestProperties {

    private boolean enabled = true;
    private String schedule = "0 0 5 * * *";
    private int lookbackDays = 7;
    private int maxResults = 100;
    private List<String> aiKeywords = new ArrayList<>(List.of(
            "artificial intelligence",
            "machine learning",
            "deep learning",
            "neural network",
            "clinical decision support",
            "SaMD",
            "software as medical device",
            "algorithm",
            "computer-aided",
            "natural language processing"
    ));

    public boolean isEnabled()                                 { return enabled; }
    public void setEnabled(boolean enabled)                    { this.enabled = enabled; }

    public String getSchedule()                                { return schedule; }
    public void setSchedule(String schedule)                   { this.schedule = schedule; }

    public int getLookbackDays()                                { return lookbackDays; }
    public void setLookbackDays(int lookbackDays)              { this.lookbackDays = lookbackDays; }

    public int getMaxResults()                                  { return maxResults; }
    public void setMaxResults(int maxResults)                   { this.maxResults = maxResults; }

    public List<String> getAiKeywords()                        { return aiKeywords; }
    public void setAiKeywords(List<String> aiKeywords)         { this.aiKeywords = aiKeywords; }
}
