package com.paperradar.web.admin.api;

import com.paperradar.admin.model.ActiveConfig;
import com.paperradar.admin.model.InstitutionSummary;
import com.paperradar.admin.service.ConfigService;
import com.paperradar.admin.service.InstitutionService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminInstitutionsApiController {

    private final ConfigService configService;
    private final InstitutionService institutionService;

    @PostMapping("/api/admin/institutions/add")
    public InstitutionSummary add(@RequestBody AddInstitutionRequest req) {
        InstitutionSummary saved = institutionService.upsertInstitution(req.instId(), req.displayName(), req.alias());
        configService.enableInstitution(saved.id());
        institutionService.setActive(saved.id(), true);
        return saved;
    }

    @PostMapping("/api/admin/institutions/enable")
    public ActiveConfig enable(@RequestBody InstitutionToggleRequest req) {
        institutionService.setActive(req.instId(), true);
        return configService.enableInstitution(req.instId());
    }

    @PostMapping("/api/admin/institutions/disable")
    public ActiveConfig disable(@RequestBody InstitutionToggleRequest req) {
        institutionService.setActive(req.instId(), false);
        return configService.disableInstitution(req.instId());
    }

    public record AddInstitutionRequest(@NotBlank String instId, String displayName, String alias) {}

    public record InstitutionToggleRequest(@NotBlank String instId) {}
}

