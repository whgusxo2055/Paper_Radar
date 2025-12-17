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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Value("${INGEST_LOOKBACK_YEARS:5}")
    private int lookbackYears;

    @Value("${APP_TIMEZONE:Asia/Seoul}")
    private String timezone;

    @Override
    public IngestJob run(IngestMode mode) {
        IngestJob job = ingestJobService.start(mode);

        int processed = 0;
        int created = 0;
        int updated = 0;
        String errorSummary = null;

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

            LocalDate fromPublicationDate = mode == IngestMode.full
                    ? LocalDate.now(ZoneId.of(timezone)).minusYears(Math.max(1, lookbackYears))
                    : null;
            LocalDate fromUpdatedDate = mode == IngestMode.incremental ? incrementalFromUpdatedDate() : null;

            Set<String> seen = new LinkedHashSet<>();
            for (String kw : keywords) {
                for (OpenAlexWork w : openAlexClient.fetchWorksByKeyword(kw, fromPublicationDate, fromUpdatedDate)) {
                    processed++;
                    UpsertResult r = upsertOpenAlexWork(workLinkEnricher.enrich(w));
                    created += r.created;
                    updated += r.updated;
                    seen.add(w.id());
                }
            }

            for (String instId : institutions) {
                for (OpenAlexWork w : openAlexClient.fetchWorksByInstitution(instId, fromPublicationDate, fromUpdatedDate)) {
                    processed++;
                    UpsertResult r = upsertOpenAlexWork(workLinkEnricher.enrich(w));
                    created += r.created;
                    updated += r.updated;
                    seen.add(w.id());
                }
            }

            ingestJobService.markFinished(job.jobId(), IngestStatus.success, processed, created, updated, "");
            return new IngestJob(job.jobId(), mode, IngestStatus.success, job.startedAt(), Instant.now(), processed, created, updated, "");
        } catch (Exception e) {
            log.error("Ingest job failed.", e);
            errorSummary = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage());
            ingestJobService.markFinished(job.jobId(), IngestStatus.failed, processed, created, updated, errorSummary);
            return new IngestJob(job.jobId(), mode, IngestStatus.failed, job.startedAt(), Instant.now(), processed, created, updated, errorSummary);
        }
    }

    private LocalDate incrementalFromUpdatedDate() {
        Optional<IngestJob> last = ingestJobService.lastSuccessful(IngestMode.incremental);
        if (last.isEmpty() || last.get().endedAt() == null) {
            return LocalDate.now(ZoneId.of(timezone)).minusDays(1);
        }
        return LocalDate.ofInstant(last.get().endedAt(), ZoneId.of(timezone));
    }

    private record UpsertResult(int created, int updated) {}

    private UpsertResult upsertOpenAlexWork(OpenAlexWork w) {
        if (w == null || w.id() == null || w.id().isBlank()) {
            return new UpsertResult(0, 0);
        }

        String sourceWorkId = w.id();
        String docId = "openalex:" + normalizeOpenAlexId(sourceWorkId);

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("source", "openalex");
        doc.put("source_work_id", sourceWorkId);
        doc.put("title", w.title());
        doc.put("abstract", w.abstractText());
        if (w.publicationDate() != null) {
            doc.put("publication_date", w.publicationDate().toString());
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
                return new UpsertResult(1, 0);
            }
            return new UpsertResult(0, 1);
        } catch (Exception e) {
            log.warn("Failed to upsert work {}", docId, e);
            return new UpsertResult(0, 0);
        }
    }

    private String normalizeOpenAlexId(String raw) {
        String s = raw.trim();
        if (s.startsWith("https://openalex.org/")) {
            return s.substring("https://openalex.org/".length());
        }
        return s;
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
            String id = inst.id() == null ? "" : inst.id().trim();
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
