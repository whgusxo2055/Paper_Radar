package com.paperradar.web.admin;

import com.paperradar.admin.model.ActiveConfig;
import com.paperradar.admin.service.KeywordAdminService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AdminKeywordsPageController {

    private final KeywordAdminService keywordAdminService;

    @GetMapping("/admin/keywords")
    public String keywords(Model model) {
        ActiveConfig cfg = keywordAdminService.config();
        List<String> candidates = keywordAdminService.suggestCandidates(30, 100);

        model.addAttribute("suggestedKeywords", candidates);
        model.addAttribute("enabledKeywords", cfg.enabledKeywords());
        model.addAttribute("disabledKeywords", cfg.disabledKeywords());
        return "admin/keywords";
    }
}

