# AIHealthcare — Code Flow Diagrams

> Generated: 2026-04-20 | GitHub renders Mermaid natively — view this file on GitHub for interactive diagrams.

---

## 1. Article Ingestion Pipeline

Three independent harvesters feed articles into the shared `ArticleStoragePort`.

```mermaid
flowchart TD
    subgraph Schedulers
        FHS["FeedHarvestScheduler<br/><i>@Scheduled daily + 4h</i>"]
        WMS["WebMonitoringScheduler<br/><i>@Scheduled daily 07:00 UTC</i>"]
        WMS2["WebMonitoringScheduler<br/><i>@Scheduled daily 07:30 UTC</i>"]
    end

    subgraph RSS Harvesting
        FHS --> RFH["RomeFeedHarvester<br/><i>implements ArticleHarvestingPort</i>"]
        RFH -->|"parse RSS XML"| FEED[(RSS Feeds<br/>PubMed, etc.)]
        RFH -->|"List&lt;NewsArticle&gt;"| ASP
    end

    subgraph Web Scraping
        WMS --> WPH["WebPageHarvester"]
        WPH -->|"jsoup fetch + CSS select"| COMP[(Competitor Pages<br/>Anthropic, Perplexity,<br/>Google)]
        WPH -->|"SHA-256 hash"| CHP["ContentHashPort"]
        CHP --> CHA["ContentHashAdapter"]
        CHA --> PCHE[("page_content_hashes<br/>(PostgreSQL)")]
        WPH -->|"List&lt;NewsArticle&gt;<br/>(changed pages only)"| ASP
    end

    subgraph HuggingFace Discovery
        WMS2 --> HFH["HuggingFaceHarvester"]
        HFH -->|"HTTP GET /api/models"| HFAPI[(HuggingFace API)]
        HFH -->|"List&lt;NewsArticle&gt;"| ASP
    end

    subgraph Persistence
        ASP["ArticleStoragePort"]
        ASP --> ASA["ArticleStorageAdapter"]
        ASA -->|"dedup by URL"| NAR["NewsArticleRepository"]
        NAR --> DB[("news_articles<br/>(PostgreSQL)")]
    end

    style FHS fill:#e1f5fe
    style WMS fill:#e1f5fe
    style WMS2 fill:#e1f5fe
    style DB fill:#fff3e0
    style PCHE fill:#fff3e0
```

---

## 2. Article Query Flow

How `GET /api/v1/articles?topic=health&limit=20` is processed.

```mermaid
sequenceDiagram
    participant Client
    participant AC as ArticleController
    participant AIA as ArticleIngestionAdapter
    participant NAR as NewsArticleRepository
    participant DB as PostgreSQL

    Client->>AC: GET /articles?topic=health&limit=20
    Note over AC: log.debug("listArticles() | topic=health, limit=20")

    AC->>AIA: fetchArticles("health", 20)
    Note over AIA: log.debug("fetchArticles() | topic=health,<br/>maxArticles=20, daysBack=7")

    alt daysBack > 0
        Note over AIA: cutoff = Instant.now() - 7 days
        AIA->>NAR: findByTopicContainingIgnoreCase<br/>AndCreatedAtAfter("health", cutoff)
    else daysBack = 0
        AIA->>NAR: findByTopicContainingIgnoreCase("health")
    end

    NAR->>DB: SELECT * FROM news_articles<br/>WHERE LOWER(topic) LIKE '%health%'<br/>AND created_at > cutoff
    DB-->>NAR: List&lt;NewsArticleEntity&gt;

    NAR-->>AIA: entities (10 rows)
    Note over AIA: log.debug("query returned 10 entities")

    loop for each entity (up to limit)
        Note over AIA: toDomain() — map Entity → NewsArticle record
    end

    AIA-->>AC: List&lt;NewsArticle&gt; (10)
    Note over AC: log.debug("return=10 articles")

    loop for each article
        Note over AC: map NewsArticle → ArticleResponse DTO
    end

    AC-->>Client: 200 OK — JSON array (10 articles)
```

