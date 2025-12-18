package com.paperradar.ingest.service;

import com.paperradar.ingest.model.IngestJob;
import com.paperradar.ingest.model.IngestMode;
import com.paperradar.ingest.model.IngestStatus;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface IngestJobService {
    IngestJob start(IngestMode mode);

    void markFinished(
            String jobId,
            IngestStatus status,
            int processed,
            int created,
            int updated,
            String errorSummary
    );

    Optional<IngestJob> lastSuccessful(IngestMode mode);

    List<IngestJob> recentJobs(int size);

    /**
     * 오래된 running job을 failed로 정리합니다. (컨테이너 재시작/강제 종료 등으로 ended_at이 남지 않는 경우)
     *
     * @return 정리된 job 개수
     */
    int cleanupStaleRunningJobs(Duration maxAge);

    /**
     * 실행 파라미터 등 부가 정보를 job 문서에 기록합니다. (구현체가 ES일 때만 동작, 그 외에는 no-op)
     */
    default void updateMeta(String jobId, Map<String, Object> fields) {}
}
