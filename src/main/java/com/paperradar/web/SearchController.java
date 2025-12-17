package com.paperradar.web;

import com.paperradar.search.dto.SearchRequest;
import com.paperradar.search.model.SearchResultPage;
import com.paperradar.search.service.SearchService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/search")
    public String search(
            @Valid @ModelAttribute("req") SearchRequest req,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(
                    "errors",
                    bindingResult.getAllErrors().stream().map(e -> e.getDefaultMessage()).toList()
            );
            model.addAttribute("result", new SearchResultPage(List.of(), 0, req.page(), req.size()));
            return "search";
        }

        if (req.isEmptyQuery()) {
            model.addAttribute("result", new SearchResultPage(List.of(), 0, req.page(), req.size()));
            return "search";
        }

        model.addAttribute("result", searchService.search(req));
        return "search";
    }
}
