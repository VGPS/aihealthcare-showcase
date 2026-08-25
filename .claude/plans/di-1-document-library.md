# DI-1: Document Library — Local File Drop → Vector Store + Wiki

## Goal
Allow ADMIN to upload PDFs, DOCX, and TXT files from the browser (no SCP required),
ingest them into the pgvector store with paragraph-aware chunking, compile them into
public WikiPages with source attribution, and track all uploads in a document library
admin page.

## Why
LinkedIn connections and other contacts will share research PDFs. These should flow into
the knowledge graph (wiki + vector search) with the same provenance as harvested articles.
Submitters are informed their documents will appear in the public wiki.

## Scope
- No changes to existing `DocumentIngestionService` chunking logic for the API path
- No changes to existing `POST /api/v1/documents/ingest` (kept for programmatic use)
- ADMIN-only upload UI — no subscriber-facing upload capability
- Wiki pages created from documents are public (same as all wiki pages)
- One slice; no phased delivery

---

## Files — New (8)

| File | Package / Path | Purpose |
|------|---------------|---------|
| `DocumentRecord.java` | `domain.model` | Immutable record: docId, filename, sourceLabel, uploadedAt, chunkCount, wikiPageSlug (nullable), status |
| `DocumentStatus.java` | `domain.model` | Enum: UPLOADED, INGESTING, INDEXED, WIKI_COMPILED, FAILED |
| `DocumentLibraryPort.java` | `domain.port.outbound` | `save(DocumentRecord)`, `findAll()`, `findById(String)`, `updateStatus(String, DocumentStatus, String wikiSlug)` |
| `DocumentLibraryEntity.java` | `infrastructure.persistence` | JPA entity: `document_library` table (ddl-auto creates it) |
| `DocumentLibraryRepository.java` | `infrastructure.persistence` | `JpaRepository<DocumentLibraryEntity, String>` |
| `DocumentLibraryAdapter.java` | `infrastructure.persistence` | Implements `DocumentLibraryPort` |
| `DocumentLibraryController.java` | `web.controller` | `GET /admin/documents` (list page), `POST /admin/documents/upload` (multipart) |
| `document-library.html` | `resources/templates` | Upload card + ingested-docs table with status badges and wiki links |

## Files — Modified (4)

| File | Change |
|------|--------|
| `DocumentIngestionService.java` | Add `ChunkingStrategy` enum param; add `PARAGRAPH_OVERLAP` implementation; after vector store, call `KnowledgeCompilationPort` to create WikiPage |
| `IngestDocumentsUseCase.java` | Add overload: `ingest(Path singleFile, String sourceLabel, ChunkingStrategy strategy)` for single-file upload path |
| `AppConfig.java` | Wire `DocumentLibraryPort` into `DocumentIngestionService`; add `@Value("${aihealthcare.documents.upload-dir}")` |
| `admin-pipelines.html` | Add "Document Library" pipeline card linking to `/admin/documents` |

---

## Chunking Strategy

### Current (FIXED — kept as default for directory batch path)
- Split text blocks at 1000-char boundaries, break at whitespace
- No overlap between chunks

### New: PARAGRAPH_OVERLAP (default for single-file upload path)
```
Algorithm:
1. Parse file → raw text (PDFBox / Apache POI as today)
2. Split on \n\n (double newline) → paragraph list
3. Aggregate short paragraphs (< 200 chars) into the previous chunk
4. Cap each chunk at 800 chars; overflow splits at sentence boundary (". " + uppercase)
5. Prepend 150-char tail of previous chunk to each chunk (overlap window)
6. Emit as DocumentChunk list
```

Why paragraph-aware wins for research PDFs:
- Preserves complete arguments and findings
- Doesn't split mid-sentence or mid-table-row
- 150-char overlap ensures context isn't lost at boundaries
- Better cosine similarity during retrieval (complete semantic units)

---

## Wiki Compilation Step

After `DocumentVectorPort.store(chunks)` succeeds:

```java
// Convert chunks to pseudo-NewsArticles for wiki compilation
List<NewsArticle> pseudoArticles = new ArrayList<>();
for (DocumentChunk chunk : allChunks) {
    pseudoArticles.add(new NewsArticle(
        chunk.chunkId(),          // articleId
        sourceLabel + " — " + chunk.sourceFile(),  // title
        URI.create(baseUrl + "/admin/documents"),   // url (back-link)
        chunk.content(),          // bodyText
        "Document",               // topic
        null,                     // author
        null,                     // topicId
        sourceLabel,              // sourceName
        "ACADEMIC",               // sourceTier
        0.85,                     // sourceWeight
        uploadedAt                // publishedAt
    ));
}
CompilationReport report = knowledgeCompilationPort.compileNewSources(pseudoArticles);
// report.createdSlugs() gives the wiki page slug(s) created
```

The existing `KnowledgeCompilationPort` / `WikiCompilationAdapter` handles the rest:
creates WikiPage(s), detects contradictions, writes provenance `SourceRef` records.

