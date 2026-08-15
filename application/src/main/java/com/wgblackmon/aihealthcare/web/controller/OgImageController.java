package com.wgblackmon.aihealthcare.web.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * Generates a 1200×630 Open Graph preview image at {@code GET /press/og.png}.
 *
 * <p>Used as {@code og:image} on the /press page so Facebook, LinkedIn, and
 * Slack display a visual card when the URL is pasted. Served with a 24-hour
 * cache header so scrapers don't hammer it.
 *
 * @author  Bill Blackmon
 * @since   2026-08-15
 * @updated 2026-08-15
 */
@Slf4j
@RestController
public class OgImageController {

    private static final int W = 1200;
    private static final int H = 630;

    // Brand colours
    private static final Color BG_TOP      = new Color(0x0d, 0x1b, 0x2a);
    private static final Color BG_BOT      = new Color(0x0f, 0x34, 0x60);
    private static final Color ACCENT      = new Color(0x4d, 0xa6, 0xff);
    private static final Color WHITE       = Color.WHITE;
    private static final Color GRAY        = new Color(0xa0, 0xae, 0xc0);
    private static final Color MUTED       = new Color(0x71, 0x80, 0x96);
    private static final Color CARD_BG     = new Color(0x1a, 0x2a, 0x3a);
    private static final Color CARD_BORDER = new Color(0x2d, 0x3d, 0x50);
    private static final Color TEAL_DIM    = new Color(0x4d, 0xa6, 0xff, 40);

    @GetMapping(value = "/press/og.png", produces = "image/png")
    public ResponseEntity<byte[]> ogImage() throws Exception {
        log.debug("ogImage() | generating 1200x630 OG card");

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,     RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,        RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        // Background gradient
        g.setPaint(new GradientPaint(0, 0, BG_TOP, W, H, BG_BOT));
        g.fillRect(0, 0, W, H);

        // Decorative circle — right side
        g.setColor(TEAL_DIM);
        g.fillOval(820, -80, 520, 520);
        g.setColor(new Color(0x4d, 0xa6, 0xff, 18));
        g.fillOval(900, 200, 380, 380);

        // Top accent bar
        g.setColor(ACCENT);
        g.fillRect(0, 0, W, 5);

        int lx = 70; // left margin

        // Brand label
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.setColor(ACCENT);
        g.drawString("BIGSKYLABS.AI  ·  AI HEALTHCARE INTELLIGENCE", lx, 65);

        // Headline — line 1
        g.setFont(new Font("SansSerif", Font.BOLD, 72));
        g.setColor(WHITE);
        g.drawString("Built this entire platform", lx, 175);

        // Headline — line 2 (accent word highlighted)
        g.setFont(new Font("SansSerif", Font.BOLD, 72));
        g.setColor(WHITE);
        g.drawString("solo, with ", lx, 265);
        FontMetrics fm72 = g.getFontMetrics();
        int soloW = fm72.stringWidth("solo, with ");
        g.setColor(ACCENT);
        g.drawString("Claude Code.", lx + soloW, 265);

        // Description
        g.setFont(new Font("SansSerif", Font.PLAIN, 24));
        g.setColor(GRAY);
        g.drawString("57+ data sources · 5 LLM providers · multi-LLM cross-verification", lx, 325);
        g.drawString("with claim-level confidence scoring and source provenance.", lx, 358);

        // Stat cards
        drawStatCard(g, lx,        430, "57+",   "Data Sources");
        drawStatCard(g, lx + 185,  430, "5",     "LLM Providers");
        drawStatCard(g, lx + 370,  430, "1,837", "Auto Tests");
        drawStatCard(g, lx + 555,  430, "590",   "Java Classes");
        drawStatCard(g, lx + 740,  430, "48",    "Dashboards");

        // Bottom CTA
        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(ACCENT);
        g.drawString("app.bigskylabs.ai  ·  Free 7-day trial — no credit card", lx, 590);

        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        byte[] bytes = out.toByteArray();

        log.debug("ogImage() | return={} bytes", bytes.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "image/png")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(bytes);
    }

    private void drawStatCard(Graphics2D g, int x, int y, String value, String label) {
        int w = 170, h = 90;

        // Card background
        RoundRectangle2D rr = new RoundRectangle2D.Float(x, y, w, h, 14, 14);
        g.setColor(CARD_BG);
        g.fill(rr);
        g.setColor(CARD_BORDER);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(rr);

        // Value
        g.setFont(new Font("SansSerif", Font.BOLD, 34));
        g.setColor(WHITE);
        FontMetrics fmV = g.getFontMetrics();
        int vw = fmV.stringWidth(value);
        g.drawString(value, x + (w - vw) / 2, y + 52);

        // Label
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        g.setColor(MUTED);
        FontMetrics fmL = g.getFontMetrics();
        int lw = fmL.stringWidth(label);
        g.drawString(label, x + (w - lw) / 2, y + 73);
    }
}
