package com.wgblackmon.aihealthcare.web.util;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ArticleToneClassifier}.
 *
 * Tests tone classification, emoji mapping, hashtag base content,
 * and dynamic hashtag selection for both LinkedIn and Facebook.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-23
 * @updated 2026-08-23
 */
class ArticleToneClassifierTest {

    private ArticleToneClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ArticleToneClassifier();
    }

    // ------------------------------------------------------------------
    // Tone classification
    // ------------------------------------------------------------------

    @Test
    void classifyTone_ugly_forRecallKeyword() {
        NewsArticle a = article("x", "FDA Recalls AI Diagnostic Device Over Safety Concerns",
                "The FDA issued a recall notice citing patient harm.");
        assertThat(classifier.classifyTone(a)).isEqualTo("UGLY");
    }

    @Test
    void classifyTone_bad_forInvestigationKeyword() {
        NewsArticle a = article("x", "DOJ Launches Investigation into AI Billing Vendor",
                "Federal investigators opened a formal investigation.");
        assertThat(classifier.classifyTone(a)).isEqualTo("BAD");
    }

    @Test
    void classifyTone_good_forApprovalKeyword() {
        NewsArticle a = article("x", "FDA Clears AI Tool for Early Cancer Detection",
                "The FDA approved a breakthrough AI diagnostic tool.");
        assertThat(classifier.classifyTone(a)).isEqualTo("GOOD");
    }

    @Test
    void classifyTone_promo_forMarketingLanguage() {
        NewsArticle a = article("x", "MedAI Proud to Announce Industry-Leading Platform",
                "We are proud to announce our award-winning, best-in-class solution.");
        assertThat(classifier.classifyTone(a)).isEqualTo("PROMO");
    }

    @Test
    void classifyTone_neutral_forInformationalArticle() {
        NewsArticle a = article("x", "Overview of AI Applications in Radiology",
                "Researchers reviewed current AI applications in radiology departments.");
        assertThat(classifier.classifyTone(a)).isEqualTo("NEUTRAL");
    }

    @Test
    void classifyTone_ugly_takesPrecedenceOverBad() {
        NewsArticle a = article("x", "Investigation Launched After AI Tool Linked to Patient Harm",
                "Both harm (UGLY) and investigation (BAD) present.");
        assertThat(classifier.classifyTone(a)).isEqualTo("UGLY");
    }

    @Test
    void classifyTone_good_takesPrecedenceOverPromo() {
        NewsArticle a = article("x", "FDA Cleared Our Industry-Leading Device",
                "We are proud to announce the FDA has cleared our device for clinical use.");
        assertThat(classifier.classifyTone(a)).isEqualTo("GOOD");
    }

    // ------------------------------------------------------------------
    // Emoji mapping
    // ------------------------------------------------------------------

    @Test
    void toneEmoji_returnsCorrectEmojiForEachTone() {
        assertThat(classifier.toneEmoji("UGLY")).isEqualTo("🚨 ");
        assertThat(classifier.toneEmoji("BAD")).isEqualTo("⚠️ ");
        assertThat(classifier.toneEmoji("GOOD")).isEqualTo("✅ ");
        assertThat(classifier.toneEmoji("PROMO")).isEqualTo("🙄 ");
        assertThat(classifier.toneEmoji("NEUTRAL")).isEqualTo("ℹ️ ");
        assertThat(classifier.toneEmoji("UNKNOWN")).isEqualTo("ℹ️ ");
    }

    @Test
    void toneEmoji_articleConvenience_classifiesAndMaps() {
        NewsArticle a = article("x", "FDA Recalls AI Device", "recall notice issued.");
        assertThat(classifier.toneEmoji(a)).isEqualTo("🚨 ");
    }

    // ------------------------------------------------------------------
    // LinkedIn hashtags
    // ------------------------------------------------------------------

    @Test
    void linkedInHashtags_containsAllBaseTags() {
        String tags = classifier.linkedInHashtags(List.of());
        assertThat(tags).contains("#HealthcareAI", "#AIinHealthcare", "#DigitalHealth",
                "#HealthTech", "#MedicalInnovation");
    }

    @Test
    void linkedInHashtags_addsLegalTag_forLegalArticle() {
        NewsArticle legal = article("x", "FTC Files Antitrust Lawsuit Against AI Vendor",
                "Antitrust case filed in federal court.");
        String tags = classifier.linkedInHashtags(List.of(legal));
        assertThat(tags).contains("#HealthcareLaw");
    }

    @Test
    void linkedInHashtags_addsDealsTag_forAcquisitionArticle() {
        NewsArticle deal = article("x", "Microsoft Acquires AI Health Startup for $2B",
                "The acquisition was announced Monday.");
        String tags = classifier.linkedInHashtags(List.of(deal));
        assertThat(tags).contains("#HealthTechDeals");
    }

    @Test
    void linkedInHashtags_addsPolicyTag_forFdaArticle() {
        NewsArticle policy = article("x", "FDA Issues Draft Guidance on AI Medical Devices",
                "The fda guidance covers pre-market submissions.");
        String tags = classifier.linkedInHashtags(List.of(policy));
        assertThat(tags).contains("#HealthPolicy");
    }

    // ------------------------------------------------------------------
    // Facebook hashtags
    // ------------------------------------------------------------------

    @Test
    void facebookHashtags_containsBigSkyLabsTag() {
        String tags = classifier.facebookHashtags(List.of());
        assertThat(tags).contains("#BigSkyLabs");
    }

    @Test
    void facebookHashtags_doesNotContainMedicalInnovation() {
        // MedicalInnovation is LinkedIn-only; FB has BigSkyLabs instead
        String tags = classifier.facebookHashtags(List.of());
        assertThat(tags).doesNotContain("#MedicalInnovation");
    }

    @Test
    void facebookHashtags_addsPatientSafetyTag_forUglyArticle() {
        NewsArticle ugly = article("x", "AI Device Recall Linked to Patient Deaths",
                "Recall issued after reports of patient harm.");
        String tags = classifier.facebookHashtags(List.of(ugly));
        assertThat(tags).contains("#PatientSafety");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private NewsArticle article(String id, String title, String body) {
        return new NewsArticle(
                id, title, URI.create("https://example.com/" + id),
                body, "ai-healthcare", null, null,
                "Test Source", "INDUSTRY", 0.8, Instant.now()
        );
    }
}
