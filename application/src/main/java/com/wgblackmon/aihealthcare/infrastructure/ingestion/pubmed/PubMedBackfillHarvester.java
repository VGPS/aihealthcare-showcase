package com.wgblackmon.aihealthcare.infrastructure.ingestion.pubmed;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Infrastructure adapter that queries the PubMed E-utilities API to backfill
 * historical healthcare AI articles.
 *
 * <p>Uses two E-utilities endpoints:
 * <ul>
 *   <li><b>esearch.fcgi</b> — searches PubMed by term and date range, returns PMIDs</li>
 *   <li><b>efetch.fcgi</b> — fetches article metadata (title, abstract, authors, date) for PMIDs</li>
 * </ul>
 *
 * <p>No API key is required for basic usage (rate-limited to 3 requests/second).
 * Results are returned as {@link NewsArticle} records ready for
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.ArticleStoragePort#save}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@Slf4j
@Component
public class PubMedBackfillHarvester {

    private static final String ESEARCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";
    private static final String EFETCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi";
    private static final int BATCH_SIZE = 50;
    private static final String SOURCE_NAME = "PubMed Backfill";
    private static final String SOURCE_TIER = "ACADEMIC";
    private static final double SOURCE_WEIGHT = 0.9;
    private static final String TOPIC = "General AI Healthcare News";

    /**
     * Searches PubMed for articles matching the given query within a date range
     * and returns them as {@link NewsArticle} records.
     *
     * @param query   PubMed search term (e.g. {@code "artificial intelligence" AND "healthcare law"})
     * @param from    start date (inclusive)
     * @param to      end date (inclusive)
     * @param maxResults maximum number of articles to retrieve
     * @return list of harvested articles (never {@code null})
     */
    public List<NewsArticle> harvest(String query, LocalDate from, LocalDate to, int maxResults) {
        log.debug("harvest() | query={}, from={}, to={}, maxResults={}", query, from, to, maxResults);

        List<String> pmids = searchPmids(query, from, to, maxResults);
        log.info("harvest() | found {} PMIDs for query '{}'", pmids.size(), query);

        if (pmids.isEmpty()) {
            log.debug("harvest() | return=[] (no PMIDs found)");
            return List.of();
        }

        List<NewsArticle> articles = fetchArticles(pmids);
        log.info("harvest() | fetched {} articles from {} PMIDs", articles.size(), pmids.size());
        log.debug("harvest() | return={} articles", articles.size());
        return articles;
    }

    /**
     * Calls esearch.fcgi to find PMIDs matching the query and date range.
     *
     * @param query      PubMed search term
     * @param from       start date (inclusive)
     * @param to         end date (inclusive)
     * @param maxResults maximum PMIDs to return
     * @return list of PMID strings
     */
    private List<String> searchPmids(String query, LocalDate from, LocalDate to, int maxResults) {
        log.debug("searchPmids() | query={}, from={}, to={}, maxResults={}", query, from, to, maxResults);

        List<String> pmids = new ArrayList<>();
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy/MM/dd");
            String minDate = from.format(fmt);
            String maxDate = to.format(fmt);

            String url = ESEARCH_URL
                    + "?db=pubmed"
                    + "&term=" + encodedQuery
                    + "&datetype=pdat"
                    + "&mindate=" + minDate
                    + "&maxdate=" + maxDate
                    + "&retmax=" + maxResults
                    + "&retmode=xml"
                    + "&sort=relevance";

            log.debug("searchPmids() | requesting URL={}", url);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            try (InputStream is = new URL(url).openStream()) {
                Document doc = builder.parse(is);
                NodeList idNodes = doc.getElementsByTagName("Id");
                for (int i = 0; i < idNodes.getLength(); i++) {
                    pmids.add(idNodes.item(i).getTextContent().trim());
                }
            }
        } catch (Exception e) {
            log.warn("searchPmids() | failed to search PubMed: {}", e.getMessage(), e);
        }