---

## 3. Newsletter Generation Flow

End-to-end: ingest articles, AI-summarize, render HTML, persist run.

```mermaid
sequenceDiagram
    participant Caller as Controller / Scheduler
    participant NS as NewsletterService
    participant AIP as ArticleIngestionPort
    participant ASP as AiSummarizationPort
    participant NR as NewsletterRenderer
    participant NRP as NewsletterRunPort
    participant DB as PostgreSQL

    Caller->>NS: ingest(runId, weekOf, topics, maxArticles)
    loop for each topic
        NS->>AIP: fetchArticles(topic, maxArticles)
        AIP-->>NS: List&lt;NewsArticle&gt;
    end
    NS-->>Caller: all articles (stored in memory by runId)

    Caller->>NS: generate(runId, draftId, title, tone, maxSections)
    Note over NS: retrieve articles by runId

    loop for each topic's articles
        NS->>ASP: summarize(articles, topic, tone, promptTemplate)
        Note over ASP: ChatClient → Anthropic Claude API
        ASP-->>NS: NewsletterSection
    end

    Note over NS: build NewsletterDraft(intro, sections, sources)

    NS->>NR: renderHtml(draft)
    NR-->>NS: htmlContent
    NS->>NR: renderPlainText(draft)
    NR-->>NS: plainTextContent

    NS->>NRP: save(NewsletterRun)
    NRP->>DB: INSERT INTO newsletter_runs

    NS-->>Caller: NewsletterDraft
```

---

## 4. Newsletter Delivery Flow

Scheduled or manual trigger: look up run, fetch subscribers, send emails.

```mermaid
sequenceDiagram
    participant Trigger as Scheduler / Controller
    participant DS as DeliveryService
    participant NRP as NewsletterRunPort
    participant SP as SubscriberPort
    participant NDP as NewsletterDeliveryPort
    participant SMTP as SMTP Server

    Trigger->>DS: deliver(runId)
    DS->>NRP: findByRunId(runId)
    NRP-->>DS: NewsletterRun (html + plainText)

    DS->>SP: findAllActive()
    SP-->>DS: List&lt;Subscriber&gt;

    DS->>NDP: deliver(run, subscribers)

    loop for each subscriber
        NDP->>SMTP: MIME multipart email<br/>(HTML + plain-text alternative)
        Note over NDP: catch per-recipient failures
    end

    NDP-->>DS: void

    DS->>NRP: save(run with status=SENT)
    DS-->>Trigger: void
```

---

## 5. Prompt Evaluation Flow

Compare prompt variants by having the LLM score its own output.

```mermaid
sequenceDiagram
    participant Client
    participant PEC as PromptEvaluationController
    participant PES as PromptEvaluationService
    participant PVP as PromptVariantPort
    participant AIP as ArticleIngestionPort
    participant ASP as AiSummarizationPort
    participant AEP as AiEvaluationPort
    participant ERP as EvaluationResultPort

    Client->>PEC: POST /evaluations {variantId, topic, articleIds}
    PEC->>PES: evaluate(variantId, topic, articleIds)

    PES->>PVP: findById(variantId)
    PVP-->>PES: PromptVariant

    PES->>AIP: fetchArticlesByIds(articleIds)
    AIP-->>PES: List&lt;NewsArticle&gt;

    PES->>ASP: summarize(articles, topic, tone, variant.template)
    Note over ASP: Generate section using variant's prompt
    ASP-->>PES: NewsletterSection

    PES->>AEP: evaluate(section, articles)
    Note over AEP: LLM-as-judge scores on 5 dimensions<br/>(accuracy, coverage, clarity, tone, attribution)
    AEP-->>PES: EvaluationScore

    PES->>ERP: save(EvaluationResult)
    PES-->>PEC: EvaluationResult
    PEC-->>Client: 200 OK — EvaluationResultResponse
```

