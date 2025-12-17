package com.paperradar.institution.web;

import com.paperradar.institution.model.InstitutionAnalysis;
import com.paperradar.institution.service.InstitutionAnalysisService;
import com.paperradar.institution.util.InstitutionIdCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class InstitutionController {

    private final InstitutionAnalysisService institutionAnalysisService;

    @GetMapping("/institution/{encodedInstitutionId}")
    public String institution(
            @PathVariable String encodedInstitutionId,
            @RequestParam(name = "kwWindow", required = false, defaultValue = "90") int kwWindow,
            Model model
    ) {
        String institutionId = InstitutionIdCodec.decode(encodedInstitutionId);
        if (institutionId.isBlank()) {
            model.addAttribute("error", "유효하지 않은 기관 ID 입니다. (encodedInstitutionId decode 실패)");
            return "institution";
        }

        int window = (kwWindow == 30) ? 30 : 90;
        InstitutionAnalysis analysis = institutionAnalysisService.analyze(institutionId, 20, 20, window);
        model.addAttribute("analysis", analysis);
        model.addAttribute("kwWindow", window);
        model.addAttribute("encodedInstitutionId", encodedInstitutionId);
        return "institution";
    }
}