        log.debug("searchPmids() | return={} PMIDs", pmids.size());
        return pmids;
    }

    /**
     * Calls efetch.fcgi to retrieve article metadata for a list of PMIDs.
     * Fetches in batches of {@value BATCH_SIZE} to respect API rate limits.
     *
     * @param pmids list of PubMed IDs to fetch
     * @return list of {@link NewsArticle} records
     */
    private List<NewsArticle> fetchArticles(List<String> pmids) {
        log.debug("fetchArticles() | pmids={}", pmids.size());

        List<NewsArticle> articles = new ArrayList<>();

        for (int offset = 0; offset < pmids.size(); offset += BATCH_SIZE) {
            int end = Math.min(offset + BATCH_SIZE, pmids.size());
            List<String> batch = pmids.subList(offset, end);
            log.debug("fetchArticles() | fetching batch {}-{} of {}", offset, end, pmids.size());

            try {
                String ids = String.join(",", batch);
                String url = EFETCH_URL
                        + "?db=pubmed"
                        + "&id=" + ids
                        + "&retmode=xml"
                        + "&rettype=abstract";

                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                try (InputStream is = new URL(url).openStream()) {
                    Document doc = builder.parse(is);
                    NodeList articleNodes = doc.getElementsByTagName("PubmedArticle");

                    for (int i = 0; i < articleNodes.getLength(); i++) {
                        Element articleEl = (Element) articleNodes.item(i);
                        NewsArticle article = parseArticleElement(articleEl);
                        if (article != null) {
                            articles.add(article);
                        }
                    }
                }

                // Rate limit: PubMed allows 3 requests/sec without API key
                if (end < pmids.size()) {
                    Thread.sleep(400);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("fetchArticles() | interrupted during batch fetch");
                break;
            } catch (Exception e) {
                log.warn("fetchArticles() | failed to fetch batch {}-{}: {}", offset, end, e.getMessage(), e);
            }
        }

        log.debug("fetchArticles() | return={} articles", articles.size());
        return articles;
    }

    /**
     * Parses a single {@code <PubmedArticle>} XML element into a {@link NewsArticle}.
     *
     * @param articleEl the PubmedArticle XML element
     * @return parsed article, or {@code null} if required fields are missing
     */
    private NewsArticle parseArticleElement(Element articleEl) {
        log.debug("parseArticleElement() | parsing PubmedArticle element");

        try {
            // Extract PMID
            String pmid = getTextContent(articleEl, "PMID");
            if (pmid == null || pmid.isBlank()) {
                log.debug("parseArticleElement() | return=null (no PMID)");
                return null;
            }

            // Extract title
            String title = getTextContent(articleEl, "ArticleTitle");
            if (title == null || title.isBlank()) {
                log.debug("parseArticleElement() | return=null (no title for PMID={})", pmid);
                return null;
            }

            // Extract abstract text
            StringBuilder abstractText = new StringBuilder();
            NodeList abstractNodes = articleEl.getElementsByTagName("AbstractText");
            for (int i = 0; i < abstractNodes.getLength(); i++) {
                if (abstractText.length() > 0) {
                    abstractText.append(" ");
                }
                String label = ((Element) abstractNodes.item(i)).getAttribute("Label");
                if (label != null && !label.isBlank()) {
                    abstractText.append(label).append(": ");
                }
                abstractText.append(abstractNodes.item(i).getTextContent().trim());
            }

            // Extract authors
            StringBuilder authors = new StringBuilder();
            NodeList authorNodes = articleEl.getElementsByTagName("Author");
            for (int i = 0; i < Math.min(authorNodes.getLength(), 3); i++) {
                Element authorEl = (Element) authorNodes.item(i);
                String lastName = getTextContent(authorEl, "LastName");
                String initials = getTextContent(authorEl, "Initials");
                if (lastName != null) {
                    if (authors.length() > 0) {
                        authors.append(", ");
                    }
                    authors.append(lastName);
                    if (initials != null) {
                        authors.append(" ").append(initials);
                    }
                }
            }
            if (authorNodes.getLength() > 3) {
                authors.append(" et al.");
            }

            // Extract publication date
            Instant publishedAt = extractPubDate(articleEl);

            // Build PubMed URL
            URI url = URI.create("https://pubmed.ncbi.nlm.nih.gov/" + pmid + "/");
            String articleId = "pubmed-" + pmid;

            NewsArticle article = new NewsArticle(
                    articleId,
                    title,
                    url,
                    abstractText.toString(),
                    TOPIC,
                    authors.length() > 0 ? authors.toString() : null,
                    null,
                    SOURCE_NAME,
                    SOURCE_TIER,
                    SOURCE_WEIGHT,
                    publishedAt
            );

            log.debug("parseArticleElement() | return={}", article.title());
            return article;
        } catch (Exception e) {
            log.warn("parseArticleElement() | failed to parse article: {}", e.getMessage());
            log.debug("parseArticleElement() | return=null");
            return null;
        }
    }

    /**
     * Extracts the publication date from a PubmedArticle element.
     * Tries ArticleDate first, then PubDate under PubMedPubDate.
     *
     * @param articleEl the PubmedArticle element
     * @return publication instant, or current time if not parseable
     */
    private Instant extractPubDate(Element articleEl) {
        log.debug("extractPubDate() | parsing publication date");

        try {
            // Try ArticleDate first (electronic publication date)
            NodeList articleDates = articleEl.getElementsByTagName("ArticleDate");
            if (articleDates.getLength() > 0) {
                Element dateEl = (Element) articleDates.item(0);
                Instant result = parseDateElement(dateEl);
                if (result != null) {
                    log.debug("extractPubDate() | return={} (from ArticleDate)", result);
                    return result;
                }
            }

            // Fall back to PubDate
            NodeList pubDates = articleEl.getElementsByTagName("PubDate");
            if (pubDates.getLength() > 0) {
                Element dateEl = (Element) pubDates.item(0);
                Instant result = parseDateElement(dateEl);
                if (result != null) {
                    log.debug("extractPubDate() | return={} (from PubDate)", result);
                    return result;
                }
            }
        } catch (Exception e) {
            log.debug("extractPubDate() | failed to parse date: {}", e.getMessage());
        }

        Instant result = Instant.now();
        log.debug("extractPubDate() | return={} (fallback to now)", result);
        return result;
    }

    /**
     * Parses a date element containing Year, Month, Day child elements.
     *
     * @param dateEl element with Year/Month/Day children
     * @return parsed instant, or {@code null} if year is missing
     */
    private Instant parseDateElement(Element dateEl) {
        log.debug("parseDateElement() | parsing date element");

        String year = getTextContent(dateEl, "Year");
        if (year == null) {
            log.debug("parseDateElement() | return=null (no Year)");
            return null;
        }

        String month = getTextContent(dateEl, "Month");
        String day = getTextContent(dateEl, "Day");

        int y = Integer.parseInt(year);
        int m = parseMonth(month);
        int d = (day != null) ? Integer.parseInt(day) : 1;

        Instant result = LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant();
        log.debug("parseDateElement() | return={}", result);
        return result;
    }

    /**
     * Parses a month string that can be numeric ("1"-"12") or abbreviated ("Jan"-"Dec").
     *
     * @param month month string, or {@code null}
     * @return month number (1-12), defaults to 1
     */
    private int parseMonth(String month) {
        if (month == null) {
            return 1;
        }
        try {
            return Integer.parseInt(month);
        } catch (NumberFormatException e) {
            // PubMed sometimes uses abbreviated month names
            String upper = month.toUpperCase();
            if (upper.startsWith("JAN")) return 1;
            if (upper.startsWith("FEB")) return 2;
            if (upper.startsWith("MAR")) return 3;
            if (upper.startsWith("APR")) return 4;
            if (upper.startsWith("MAY")) return 5;
            if (upper.startsWith("JUN")) return 6;
            if (upper.startsWith("JUL")) return 7;
            if (upper.startsWith("AUG")) return 8;
            if (upper.startsWith("SEP")) return 9;
            if (upper.startsWith("OCT")) return 10;
            if (upper.startsWith("NOV")) return 11;
            if (upper.startsWith("DEC")) return 12;
            return 1;
        }
    }

    /**
     * Gets the text content of the first child element with the given tag name.
     *
     * @param parent  parent element to search within
     * @param tagName child element tag name
     * @return text content, or {@code null} if not found
     */
    private String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }
}
