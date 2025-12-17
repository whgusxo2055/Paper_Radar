package com.paperradar.ingest.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.paperradar.ingest.model.IngestJob;
import com.paperradar.ingest.model.IngestMode;
import com.paperradar.ingest.model.IngestStatus;
import java.time.Instant;
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
        IngestJob job = new IngestJob(jobId, mode, IngestStatus.running, now, null, 0, 0, 0, null);

        try {
            client.index(i -> i.index(INDEX).id(jobId).document(toDoc(job)));
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
                    "processed_count", processed,
                    "created_count", created,
                    "updated_count", updated,
                    "error_summary", errorSummary == null ? "" : errorSummary
            );
            client.update(u -> u.index(INDEX).id(jobId).doc(doc), Map.class);
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
        } catch (Exception e) {
            log.warn("Failed to query recent ingest jobs.", e);
            return List.of();
        }
    }

    private Map<String, Object> toDoc(IngestJob job) {
        return Map.of(
                "job_id", job.jobId(),
                "mode", job.mode().name(),
                "status", job.status().name(),
                "started_at", job.startedAt().toString(),
                "processed_count", job.processedCount(),
                "created_count", job.createdCount(),
                "updated_count", job.updatedCount(),
                "error_summary", job.errorSummary() == null ? "" : job.errorSummary()
        );
    }

    private IngestJob fromSource(Map<?, ?> src) {
        String jobId = asString(src.get("job_id"));
        IngestMode mode = parseMode(asString(src.get("mode")));
        IngestStatus status = parseStatus(asString(src.get("status")));
        Instant startedAt = parseInstant(src.get("started_at"));
        Instant endedAt = parseInstantOrNull(src.get("ended_at"));
        int processed = asInt(src.get("processed_count"));
        int created = asInt(src.get("created_count"));
        int updated = asInt(src.get("updated_count"));
        String err = asString(src.get("error_summary"));
        return new IngestJob(jobId, mode, status, startedAt, endedAt, processed, created, updated, err);
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

