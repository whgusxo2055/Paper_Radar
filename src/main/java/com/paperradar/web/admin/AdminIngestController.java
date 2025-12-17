package com.paperradar.web.admin;

import com.paperradar.ingest.service.IngestJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AdminIngestController {

    private final IngestJobService ingestJobService;

    @GetMapping("/admin/ingest")
    public String ingest(Model model) {
        model.addAttribute("jobs", ingestJobService.recentJobs(20));
        return "admin/ingest";
    }
}