---

## 6. Hexagonal Architecture Overview

```mermaid
flowchart LR
    subgraph Web Layer
        NC["NewsletterController"]
        AC["ArticleController"]
        SC["SubscriberController"]
        PVC["PromptVariantController"]
        PEC["PromptEvaluationController"]
        WMC["WebMonitoringController"]
    end

    subgraph Domain Layer
        subgraph Inbound Ports
            IAU["IngestArticlesUseCase"]
            GNU["GenerateNewsletterUseCase"]
            MSU["ManageSubscribersUseCase"]
            DNU["DeliverNewsletterUseCase"]
            EPU["EvaluatePromptsUseCase"]
        end
        subgraph Services
            NS["NewsletterService"]
            DS["DeliveryService"]
            PES["PromptEvaluationService"]
        end
        subgraph Outbound Ports
            AIP["ArticleIngestionPort"]
            AISP["AiSummarizationPort"]
            ASTP["ArticleStoragePort"]
            NRP["NewsletterRunPort"]
            NDP["NewsletterDeliveryPort"]
            SUP["SubscriberPort"]
            PVP["PromptVariantPort"]
            AEP["AiEvaluationPort"]
            ERP["EvaluationResultPort"]
            CHP["ContentHashPort"]
            ASR["ArticleSearchPort"]
        end
    end

    subgraph Infrastructure Layer
        subgraph AI Adapters
            ASA["AiSummarizationAdapter<br/><i>ChatClient → Claude</i>"]
            AEA["AiEvaluationAdapter<br/><i>ChatClient → Claude</i>"]
            VSA["VectorStoreArticleSearchAdapter"]
            ES["EmbeddingScheduler"]
        end
        subgraph Persistence Adapters
            ASTA["ArticleStorageAdapter"]
            NRA["NewsletterRunAdapter"]
            SA["SubscriberAdapter"]
            PVA["PromptVariantAdapter"]
            ERA["EvaluationResultAdapter"]
            CHA["ContentHashAdapter"]
        end
        subgraph Ingestion Adapters
            AIA["ArticleIngestionAdapter"]
            RFH["RomeFeedHarvester"]
            WPH["WebPageHarvester"]
            HFH["HuggingFaceHarvester"]
        end
        subgraph Delivery Adapters
            EDA["EmailDeliveryAdapter"]
        end
    end

    NC --> IAU & GNU
    AC --> AIP
    SC --> MSU
    PVC --> EPU
    PEC --> EPU
    WMC --> WPH & HFH

    IAU & GNU -.-> NS
    MSU & DNU -.-> DS
    EPU -.-> PES

    NS --> AIP & AISP & NRP
    DS --> SUP & NRP & NDP
    PES --> PVP & AISP & AEP & AIP & ERP

    AIP -.-> AIA
    AISP -.-> ASA
    ASTP -.-> ASTA
    NRP -.-> NRA
    NDP -.-> EDA
    SUP -.-> SA
    PVP -.-> PVA
    AEP -.-> AEA
    ERP -.-> ERA
    CHP -.-> CHA
    ASR -.-> VSA

    style NS fill:#c8e6c9
    style DS fill:#c8e6c9
    style PES fill:#c8e6c9
    style ASA fill:#bbdefb
    style AEA fill:#bbdefb
```

---

## Configuration Reference

| Property | Default | Effect |
|---|---|---|
| `aihealthcare.articles.days-back` | `7` | Articles older than N days excluded from queries. `0` = no filter. |
| `aihealthcare.embedding.schedule` | `0 0 0 * * *` | Cron for vector embedding job |
| `aihealthcare.newsletter.schedule` | `0 0 8 * * MON` | Cron for newsletter generation |
| `aihealthcare.feeds.sources[].tier` | — | `ACADEMIC`, `REGULATORY`, `INDUSTRY`, `COMPETITOR`, `HUGGINGFACE` |
