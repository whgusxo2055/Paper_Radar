package com.paperradar.ingest.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.paperradar.ingest.model.IngestJob;
import com.paperradar.ingest.model.IngestMode;
import com.paperradar.ingest.model.IngestStatus;
import com.paperradar.infra.es.ElasticsearchErrorUtil;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ElasticsearchIngestJobService implements IngestJobService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIngestJobService.class);

    private static final String INDEX = "ingest_jobs";

    private final ElasticsearchClient client;

    @Override
    public IngestJob start(IngestMode mode) {
        String jobId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        IngestJob job = new IngestJob(jobId, mode, IngestStatus.running, now, null, now, null, 0, 0, 0, null, null, null, null, null);

        try {
            client.index(i -> i.index(INDEX).id(jobId).document(toDoc(job)).refresh(Refresh.WaitFor));
        } catch (Exception e) {
            log.error("Failed to persist ingest job start.", e);
        }
        return job;
    }

    @Override
    public void markFinished(
            String jobId,
            IngestStatus status,
            int processed,
            int created,
            int updated,
            String errorSummary
    ) {
        Instant now = Instant.now();
        try {
            Map<String, Object> doc = Map.of(
                    "job_id", jobId,
                    "status", status.name(),
                    "ended_at", now.toString(),
                    "last_heartbeat_at", now.toString(),
                    "last_progress_at", now.toString(),
                    "processed_count", processed,
                    "created_count", created,
                    "updated_count", updated,
                    "error_summary", errorSummary == null ? "" : errorSummary
            );
            client.update(u -> u.index(INDEX).id(jobId).doc(doc).refresh(Refresh.WaitFor), Map.class);
        } catch (Exception e) {
            log.error("Failed to update ingest job {}", jobId, e);
        }
    }

    @Override
    public Optional<IngestJob> lastSuccessful(IngestMode mode) {
        try {
            var response = client.search(s -> s
                            .index(INDEX)
                            .size(1)
                            .sort(so -> so.field(f -> f.field("ended_at").order(SortOrder.Desc)))
                            .query(q -> q.bool(b -> b
                                    .filter(q2 -> q2.term(t -> t.field("mode").value(mode.name())))
                                    .filter(q2 -> q2.term(t -> t.field("status").value(IngestStatus.success.name())))
                            )),
                    Map.class
            );
            if (response.hits().hits().isEmpty() || response.hits().hits().getFirst().source() == null) {
                return Optional.empty();
            }
            return Optional.of(fromSource((Map<?, ?>) response.hits().hits().getFirst().source()));
        } catch (ElasticsearchException e) {
            if (ElasticsearchErrorUtil.isIndexNotFound(e)) {
                log.info("Index {} not found. Returning empty lastSuccessful ingest job.", INDEX);
                return Optional.empty();
            }
            log.warn("Failed to query last successful ingest job.", e);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to query last successful ingest job.", e);
            return Optional.empty();
        }
    }

    @Override
    public List<IngestJob> recentJobs(int size) {
        try {
            var response = client.search(s -> s
                            .index(INDEX)
                            .size(Math.min(size, 50))
                            .sort(so -> so.field(f -> f.field("started_at").order(SortOrder.Desc))),
                    Map.class
            );
            return response.hits().hits().stream()
                    .map(h -> h.source() == null ? null : fromSource((Map<?, ?>) h.source()))
                    .filter(j -> j != null)
                    .toList();
        } catch (ElasticsearchException e) {
            if (ElasticsearchErrorUtil.isIndexNotFound(e)) {
                log.info("Index {} not found. Returning empty ingest jobs.", INDEX);
                return List.of();
            }
            log.warn("Failed to query recent ingest jobs.", e);
            return List.of();
        } catch (Exception e) {
            log.warn("Failed to query recent ingest jobs.", e);
            return List.of();
        }
    }

    @Override
    public int cleanupStaleRunningJobs(Duration maxAge) {
        if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
            return 0;
        }
        Instant cutoff = Instant.now().minus(maxAge);
        try {
            var response = client.search(s -> s
                            .index(INDEX)
                            .size(50)
                            .sort(so -> so.field(f -> f.field("started_at").order(SortOrder.Asc)))
                            .query(q -> q.bool(b -> b
                                    .filter(q1 -> q1.term(t -> t.field("status").value(IngestStatus.running.name())))
                                    .filter(q2 -> q2.range(r -> r.date(dr -> dr.field("started_at").lte(cutoff.toString()))))
                            )),
                    Map.class
            );

            int cleaned = 0;
            for (var hit : response.hits().hits()) {
                String jobId = hit.id();
                Map<?, ?> src = hit.source();
                int processed = src == null ? 0 : asInt(src.get("processed_count"));
                int created = src == null ? 0 : asInt(src.get("created_count"));
                int updated = src == null ? 0 : asInt(src.get("updated_count"));

                String err = "Stale running job was cleaned up (likely container restart).";
                markFinished(jobId, IngestStatus.failed, processed, created, updated, err);
                cleaned++;
            }
            return cleaned;
        } catch (ElasticsearchException e) {
            if (ElasticsearchErrorUtil.isIndexNotFound(e)) {
                return 0;
            }
            log.warn("Failed to cleanup stale running ingest jobs.", e);
            return 0;
        } catch (Exception e) {
            log.warn("Failed to cleanup stale running ingest jobs.", e);
            return 0;
        }
    }

    @Override
    public void updateMeta(String jobId, Map<String, Object> fields) {
        if (jobId == null || jobId.isBlank() || fields == null || fields.isEmpty()) {
            return;
        }
        try {
            client.update(u -> u.index(INDEX).id(jobId).doc(fields).refresh(Refresh.WaitFor), Map.class);
        } catch (Exception e) {
            log.warn("Failed to update ingest job meta: {}", jobId, e);
        }
    }

    private Map<String, Object> toDoc(IngestJob job) {
        Map<String, Object> doc = new java.util.LinkedHashMap<>();
        doc.put("job_id", job.jobId());
        doc.put("mode", job.mode().name());
        doc.put("status", job.status().name());
        doc.put("started_at", job.startedAt().toString());
        doc.put("processed_count", job.processedCount());
        doc.put("created_count", job.createdCount());
        doc.put("updated_count", job.updatedCount());
        doc.put("error_summary", job.errorSummary() == null ? "" : job.errorSummary());
        doc.put("current_source", job.currentSource() == null ? "" : job.currentSource());
        doc.put("current_key", job.currentKey() == null ? "" : job.currentKey());
        if (job.lastHeartbeatAt() != null) {
            doc.put("last_heartbeat_at", job.lastHeartbeatAt().toString());
        }
        if (job.lastProgressAt() != null) {
            doc.put("last_progress_at", job.lastProgressAt().toString());
        }
        if (job.fromPublicationDate() != null) {
            doc.put("from_publication_date", job.fromPublicationDate().toString());
        }
        if (job.toPublicationDate() != null) {
            doc.put("to_publication_date", job.toPublicationDate().toString());
        }
        return doc;
    }

    private IngestJob fromSource(Map<?, ?> src) {
        String jobId = asString(src.get("job_id"));
        IngestMode mode = parseMode(asString(src.get("mode")));
        IngestStatus status = parseStatus(asString(src.get("status")));
        Instant startedAt = parseInstant(src.get("started_at"));
        Instant endedAt = parseInstantOrNull(src.get("ended_at"));
        Instant lastHeartbeatAt = parseInstantOrNull(src.get("last_heartbeat_at"));
        Instant lastProgressAt = parseInstantOrNull(src.get("last_progress_at"));
        int processed = asInt(src.get("processed_count"));
        int created = asInt(src.get("created_count"));
        int updated = asInt(src.get("updated_count"));
        String err = asString(src.get("error_summary"));
        String currentSource = asString(src.get("current_source"));
        String currentKey = asString(src.get("current_key"));
        LocalDate fromPub = parseLocalDateOrNull(src.get("from_publication_date"));
        LocalDate toPub = parseLocalDateOrNull(src.get("to_publication_date"));
        return new IngestJob(jobId, mode, status, startedAt, endedAt, lastHeartbeatAt, lastProgressAt, processed, created, updated, err, currentSource, currentKey, fromPub, toPub);
    }

    private String asString(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private int asInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    private Instant parseInstant(Object v) {
        try {
            return Instant.parse(String.valueOf(v));
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    private Instant parseInstantOrNull(Object v) {
        if (v == null) return null;
        try {
            String s = String.valueOf(v);
            if (s.isBlank()) return null;
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseLocalDateOrNull(Object v) {
        if (v == null) return null;
        try {
            String s = String.valueOf(v);
            if (s.isBlank()) return null;
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private IngestMode parseMode(String s) {
        try {
            return IngestMode.valueOf(s);
        } catch (Exception e) {
            return IngestMode.incremental;
        }
    }

    private IngestStatus parseStatus(String s) {
        try {
            return IngestStatus.valueOf(s);
        } catch (Exception e) {
            return IngestStatus.failed;
        }
    }
}
