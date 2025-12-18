package com.paperradar.admin.maintenance.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.paperradar.admin.maintenance.model.WorkInstitutionIdBackfillResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ElasticsearchWorkInstitutionIdBackfillService implements WorkInstitutionIdBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchWorkInstitutionIdBackfillService.class);

    private final ElasticsearchClient client;

    @Override
    public WorkInstitutionIdBackfillResult normalizeInstitutionIds(int batchSize, int maxDocs) {
        int size = Math.min(Math.max(batchSize, 1), 200);
        int limit = Math.min(Math.max(maxDocs, 1), 20_000);

        int scanned = 0;
        int updatedDocs = 0;
        int updatedInstitutionIds = 0;
        int failed = 0;
        Set<String> failedDocIds = new LinkedHashSet<>();

        String searchAfter = null;

        while (scanned < limit) {
            int remaining = limit - scanned;
            int pageSize = Math.min(size, remaining);

            var response = safeSearch(searchAfter, pageSize);
            if (response == null || response.hits() == null || response.hits().hits().isEmpty()) {
                break;
            }

            List<BulkUpdate> updates = new ArrayList<>();
            for (var hit : response.hits().hits()) {
                scanned++;
                searchAfter = hit.id();

                Map<?, ?> src = (Map<?, ?>) hit.source();
                if (src == null) {
                    continue;
                }

                Object institutionsValue = src.get("institutions");
                if (!(institutionsValue instanceof List<?> instList)) {
                    continue;
                }

                boolean changed = false;
                List<Map<String, Object>> normalizedInstitutions = new ArrayList<>();
                for (Object entry : instList) {
                    if (!(entry instanceof Map<?, ?> m)) {
                        continue;
                    }
                    String id = asString(m.get("id"));
                    String name = asString(m.get("name"));
                    String normalizedId = normalizeInstitutionId(id);
                    if (!normalizedId.equals(id)) {
                        changed = true;
                        updatedInstitutionIds++;
                    }
                    normalizedInstitutions.add(Map.of(
                            "id", normalizedId,
                            "name", name
                    ));
                }

                if (!changed) {
                    continue;
                }

                updates.add(new BulkUpdate(hit.id(), Map.of(
                        "institutions", normalizedInstitutions
                )));
            }

            if (!updates.isEmpty()) {
                BulkOutcome outcome = executeBulk(updates);
                updatedDocs += outcome.updated;
                failed += outcome.failed;
                for (String id : outcome.failedDocIds) {
                    if (failedDocIds.size() >= 100) break;
                    failedDocIds.add(id);
                }
            }
        }

        return new WorkInstitutionIdBackfillResult(scanned, updatedDocs, updatedInstitutionIds, failed, List.copyOf(failedDocIds));
    }

    private co.elastic.clients.elasticsearch.core.SearchResponse<Map> safeSearch(String searchAfterId, int size) {
        try {
            return client.search(s -> {
                        s.index("works");
                        s.size(size);
                        s.sort(so -> so.field(f -> f.field("_id").order(SortOrder.Asc)));
                        s.source(src -> src.filter(f -> f.includes("institutions")));
                        if (searchAfterId != null && !searchAfterId.isBlank()) {
                            s.searchAfter(searchAfterId);
                        }
                        return s;
                    },
                    Map.class);
        } catch (Exception e) {
            log.warn("Institution id backfill search failed.", e);
            return null;
        }
    }

    private BulkOutcome executeBulk(List<BulkUpdate> updates) {
        try {
            BulkResponse res = client.bulk(b -> {
                for (BulkUpdate u : updates) {
                    b.operations(op -> op.update(up -> up
                            .index("works")
                            .id(u.id)
                            .action(a -> a.doc(u.doc))
                    ));
                }
                return b;
            });
            if (res.errors()) {
                log.warn("Institution id backfill bulk had errors.");
            }
            int ok = 0;
            int failed = 0;
            List<String> failedIds = new ArrayList<>();
            for (var item : res.items()) {
                if (item.error() == null && item.status() >= 200 && item.status() < 300) {
                    ok++;
                    continue;
                }
                failed++;
                if (failedIds.size() < 100 && item.id() != null && !item.id().isBlank()) {
                    failedIds.add(item.id());
                }
                if (item.error() != null) {
                    log.warn("Institution id backfill item failed (id={}, status={}, errorType={}, reason={})",
                            item.id(),
                            item.status(),
                            item.error().type(),
                            item.error().reason()
                    );
                } else {
                    log.warn("Institution id backfill item failed (id={}, status={})", item.id(), item.status());
                }
            }
            return new BulkOutcome(ok, failed, failedIds);
        } catch (Exception e) {
            log.warn("Institution id backfill bulk failed.", e);
            return new BulkOutcome(0, updates.size(), updates.stream().limit(100).map(u -> u.id).toList());
        }
    }

    private record BulkOutcome(int updated, int failed, List<String> failedDocIds) {}

    private record BulkUpdate(String id, Map<String, Object> doc) {}

    private String normalizeInstitutionId(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        if (trimmed.startsWith("https://openalex.org/")) {
            return trimmed.substring("https://openalex.org/".length());
        }
        if (trimmed.startsWith("http://openalex.org/")) {
            return trimmed.substring("http://openalex.org/".length());
        }
        return trimmed;
    }

    private String asString(Object value) {
        if (value == null) return "";
        return String.valueOf(value);
    }
}

