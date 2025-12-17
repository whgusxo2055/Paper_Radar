package com.paperradar.admin.maintenance.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import com.paperradar.admin.maintenance.model.MaintenanceJob;
import com.paperradar.admin.maintenance.model.MaintenanceJobStatus;
import com.paperradar.admin.maintenance.model.MaintenanceJobType;
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
public class ElasticsearchMaintenanceJobService implements MaintenanceJobService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchMaintenanceJobService.class);
    private static final String INDEX = "maintenance_jobs";

    private final ElasticsearchClient client;

    @Override
    public MaintenanceJob start(MaintenanceJobType type) {
        String jobId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        MaintenanceJob job = new MaintenanceJob(jobId, type, MaintenanceJobStatus.running, now, null, 0, 0, 0, List.of(), "");
        try {
            client.index(i -> i.index(INDEX).id(jobId).document(toDoc(job)));
        } catch (Exception e) {
            log.warn("Failed to persist maintenance job start.", e);
        }
        return job;
    }

    @Override
    public void markSuccess(String jobId, int scanned, int updated, int failedCount, List<String> failedDocIds) {
        update(jobId, MaintenanceJobStatus.success, scanned, updated, failedCount, failedDocIds, "");
    }

    @Override
    public void markFailed(String jobId, String errorSummary, int failedCount, List<String> failedDocIds) {
        update(jobId, MaintenanceJobStatus.failed, 0, 0, failedCount, failedDocIds, errorSummary == null ? "" : errorSummary);
    }

    @Override
    public List<MaintenanceJob> recentJobs(int size) {
        try {
            var res = client.search(s -> s
                            .index(INDEX)
                            .size(Math.min(Math.max(size, 1), 50))
                            .sort(so -> so.field(f -> f.field("started_at").order(SortOrder.Desc))),
                    Map.class);
            return res.hits().hits().stream()
                    .map(h -> h.source() == null ? null : fromSource((Map<?, ?>) h.source()))
                    .filter(j -> j != null)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to query maintenance jobs.", e);
            return List.of();
        }
    }

    @Override
    public Optional<MaintenanceJob> latestOfType(MaintenanceJobType type) {
        try {
            var res = client.search(s -> s
                            .index(INDEX)
                            .size(1)
                            .sort(so -> so.field(f -> f.field("started_at").order(SortOrder.Desc)))
                            .query(q -> q.term(t -> t.field("type").value(type.name()))),
                    Map.class);
            if (res.hits().hits().isEmpty() || res.hits().hits().getFirst().source() == null) {
                return Optional.empty();
            }
            return Optional.of(fromSource((Map<?, ?>) res.hits().hits().getFirst().source()));
        } catch (Exception e) {
            log.warn("Failed to query latest maintenance job.", e);
            return Optional.empty();
        }
    }

    private void update(
            String jobId,
            MaintenanceJobStatus status,
            int scanned,
            int updated,
            int failedCount,
            List<String> failedDocIds,
            String errorSummary
    ) {
        try {
            Map<String, Object> doc = Map.of(
                    "status", status.name(),
                    "ended_at", Instant.now().toString(),
                    "scanned_count", scanned,
                    "updated_count", updated,
                    "failed_count", failedCount,
                    "failed_doc_ids", failedDocIds == null ? List.of() : failedDocIds,
                    "error_summary", errorSummary
            );
            client.update(u -> u.index(INDEX).id(jobId).doc(doc), Map.class);
        } catch (Exception e) {
            log.warn("Failed to update maintenance job {}", jobId, e);
        }
    }

    private Map<String, Object> toDoc(MaintenanceJob job) {
        return Map.of(
                "job_id", job.jobId(),
                "type", job.type().name(),
                "status", job.status().name(),
                "started_at", job.startedAt().toString(),
                "scanned_count", job.scannedCount(),
                "updated_count", job.updatedCount(),
                "failed_count", job.failedCount(),
                "failed_doc_ids", job.failedDocIds() == null ? List.of() : job.failedDocIds(),
                "error_summary", job.errorSummary() == null ? "" : job.errorSummary()
        );
    }

    private MaintenanceJob fromSource(Map<?, ?> src) {
        String jobId = asString(src.get("job_id"));
        MaintenanceJobType type = parseType(asString(src.get("type")));
        MaintenanceJobStatus status = parseStatus(asString(src.get("status")));
        Instant startedAt = parseInstant(src.get("started_at"));
        Instant endedAt = parseInstantOrNull(src.get("ended_at"));
        int scanned = asInt(src.get("scanned_count"));
        int updated = asInt(src.get("updated_count"));
        int failed = asInt(src.get("failed_count"));
        List<String> failedIds = asStringList(src.get("failed_doc_ids"));
        String err = asString(src.get("error_summary"));
        return new MaintenanceJob(jobId, type, status, startedAt, endedAt, scanned, updated, failed, failedIds, err);
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

    private List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(o -> o == null ? "" : String.valueOf(o)).filter(s -> !s.isBlank()).toList();
        }
        return List.of();
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

    private MaintenanceJobType parseType(String s) {
        try {
            return MaintenanceJobType.valueOf(s);
        } catch (Exception e) {
            return MaintenanceJobType.recompute_work_links;
        }
    }

    private MaintenanceJobStatus parseStatus(String s) {
        try {
            return MaintenanceJobStatus.valueOf(s);
        } catch (Exception e) {
            return MaintenanceJobStatus.failed;
        }
    }
}
