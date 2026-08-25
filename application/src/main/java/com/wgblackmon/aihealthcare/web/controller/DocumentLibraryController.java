package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DocumentRecord;
import com.wgblackmon.aihealthcare.domain.model.DocumentStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.DocumentLibraryPort;
import com.wgblackmon.aihealthcare.domain.service.DocumentUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ADMIN-only Thymeleaf controller for the Document Library — a browser-based
 * upload interface that accepts PDF, DOCX, and TXT files, ingests them into
 * the vector store with paragraph-aware chunking, and compiles them into the
 * public wiki knowledge base.
 *
 * <p>Exposes two endpoints:
 * <ul>
 *   <li>{@code GET /admin/documents} — library page listing all uploaded documents
 *       with status badges and wiki deep-links.</li>
 *   <li>{@code POST /admin/documents/upload} — multipart file upload; validates,
 *       saves to disk, triggers the ingestion pipeline, then PRG-redirects.</li>
 * </ul>
 *
 * <p>Access is restricted to the {@code ADMIN} role via the {@code /admin/**}
 * rule already present in {@code SecurityConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-25
 * @updated 2026-08-25
 */
@Slf4j
@Controller
@RequestMapping("/admin/documents")
public class DocumentLibraryController {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx", "txt", "md");
    private static final long MAX_BYTES = 50L * 1024 * 1024; // 50 MB
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.of("America/Chicago"));

    private final DocumentUploadService uploadService;
    private final DocumentLibraryPort   documentLibraryPort;
    private final String                uploadDir;

    public DocumentLibraryController(DocumentUploadService uploadService,
                                      DocumentLibraryPort documentLibraryPort,
                                      @Value("${aihealthcare.documents.upload-dir:/tmp/aihealthcare-documents}")
                                      String uploadDir) {
        log.debug("DocumentLibraryController() | uploadService={}, documentLibraryPort={}, uploadDir={}",
                uploadService.getClass().getSimpleName(),
                documentLibraryPort.getClass().getSimpleName(),
                uploadDir);
        this.uploadService       = uploadService;
        this.documentLibraryPort = documentLibraryPort;
        this.uploadDir           = uploadDir;
    }

    /**
     * Renders the Document Library page with all uploaded documents.
     *
     * @param model    Thymeleaf model.
     * @param message  Optional success message from a redirect.
     * @param error    Optional error message from a redirect.
     * @return the "document-library" view name.
     */
    @GetMapping
    public String library(Model model,
                          @RequestParam(required = false) String message,
                          @RequestParam(required = false) String error) {
        log.debug("library() | message={}, error={}", message, error);

        List<DocumentRecord> documents = documentLibraryPort.findAll();

        Map<String, String> uploadedAtMap = new LinkedHashMap<>();
        for (DocumentRecord doc : documents) {
            uploadedAtMap.put(doc.docId(), DISPLAY_FMT.format(doc.uploadedAt()));
        }

        model.addAttribute("documents", documents);
        model.addAttribute("documentCount", documents.size());
        model.addAttribute("uploadedAtMap", uploadedAtMap);
        model.addAttribute("message", message);
        model.addAttribute("error", error);

        log.debug("library() | return=document-library, documents={}", documents.size());
        return "document-library";
    }

    /**
     * Accepts a multipart file upload, validates it, saves it to disk,
     * and runs the full ingestion pipeline.  Redirects to the library list
     * page (PRG pattern) with a success or error flash parameter.
     *
     * @param file         The uploaded file.
     * @param sourceLabel  Attribution label from the upload form.
     * @param redirectAttrs Spring MVC redirect attributes for flash messages.
     * @return Redirect to {@code GET /admin/documents}.
     */
    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam("sourceLabel") String sourceLabel,
                         RedirectAttributes redirectAttrs) {
        log.debug("upload() | filename={}, sourceLabel={}, size={}",
                file.getOriginalFilename(), sourceLabel, file.getSize());

        String filename = file.getOriginalFilename();

        if (file.isEmpty() || filename == null || filename.isBlank()) {
            log.warn("upload() | rejected: empty file");
            redirectAttrs.addAttribute("error", "No file selected.");
            return "redirect:/admin/documents";
        }

        if (sourceLabel == null || sourceLabel.isBlank()) {
            log.warn("upload() | rejected: missing sourceLabel");
            redirectAttrs.addAttribute("error", "Source label is required.");
            return "redirect:/admin/documents";
        }

        String ext = extension(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            log.warn("upload() | rejected: unsupported extension={}", ext);
            redirectAttrs.addAttribute("error",
                    "Unsupported file type '" + ext + "'. Allowed: pdf, docx, txt, md.");
            return "redirect:/admin/documents";
        }

        if (file.getSize() > MAX_BYTES) {
            log.warn("upload() | rejected: file too large, size={}", file.getSize());
            redirectAttrs.addAttribute("error",
                    "File exceeds maximum size of 50 MB.");
            return "redirect:/admin/documents";
        }

        Path savedPath;
        try {
            savedPath = saveToUploadDir(file, filename);
        } catch (IOException e) {
            log.error("upload() | disk save failed: {}", e.getMessage());
            redirectAttrs.addAttribute("error", "Failed to save file: " + e.getMessage());
            return "redirect:/admin/documents";
        }

        try {
            DocumentRecord result = uploadService.uploadAndIngest(savedPath, filename, sourceLabel.trim());
            if (result.status() == DocumentStatus.FAILED) {
                log.warn("upload() | upload failed docId={}, filename={}, errorMessage={}",
                        result.docId(), filename, result.errorMessage());
                redirectAttrs.addAttribute("error",
                        "'" + filename + "' upload failed: " + result.errorMessage());
            } else {
                log.info("upload() | upload succeeded docId={}, filename={}, status={}",
                        result.docId(), filename, result.status());
                redirectAttrs.addAttribute("message",
                        "'" + filename + "' uploaded and ingested successfully (" + result.chunkCount() + " chunks).");
            }
        } catch (Exception e) {
            log.error("upload() | ingestion failed for file={}: {}", filename, e.getMessage());
            redirectAttrs.addAttribute("error", "Ingestion failed: " + e.getMessage());
        }

        log.debug("upload() | return=redirect:/admin/documents");
        return "redirect:/admin/documents";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Path saveToUploadDir(MultipartFile file, String filename) throws IOException {
        log.debug("saveToUploadDir() | filename={}, uploadDir={}", filename, uploadDir);
        String subDir = UUID.randomUUID().toString();
        Path destDir = Paths.get(uploadDir, subDir);
        Files.createDirectories(destDir);
        Path dest = destDir.resolve(sanitizeFilename(filename));
        file.transferTo(dest.toFile());
        log.debug("saveToUploadDir() | return={}", dest);
        return dest;
    }

    private String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(dot + 1) : "";
    }

    /** Strips path traversal characters from the original filename. */
    private String sanitizeFilename(String filename) {
        String name = Paths.get(filename).getFileName().toString();
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
