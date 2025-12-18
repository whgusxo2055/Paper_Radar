package com.paperradar.web.view;

import com.paperradar.ingest.model.IngestJob;
import com.paperradar.ingest.model.IngestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("viewIngestJobFormat")
@RequiredArgsConstructor
public class ViewIngestJobFormat {

    private final ViewTimeFormat viewTimeFormat;

    public String progress(IngestJob job) {
        if (job == null || job.status() != IngestStatus.running) {
            return "";
        }

        String time = viewTimeFormat.format(job.lastProgressAt() != null ? job.lastProgressAt() : job.lastHeartbeatAt());
        String source = normalizeSource(job.currentSource());
        String key = normalizeValue(job.currentKey());

        if (time.isBlank() && "-".equals(source) && "-".equals(key)) {
            return "진행 중";
        }

        String timePart = time.isBlank() ? "" : "마지막: " + time;
        String detailPart = source + ":" + key;

        if (timePart.isBlank()) {
            return detailPart;
        }
        return timePart + " · " + detailPart;
    }

    private static String normalizeValue(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value;
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "-";
        }
        return switch (source) {
            case "keyword" -> "키워드";
            case "institution" -> "기관";
            default -> source;
        };
    }
}

