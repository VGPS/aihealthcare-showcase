package com.wgblackmon.aihealthcare.infrastructure.ingestion.legal;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.ByteArrayInputStream;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Harvests legal and regulatory policy research articles from PubMed
 * using the NCBI E-utilities API.
 *
 * <p>Uses a two-step approach:
 * <ol>
 *   <li>esearch — retrieves PMIDs matching the search query</li>
 *   <li>efetch — retrieves article metadata (title, abstract, authors, date)
 *       for those PMIDs in XML format</li>
 * </ol>
 *
 * <p>Results are mapped to {@link NewsArticle} records with topic
 * "AI Healthcare Government Policy" so they flow into the Policy category
 * on the Legal Timeline page.
 *
 * <p>The PubMed E-utilities API is free and requires no API key for
 * moderate usage (up to 3 requests/second without a key).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
@Slf4j
@Component
public class PubMedLegalHarvester {

    private static final String ESEARCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi";
    private static final String EFETCH_URL = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_RESULTS = 200;
    private static final int BATCH_SIZE = 50;
    private static final String TOPIC = "AI Healthcare Government Policy";
    private static final String SOURCE_NAME = "PubMed";
    private static final DateTimeFormatter PUBMED_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private static final String SEARCH_TERM =
            "(\"artificial intelligence\" OR \"machine learning\" OR \"deep learning\") "
            + "AND (\"regulation\" OR \"liability\" OR \"legal\" OR \"policy\" OR \"FDA\" "
            + "OR \"HIPAA\" OR \"legislation\") AND \"healthcare\"";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public PubMedLegalHarvester() {
        log.debug("PubMedLegalHarvester()");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Returns a short label for this source (used in logging).
     */
    public String sourceName() {
        log.debug("sourceName()");
        String result = SOURCE_NAME;
        log.debug("sourceName() | return={}", result);
        return result;
    }

    /**
     * Harvests legal/policy PubMed articles within the lookback window.
     *
     * @param lookbackDays how many days back to search
     * @return list of articles mapped from PubMed records
     */
    public List<NewsArticle> harvest(int lookbackDays) {
        log.debug("harvest() | lookbackDays={}", lookbackDays);

        List<NewsArticle> articles = new ArrayList<>();
        try {
            // Step 1: esearch to get PMIDs
            List<String> pmids = searchPmids(lookbackDays);
            log.info("harvest() | esearch returned {} PMIDs", pmids.size());

            if (pmids.isEmpty()) {
                log.debug("harvest() | return=0 articles");
                return articles;
            }

            // Step 2: efetch in batches to get article metadata
            for (int i = 0; i < pmids.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, pmids.size());
                List<String> batch = pmids.subList(i, end);
                List<NewsArticle> batchArticles = fetchArticleMetadata(batch);
                articles.addAll(batchArticles);
            }

        } catch (Exception e) {
            log.warn("harvest() | PubMed harvest failed: {}", e.getMessage());
        }

        log.info("harvest() | harvested {} articles from PubMed", articles.size());
        log.debug("harvest() | return={} articles", articles.size());
        return articles;
    }

    private List<String> searchPmids(int lookbackDays) throws Exception {
        log.debug("searchPmids() | lookbackDays={}", lookbackDays);

        String minDate = LocalDate.now(ZoneOffset.UTC).minusDays(lookbackDays).format(PUBMED_DATE);
        String maxDate = LocalDate.now(ZoneOffset.UTC).format(PUBMED_DATE);

        String url = ESEARCH_URL
                + "?db=pubmed"
                + "&term=" + URLEncoder.encode(SEARCH_TERM, StandardCharsets.UTF_8)
                + "&retmode=json"
                + "&retmax=" + MAX_RESULTS
                + "&mindate=" + minDate
                + "&maxdate=" + maxDate
                + "&datetype=pdat";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.debug("searchPmids() | esearch returned status={}", response.statusCode());

        if (response.statusCode() != 200) {
            log.warn("searchPmids() | esearch returned non-200: {}", response.statusCode());
            return List.of();
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode esearchResult = root.get("esearchresult");
        if (esearchResult == null) {
            return List.of();
        }

        JsonNode idList = esearchResult.get("idlist");
        if (idList == null || !idList.isArray()) {
            return List.of();
        }

        List<String> pmids = new ArrayList<>();
        for (JsonNode idNode : idList) {
            pmids.add(idNode.asText());
        }

        log.debug("searchPmids() | return={} PMIDs", pmids.size());
        return pmids;
    }

    private List<NewsArticle> fetchArticleMetadata(List<String> pmids) {
        log.debug("fetchArticleMetadata() | pmids={}", pmids.size());

        List<NewsArticle> articles = new ArrayList<>();
        try {
            StringBuilder idParam = new StringBuilder();
            for (int i = 0; i < pmids.size(); i++) {
                if (i > 0) {
                    idParam.append(",");
                }
                idParam.append(pmids.get(i));
            }

            String url = EFETCH_URL
                    + "?db=pubmed"
                    + "&id=" + idParam
                    + "&retmode=xml";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("fetchArticleMetadata() | efetch returned status={}", response.statusCode());

            if (response.statusCode() != 200) {
                log.warn("fetchArticleMetadata() | efetch returned non-200: {}", response.statusCode());
                return articles;
            }

            // Parse XML response
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // PubMed XML includes a DOCTYPE declaration — allow it but disable external entities
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(response.body().getBytes(StandardCharsets.UTF_8)));

            NodeList pubmedArticles = doc.getElementsByTagName("PubmedArticle");
            for (int i = 0; i < pubmedArticles.getLength(); i++) {
                Element articleElement = (Element) pubmedArticles.item(i);
                NewsArticle article = parseArticleElement(articleElement);
                if (article != null) {
                    articles.add(article);
                }
            }

        } catch (Exception e) {
            log.warn("fetchArticleMetadata() | efetch failed: {}", e.getMessage());
        }

        log.debug("fetchArticleMetadata() | return={} articles", articles.size());
        return articles;
    }

