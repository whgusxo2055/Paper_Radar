package com.paperradar.web.admin;

import com.paperradar.admin.model.ActiveConfig;
import com.paperradar.admin.service.ConfigService;
import com.paperradar.admin.service.InstitutionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AdminInstitutionsController {

    private final ConfigService configService;
    private final InstitutionService institutionService;

    @GetMapping("/admin/institutions")
    public String institutions(Model model) {
        ActiveConfig cfg = configService.getActiveConfig();
        List<String> enabled = cfg.enabledInstitutions();
        List<String> disabled = cfg.disabledInstitutions();

        model.addAttribute("enabledInstitutions", institutionService.getByIds(enabled));
        model.addAttribute("disabledInstitutions", institutionService.getByIds(disabled));
        return "admin/institutions";
    }
}

