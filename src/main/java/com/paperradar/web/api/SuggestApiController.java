package com.paperradar.web.api;

import com.paperradar.suggest.model.SuggestItem;
import com.paperradar.suggest.service.SuggestService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SuggestApiController {

    private final SuggestService suggestService;

    @GetMapping("/api/suggest/keyword")
    public List<SuggestItem> keyword(
            @RequestParam(name = "prefix", required = false) String prefix,
            @RequestParam(name = "size", required = false, defaultValue = "10") int size
    ) {
        if (prefix == null || prefix.length() < 2) {
            return List.of();
        }
        return suggestService.suggestKeywords(prefix, Math.min(size, 20));
    }

    @GetMapping("/api/suggest/institution")
    public List<SuggestItem> institution(
            @RequestParam(name = "prefix", required = false) String prefix,
            @RequestParam(name = "size", required = false, defaultValue = "10") int size
    ) {
        if (prefix == null || prefix.length() < 2) {
            return List.of();
        }
        return suggestService.suggestInstitutions(prefix, Math.min(size, 20));
    }

    @GetMapping("/api/suggest/author")
    public List<SuggestItem> author(
            @RequestParam(name = "prefix", required = false) String prefix,
            @RequestParam(name = "size", required = false, defaultValue = "10") int size
    ) {
        if (prefix == null || prefix.length() < 2) {
            return List.of();
        }
        return suggestService.suggestAuthors(prefix, Math.min(size, 20));
    }
}

