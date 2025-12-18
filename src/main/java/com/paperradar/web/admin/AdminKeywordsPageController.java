package com.paperradar.web.admin;

import com.paperradar.admin.model.ActiveConfig;
import com.paperradar.admin.service.KeywordAdminService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AdminKeywordsPageController {

    private final KeywordAdminService keywordAdminService;

    @GetMapping("/admin/keywords")
    public String keywords(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "1") int disabledPage,
            Model model
    ) {
        ActiveConfig cfg = keywordAdminService.config();
        List<String> candidates = keywordAdminService.suggestCandidates(30, 100);

        List<String> enabled = cfg.enabledKeywords();
        List<String> disabled = cfg.disabledKeywords();

        // Filter
        if (!q.isBlank()) {
            String query = q.toLowerCase();
            enabled = enabled.stream().filter(k -> k.contains(query)).toList();
            disabled = disabled.stream().filter(k -> k.contains(query)).toList();
        }

        int pageSize = 10;

        // Paging (Enabled)
        int totalEnabled = enabled.size();
        int totalPages = (int) Math.ceil((double) totalEnabled / pageSize);
        int currentPage = Math.max(1, Math.min(page, totalPages == 0 ? 1 : totalPages));
        int start = (currentPage - 1) * pageSize;
        int end = Math.min(start + pageSize, totalEnabled);

        List<String> pagedEnabled = (start < totalEnabled) ? enabled.subList(start, end) : List.of();

        // Paging (Disabled)
        int totalDisabled = disabled.size();
        int totalDisabledPages = (int) Math.ceil((double) totalDisabled / pageSize);
        int currentDisabledPage = Math.max(1, Math.min(disabledPage, totalDisabledPages == 0 ? 1 : totalDisabledPages));
        int disabledStart = (currentDisabledPage - 1) * pageSize;
        int disabledEnd = Math.min(disabledStart + pageSize, totalDisabled);

        List<String> pagedDisabled = (disabledStart < totalDisabled) ? disabled.subList(disabledStart, disabledEnd) : List.of();

        model.addAttribute("suggestedKeywords", candidates);
        model.addAttribute("enabledKeywords", pagedEnabled);
        model.addAttribute("disabledKeywords", pagedDisabled);

        model.addAttribute("q", q);

        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalEnabled", totalEnabled);

        model.addAttribute("currentDisabledPage", currentDisabledPage);
        model.addAttribute("totalDisabledPages", totalDisabledPages);
        model.addAttribute("totalDisabled", totalDisabled);

        return "admin/keywords";
    }
}
