package com.wgblackmon.aihealthcare.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Binds the {@code aihealthcare.frameworks} YAML section to a list of
 * company configurations for healthcare framework competitive analysis.
 *
 * <p>Adding a new company to the analysis requires only a new entry in
 * {@code application.yml} — zero code changes.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "aihealthcare.frameworks")
public class FrameworkCompanyProperties {

    private List<CompanyEntry> companies = new ArrayList<>();

    public List<CompanyEntry> getCompanies() {
        return companies;
    }

    public void setCompanies(List<CompanyEntry> companies) {
        this.companies = companies;
    }

    /**
     * Mutable POJO for YAML binding — converted to immutable
     * {@link com.wgblackmon.aihealthcare.domain.model.FrameworkCompany}
     * at wiring time in {@code AppConfig}.
     */
    public static class CompanyEntry {
        private String slug;
        private String name;
        private String url;
        private List<String> topics = new ArrayList<>();

        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public List<String> getTopics() { return topics; }
        public void setTopics(List<String> topics) { this.topics = topics; }
    }
}
