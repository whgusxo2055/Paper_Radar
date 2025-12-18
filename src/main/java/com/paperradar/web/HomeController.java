package com.paperradar.web;

import com.paperradar.trend.service.TrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final TrendService trendService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("keywordTrends", trendService.keywordTrends(10));
        model.addAttribute("institutionTrends", trendService.institutionTrends(10));
        return "index";
    }
}
