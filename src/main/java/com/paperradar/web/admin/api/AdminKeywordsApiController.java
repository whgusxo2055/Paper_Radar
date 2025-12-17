package com.paperradar.web.admin.api;

import com.paperradar.admin.model.ActiveConfig;
import com.paperradar.admin.service.ConfigService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminKeywordsApiController {

    private final ConfigService configService;

    @PostMapping("/api/admin/keywords/enable")
    public ActiveConfig enable(@RequestBody KeywordToggleRequest req) {
        return configService.enableKeyword(req.keyword());
    }

    @PostMapping("/api/admin/keywords/disable")
    public ActiveConfig disable(@RequestBody KeywordToggleRequest req) {
        return configService.disableKeyword(req.keyword());
    }

    public record KeywordToggleRequest(@NotBlank String keyword) {}
}