WikiPage attribution will show: `"Source: [filename] (uploaded [date])"` via the
`sourceName` field surfaced in `wiki-detail.html`'s provenance table.

---

## Upload Flow (single file)

```
Browser (admin) → drag PDF onto document-library.html
      ↓
POST /admin/documents/upload  (multipart/form-data, file + sourceLabel)
      ↓
DocumentLibraryController:
  1. Validate: file not null, size < 50MB, extension in {pdf, docx, txt, md}
  2. Save to ${aihealthcare.documents.upload-dir}/{uuid}/{filename}
  3. Create DocumentRecord(status=UPLOADED), persist via DocumentLibraryPort
  4. Call ingestUseCase.ingest(path, sourceLabel, PARAGRAPH_OVERLAP)
  5. Call knowledgeCompilationPort.compileNewSources(pseudoArticles)
  6. Update DocumentRecord(status=WIKI_COMPILED, wikiPageSlug=...)
  7. Redirect to GET /admin/documents  (PRG pattern)
```

Upload is synchronous (documents are small — PDFs rarely exceed a few MB).
If compilation fails, status = INDEXED (vector store still has the chunks).

---

## document-library.html Layout

```
┌─────────────────────────────────────────────┐
│  Document Library              [ADMIN]       │
│                                              │
│  ┌─────────────────────────────────────────┐│
│  │  Drop PDF / DOCX / TXT here             ││
│  │  or [Choose File]   Source Label: [___] ││
│  │                     [Upload & Ingest]   ││
│  └─────────────────────────────────────────┘│
│                                              │
│  Ingested Documents (N)                      │
│  ┌──────────┬────────┬────────┬───────────┐ │
│  │ Filename │ Label  │ Chunks │ Wiki Page │ │
│  ├──────────┼────────┼────────┼───────────┤ │
│  │ paper.pdf│ Smith  │   42   │ /wiki/... │ │
│  │ ...      │ ...    │  ...   │  ...      │ │
│  └──────────┴────────┴────────┴───────────┘ │
└─────────────────────────────────────────────┘
```

Status badges: `UPLOADED` (gray) / `INDEXED` (blue) / `WIKI_COMPILED` (green) / `FAILED` (red)

---

## Database — New Table

```sql
-- auto-created by JPA ddl-auto
CREATE TABLE document_library (
    doc_id        VARCHAR(36)  PRIMARY KEY,
    filename      VARCHAR(500) NOT NULL,
    source_label  VARCHAR(200) NOT NULL,
    uploaded_at   TIMESTAMPTZ  NOT NULL,
    chunk_count   INTEGER      NOT NULL DEFAULT 0,
    wiki_page_slug VARCHAR(300),
    status        VARCHAR(30)  NOT NULL,
    error_message TEXT
);
```

---

## application.yml Addition

```yaml
aihealthcare:
  documents:
    upload-dir: /opt/aihealthcare/documents   # EC2 path; local: C:/aihealthcare/documents
    max-file-size-mb: 50
```

Spring multipart config (already in Spring Boot defaults, no change needed for ≤50MB).

---

## Security

- `GET /admin/documents` and `POST /admin/documents/upload` → `hasRole("ADMIN")` (already covered by `/admin/**` rule in `SecurityConfig`)
- No change to SecurityConfig needed

---

## Tests (target: ~20 selective tests)

| Test Class | Count | What it covers |
|---|---|---|
| `DocumentLibraryControllerTest` | 8 | GET list, POST upload happy path, POST invalid extension, POST oversized file, POST missing label, redirect after success, status badge rendering, wiki link present |
| `DocumentLibraryAdapterTest` | 5 | save, findAll, findById, updateStatus, not-found returns empty |
| `DocumentIngestionServiceTest` | 7 | PARAGRAPH_OVERLAP chunking: short paragraphs aggregated, long paragraph split at sentence, overlap applied, blank paragraphs skipped, wiki compilation called after store, status updated to WIKI_COMPILED on success, status updated to INDEXED on compilation failure |

---

## Build Order

1. Domain: `DocumentStatus`, `DocumentRecord`
2. Port: `DocumentLibraryPort`
3. JPA: `DocumentLibraryEntity`, `DocumentLibraryRepository`, `DocumentLibraryAdapter`
4. Service: `DocumentIngestionService` — add `PARAGRAPH_OVERLAP` + wiki compilation step
5. Config: `AppConfig` — wire `DocumentLibraryPort` + `upload-dir`
6. Web: `DocumentLibraryController` + `document-library.html`
7. Template: `admin-pipelines.html` — add card
8. Tests

---

## Open Questions (resolved)
- **Public wiki?** Yes — documents appear in public wiki index. Submitters informed.
- **Chunking default for upload?** `PARAGRAPH_OVERLAP`. Existing batch API keeps `FIXED`.
- **Upload size limit?** 50 MB (covers any realistic whitepaper or research PDF).
- **Synchronous or async?** Synchronous — PDFs are small, compilation takes <10s.
