package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;
import com.wgblackmon.aihealthcare.domain.model.FrameworkCompany;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeFrameworksUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FrameworkAnalysisPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.FrameworkLlmPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Domain service orchestrating healthcare framework competitive analysis.
 *
 * <p>For each configured {@link FrameworkCompany}, collects articles from
 * its topic feeds, delegates to the LLM for deep multi-dimensional analysis,
 * and persists the result. Companies are configured externally via
 * {@code application.yml} — adding a new company requires zero code changes.
 *
 * <p>Has no Spring dependencies — wired via {@code AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-08-03
 * @updated 2026-09-24
 */
public class FrameworkAnalysisService implements AnalyzeFrameworksUseCase {

    private static final int MIN_ARTICLES = 3;

    private final List<FrameworkCompany> companies;
    private final ArticleIngestionPort articleIngestionPort;
    private final FrameworkLlmPort frameworkLlmPort;
    private final FrameworkAnalysisPort frameworkAnalysisPort;

    public FrameworkAnalysisService(List<FrameworkCompany> companies,
                                    ArticleIngestionPort articleIngestionPort,
                                    FrameworkLlmPort frameworkLlmPort,
                                    FrameworkAnalysisPort frameworkAnalysisPort) {
        this.companies = new ArrayList<>(companies);
        this.articleIngestionPort = articleIngestionPort;
        this.frameworkLlmPort = frameworkLlmPort;
        this.frameworkAnalysisPort = frameworkAnalysisPort;
    }

    @Override
    public List<FrameworkAnalysis> analyzeAll() {
        List<FrameworkAnalysis> results = new ArrayList<>();

        for (FrameworkCompany company : companies) {
            List<NewsArticle> articles = collectArticles(company);
            if (articles.size() < MIN_ARTICLES) {
                continue;
            }

            FrameworkAnalysis llmResult = frameworkLlmPort.analyze(
                    company.slug(), company.name(), articles);
            if (llmResult == null) {
                continue;
            }

            // Overlay the exact article IDs used so the analysis is inspectable
            List<String> ids = new ArrayList<>();
            for (NewsArticle article : articles) {
                ids.add(article.articleId());
            }
            FrameworkAnalysis analysis = new FrameworkAnalysis(
                    llmResult.companySlug(), llmResult.companyName(),
                    llmResult.overallAssessment(), llmResult.dimensions(),
                    llmResult.strengths(), llmResult.weaknesses(),
                    llmResult.recentDevelopments(), llmResult.overallScore(),
                    llmResult.articleCount(), ids, llmResult.analyzedAt());

            frameworkAnalysisPort.save(analysis);
            results.add(analysis);
        }

        return results;
    }

    @Override
    public Optional<FrameworkAnalysis> getBySlug(String companySlug) {
        return frameworkAnalysisPort.findBySlug(companySlug);
    }

    @Override
    public List<FrameworkAnalysis> getAll() {
        return frameworkAnalysisPort.findAll();
    }

    @Override
    public List<NewsArticle> getArticlesForSlug(String companySlug) {
        return frameworkAnalysisPort.findBySlug(companySlug)
                .map(analysis -> articleIngestionPort.fetchArticlesByIds(analysis.articleIds()))
                .orElse(List.of());
    }

    /**
     * Collects articles from all configured topics for a company, deduped by article ID.
     */
    private List<NewsArticle> collectArticles(FrameworkCompany company) {
        List<NewsArticle> all = new ArrayList<>();
        List<String> seenIds = new ArrayList<>();

        for (String topic : company.topics()) {
            List<NewsArticle> topicArticles = articleIngestionPort.fetchAllByTopic(topic);
            for (NewsArticle article : topicArticles) {
                if (!seenIds.contains(article.articleId())) {
                    seenIds.add(article.articleId());
                    all.add(article);
                }
            }
        }

        return all;
    }
}
