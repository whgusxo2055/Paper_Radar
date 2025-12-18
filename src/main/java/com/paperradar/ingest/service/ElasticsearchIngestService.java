package com.paperradar.ingest.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Result;
import com.paperradar.admin.model.ActiveConfig;
import com.paperradar.admin.service.ConfigService;
import com.paperradar.ingest.model.IngestJob;
import com.paperradar.ingest.model.IngestMode;
import com.paperradar.ingest.model.IngestStatus;
import com.paperradar.ingest.model.OpenAlexWork;
import com.paperradar.ingest.service.enrich.WorkLinkEnricher;
import com.paperradar.ingest.openalex.OpenAlexClient;
import com.paperradar.work.link.WorkLink;
import com.paperradar.work.link.WorkLinkPolicy;
import com.paperradar.util.KeywordNormalizeUtil;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ElasticsearchIngestService implements IngestService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIngestService.class);

    private static final String WORKS_INDEX = "works";

    private final ElasticsearchClient esClient;
    private final OpenAlexClient openAlexClient;
    private final ConfigService configService;
    private final IngestJobService ingestJobService;
    private final WorkLinkEnricher workLinkEnricher;

    @Value("${INGEST_LOOKBACK_YEARS:3}")
    private int lookbackYears;

    @Value("${INGEST_INCREMENTAL_LOOKBACK_DAYS:3}")
    private int incrementalLookbackDays;

    @Value("${APP_TIMEZONE:Asia/Seoul}")
    private String timezone;

    @Override
    public IngestJob run(IngestMode mode) {
        return run(mode, null, null);
    }

    @Override
    public IngestJob run(IngestMode mode, LocalDate fromPublicationDate, LocalDate toPublicationDate) {
        IngestJob job = ingestJobService.start(mode);

        if (mode == IngestMode.full && (fromPublicationDate != null || toPublicationDate != null)) {
            ingestJobService.updateMeta(job.jobId(), Map.of(
                    "from_publication_date", fromPublicationDate == null ? "" : fromPublicationDate.toString(),
                    "to_publication_date", toPublicationDate == null ? "" : toPublicationDate.toString()
            ));
        }

        int processed = 0;
        int created = 0;
        int updated = 0;
        int fetchFailed = 0;
        int upsertFailed = 0;
        String errorSummary = null;

        Instant lastProgressUpdateAt = Instant.EPOCH;

        try {
            ActiveConfig cfg = configService.getActiveConfig();
            List<String> keywords = cfg.enabledKeywords().stream()
                    .map(KeywordNormalizeUtil::normalize)
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();
            List<String> institutions = cfg.enabledInstitutions().stream()
                    .map(s -> s == null ? "" : s.trim())
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .toList();

            if (keywords.isEmpty() && institutions.isEmpty()) {
                errorSummary = "No enabled keywords/institutions. Configure at least one before running ingest.";
                ingestJobService.markFinished(job.jobId(), IngestStatus.failed, 0, 0, 0, errorSummary);
                return new IngestJob(
                        job.jobId(),
                        mode,
                        IngestStatus.failed,
                        job.startedAt(),
                        Instant.now(),
                        Instant.now(),
                        null,
                        0,
                        0,
                        0,
                        errorSummary,
                        null,
                        null,
                        fromPublicationDate,
                        toPublicationDate
                );
            }

            LocalDate fromPub = fromPublicationDate;
            LocalDate toPub = toPublicationDate;
            if (mode == IngestMode.full) {
                if (fromPub == null && toPub == null) {
                    fromPub = LocalDate.now(ZoneId.of(timezone)).minusYears(Math.max(1, lookbackYears));
                }
            } else if (mode == IngestMode.incremental) {
                fromPub = LocalDate.now(ZoneId.of(timezone))
                        .minusDays(Math.max(1, incrementalLookbackDays));
            }

            // NOTE: OpenAlex는 `from_updated_date` 필터에 API key를 요구하므로(v1 범위에서는 미사용)
            LocalDate fromUpdatedDate = null;

            Set<String> seen = new LinkedHashSet<>();
            for (String kw : keywords) {
                ingestJobService.updateMeta(job.jobId(), Map.of(
                        "current_source", "keyword",
                        "current_key", kw,
                        "last_heartbeat_at", Instant.now().toString()
                ));
                List<OpenAlexWork> works;
                try {
                    works = openAlexClient.fetchWorksByKeyword(kw, fromPub, toPub, fromUpdatedDate);
                } catch (Exception e) {
                    fetchFailed++;
                    log.warn("Failed to fetch works by keyword: {}", kw, e);
                    continue;
                }
                for (OpenAlexWork w : works) {
                    processed++;
                    UpsertResult r = upsertOpenAlexWork(workLinkEnricher.enrich(w));
                    created += r.created;
                    updated += r.updated;
                    upsertFailed += r.failed;
                    seen.add(w.id());

                    Instant now = Instant.now();
                    if (processed % 50 == 0 || Duration.between(lastProgressUpdateAt, now).toSeconds() >= 3) {
                        lastProgressUpdateAt = now;
                        ingestJobService.updateMeta(job.jobId(), Map.of(
                                "processed_count", processed,
                                "created_count", created,
                                "updated_count", updated,
                                "last_progress_at", now.toString(),
                                "last_heartbeat_at", now.toString()
                        ));
                    }
                }
            }

            for (String instId : institutions) {
                ingestJobService.updateMeta(job.jobId(), Map.of(
                        "current_source", "institution",
                        "current_key", instId,
                        "last_heartbeat_at", Instant.now().toString()
                ));
                List<OpenAlexWork> works;
                try {
                    works = openAlexClient.fetchWorksByInstitution(instId, fromPub, toPub, fromUpdatedDate);
                } catch (Exception e) {
                    fetchFailed++;
                    log.warn("Failed to fetch works by institution: {}", instId, e);
                    continue;
                }
                for (OpenAlexWork w : works) {
                    processed++;
                    UpsertResult r = upsertOpenAlexWork(workLinkEnricher.enrich(w));
                    created += r.created;
                    updated += r.updated;
                    upsertFailed += r.failed;
                    seen.add(w.id());

                    Instant now = Instant.now();
                    if (processed % 50 == 0 || Duration.between(lastProgressUpdateAt, now).toSeconds() >= 3) {
                        lastProgressUpdateAt = now;
                        ingestJobService.updateMeta(job.jobId(), Map.of(
                                "processed_count", processed,
                                "created_count", created,
                                "updated_count", updated,
                                "last_progress_at", now.toString(),
                                "last_heartbeat_at", now.toString()
                        ));
                    }
                }
            }

            IngestStatus status = IngestStatus.success;
            if (processed == 0 && fetchFailed > 0) {
                status = IngestStatus.failed;
                errorSummary = "OpenAlex fetch failed for all configured sources (failed=%d). See logs for details."
                        .formatted(fetchFailed);
            } else if (processed > 0 && (created + updated) == 0 && upsertFailed > 0) {
                status = IngestStatus.failed;
                errorSummary = "Elasticsearch upsert failed for all fetched works (failed=%d). Check ES connectivity/mapping."
                        .formatted(upsertFailed);
            } else if (fetchFailed > 0 || upsertFailed > 0) {
                errorSummary = "Partial issues: fetchFailed=%d, upsertFailed=%d".formatted(fetchFailed, upsertFailed);
            } else {
                errorSummary = "";
            }

            ingestJobService.markFinished(job.jobId(), status, processed, created, updated, errorSummary);
            return new IngestJob(job.jobId(), mode, status, job.startedAt(), Instant.now(), null, null, processed, created, updated, errorSummary, null, null, fromPub, toPub);
        } catch (Exception e) {
            log.error("Ingest job failed.", e);
            errorSummary = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage());
            ingestJobService.markFinished(job.jobId(), IngestStatus.failed, processed, created, updated, errorSummary);
            return new IngestJob(job.jobId(), mode, IngestStatus.failed, job.startedAt(), Instant.now(), null, null, processed, created, updated, errorSummary, null, null, fromPublicationDate, toPublicationDate);
        }
    }

    private record UpsertResult(int created, int updated, int failed) {}

    private UpsertResult upsertOpenAlexWork(OpenAlexWork w) {
        if (w == null || w.id() == null || w.id().isBlank()) {
            return new UpsertResult(0, 0, 0);
        }

        String sourceWorkId = w.id();
        String docId = "openalex:" + normalizeOpenAlexId(sourceWorkId);

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("source", "openalex");
        doc.put("source_work_id", sourceWorkId);
        doc.put("title", w.title());
        doc.put("abstract", w.abstractText());
        LocalDate publicationDate = sanitizePublicationDate(w.publicationDate());
        if (publicationDate != null) {
            doc.put("publication_date", publicationDate.toString());
        }
        doc.put("cited_by_count", w.citedByCount());
        doc.put("keywords", normalizeKeywordList(w.keywords()));
        doc.put("keyword_candidates", normalizeKeywordList(w.keywords()));
        doc.put("authors", toAuthors(w.authors()));
        doc.put("institutions", toInstitutions(w.institutions()));

        if (w.doi() != null && !w.doi().isBlank()) {
            doc.put("doi", w.doi().trim());
        }
        if (w.landingPageUrl() != null && !w.landingPageUrl().isBlank()) {
            doc.put("landing_page_url", w.landingPageUrl().trim());
        }
        if (w.pdfUrl() != null && !w.pdfUrl().isBlank()) {
            doc.put("pdf_url", w.pdfUrl().trim());
        }
        if (w.openAccessUrl() != null && !w.openAccessUrl().isBlank()) {
            doc.put("open_access_oa_url", w.openAccessUrl().trim());
        }

        WorkLink best = WorkLinkPolicy.pickBestLink(
                w.doi(),
                w.landingPageUrl(),
                w.pdfUrl(),
                w.openAccessUrl()
        );
        if (best != null) {
            doc.put("best_link_url", best.url());
            doc.put("best_link_type", best.type().name());
        }

        doc.put("updated_at", Instant.now().toString());

        try {
            var res = esClient.index(i -> i.index(WORKS_INDEX).id(docId).document(doc));
            if (res.result() == Result.Created) {
                return new UpsertResult(1, 0, 0);
            }
            return new UpsertResult(0, 1, 0);
        } catch (Exception e) {
            log.warn("Failed to upsert work {}", docId, e);
            return new UpsertResult(0, 0, 1);
        }
    }

    private String normalizeOpenAlexId(String raw) {
        String s = raw.trim();
        if (s.startsWith("https://openalex.org/")) {
            return s.substring("https://openalex.org/".length());
        }
        return s;
    }

    private LocalDate sanitizePublicationDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDate today = LocalDate.now(ZoneId.of(timezone));
        return date.isAfter(today) ? null : date;
    }

    private List<String> normalizeKeywordList(List<String> keywords) {
        if (keywords == null) return List.of();
        return keywords.stream()
                .map(KeywordNormalizeUtil::normalize)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private List<Map<String, Object>> toAuthors(List<OpenAlexWork.AuthorRef> authors) {
        if (authors == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (OpenAlexWork.AuthorRef a : authors) {
            if (a == null || a.name() == null || a.name().isBlank()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.id() == null ? "" : a.id());
            m.put("name", a.name());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> toInstitutions(List<OpenAlexWork.InstitutionRef> institutions) {
        if (institutions == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (OpenAlexWork.InstitutionRef inst : institutions) {
            if (inst == null) continue;
            String id = inst.id() == null ? "" : normalizeOpenAlexId(inst.id().trim());
            String name = inst.name() == null ? "" : inst.name().trim();
            if (id.isBlank() && name.isBlank()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("name", name);
            out.add(m);
        }
        return out;
    }
}