    private NewsArticle parseArticleElement(Element articleElement) {
        // Extract PMID
        String pmid = getElementText(articleElement, "PMID");
        if (pmid == null) {
            return null;
        }

        // Extract title from MedlineCitation > Article > ArticleTitle
        NodeList articleNodes = articleElement.getElementsByTagName("Article");
        if (articleNodes.getLength() == 0) {
            return null;
        }
        Element article = (Element) articleNodes.item(0);
        String title = getElementText(article, "ArticleTitle");
        if (title == null || title.isBlank()) {
            return null;
        }

        // Extract abstract
        String abstractText = extractAbstract(article);

        // Extract first author
        String author = extractFirstAuthor(article);

        // Extract publication date
        Instant publishedAt = extractPubDate(articleElement);

        String articleId = "pubmed-" + pmid;
        URI articleUrl = URI.create("https://pubmed.ncbi.nlm.nih.gov/" + pmid + "/");

        return new NewsArticle(
                articleId,
                title,
                articleUrl,
                abstractText,
                TOPIC,
                author,
                null,
                SOURCE_NAME,
                "ACADEMIC",
                0.9,
                publishedAt
        );
    }

    private String extractAbstract(Element article) {
        NodeList abstractNodes = article.getElementsByTagName("Abstract");
        if (abstractNodes.getLength() == 0) {
            return null;
        }
        Element abstractElement = (Element) abstractNodes.item(0);
        NodeList abstractTexts = abstractElement.getElementsByTagName("AbstractText");
        if (abstractTexts.getLength() == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < abstractTexts.getLength(); i++) {
            Element textElement = (Element) abstractTexts.item(i);
            String label = textElement.getAttribute("Label");
            String text = textElement.getTextContent();
            if (text != null && !text.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" ");
                }
                if (label != null && !label.isEmpty()) {
                    sb.append(label).append(": ");
                }
                sb.append(text.trim());
            }
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String extractFirstAuthor(Element article) {
        NodeList authorLists = article.getElementsByTagName("AuthorList");
        if (authorLists.getLength() == 0) {
            return null;
        }
        Element authorList = (Element) authorLists.item(0);
        NodeList authors = authorList.getElementsByTagName("Author");
        if (authors.getLength() == 0) {
            return null;
        }
        Element firstAuthor = (Element) authors.item(0);
        String lastName = getElementText(firstAuthor, "LastName");
        String initials = getElementText(firstAuthor, "Initials");
        if (lastName == null) {
            return null;
        }
        if (initials != null) {
            return lastName + " " + initials;
        }
        return lastName;
    }

    private Instant extractPubDate(Element articleElement) {
        // Try PubMedPubDate with PubStatus="pubmed" first, then Article > Journal > PubDate
        NodeList pubDates = articleElement.getElementsByTagName("PubDate");
        if (pubDates.getLength() == 0) {
            return null;
        }
        Element pubDate = (Element) pubDates.item(0);
        String year = getElementText(pubDate, "Year");
        String month = getElementText(pubDate, "Month");
        String day = getElementText(pubDate, "Day");

        if (year == null) {
            return null;
        }
        try {
            int y = Integer.parseInt(year);
            int m = parseMonth(month);
            int d = day != null ? Integer.parseInt(day) : 1;
            LocalDate date = LocalDate.of(y, m, d);
            return date.atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private int parseMonth(String month) {
        if (month == null) {
            return 1;
        }
        try {
            return Integer.parseInt(month);
        } catch (NumberFormatException e) {
            // PubMed uses 3-letter month abbreviations
            switch (month.toLowerCase()) {
                case "jan": return 1;
                case "feb": return 2;
                case "mar": return 3;
                case "apr": return 4;
                case "may": return 5;
                case "jun": return 6;
                case "jul": return 7;
                case "aug": return 8;
                case "sep": return 9;
                case "oct": return 10;
                case "nov": return 11;
                case "dec": return 12;
                default: return 1;
            }
        }
    }

    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return text != null && !text.isBlank() ? text.trim() : null;
    }
}
