package com.paperradar.web.admin;

import com.paperradar.admin.model.ActiveConfig;
import com.paperradar.admin.service.ConfigService;
import com.paperradar.ingest.model.IngestJob;
import com.paperradar.ingest.model.IngestStatus;
import com.paperradar.ingest.service.IngestJobService;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequiredArgsConstructor
public class AdminIngestController {

    private final IngestJobService ingestJobService;
    private final ConfigService configService;

    @Value("${INGEST_LOOKBACK_YEARS:3}")
    private int lookbackYears;

    @Value("${APP_TIMEZONE:Asia/Seoul}")
    private String timezone;

    @Value("${INGEST_STALE_JOB_THRESHOLD_MINUTES:30}")
    private int staleThresholdMinutes;

    @GetMapping("/admin/ingest")
    public String ingest(Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        ActiveConfig cfg = configService.getActiveConfig();
        int enabledKeywordCount = cfg.enabledKeywords() == null ? 0 : cfg.enabledKeywords().size();
        int enabledInstitutionCount = cfg.enabledInstitutions() == null ? 0 : cfg.enabledInstitutions().size();

        model.addAttribute("enabledKeywordCount", enabledKeywordCount);
        model.addAttribute("enabledInstitutionCount", enabledInstitutionCount);
        model.addAttribute("hasIngestSources", enabledKeywordCount > 0 || enabledInstitutionCount > 0);
        model.addAttribute("lookbackYears", lookbackYears);

        ZoneId zoneId = ZoneId.of(timezone);
        LocalDate defaultTo = LocalDate.now(zoneId);
        LocalDate defaultFrom = defaultTo.minusYears(Math.max(1, lookbackYears));
        model.addAttribute("defaultFullFrom", defaultFrom);
        model.addAttribute("defaultFullTo", defaultTo);

        List<IngestJob> jobs = ingestJobService.recentJobs(20);
        long runningCount = jobs.stream()
                .filter(j -> j != null && j.status() == IngestStatus.running)
                .count();
        int threshold = Math.max(1, staleThresholdMinutes);
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(threshold));
        long staleRunningCount = jobs.stream()
                .filter(j -> j != null
                        && j.status() == IngestStatus.running
                        && j.startedAt() != null
                        && j.startedAt().isBefore(cutoff))
                .count();
        model.addAttribute("jobs", jobs);
        model.addAttribute("runningCount", runningCount);
        model.addAttribute("staleRunningCount", staleRunningCount);
        model.addAttribute("staleThresholdMinutes", threshold);
        return "admin/ingest";
    }
}
