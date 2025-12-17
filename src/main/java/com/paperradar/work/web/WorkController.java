package com.paperradar.work.web;

import com.paperradar.work.model.WorkDetail;
import com.paperradar.work.service.WorkService;
import com.paperradar.work.util.WorkIdCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class WorkController {

    private final WorkService workService;

    @GetMapping("/work/{encodedWorkId}")
    public String work(@PathVariable String encodedWorkId, Model model) {
        String id = WorkIdCodec.decode(encodedWorkId);
        if (id.isBlank()) {
            model.addAttribute("error", "유효하지 않은 논문 ID 입니다. (encodedWorkId decode 실패)");
            return "work";
        }

        WorkDetail detail = workService.getById(id).orElse(null);
        if (detail == null) {
            model.addAttribute("error", "논문을 찾을 수 없습니다.");
            return "work";
        }

        model.addAttribute("work", detail);
        model.addAttribute("encodedWorkId", encodedWorkId);
        return "work";
    }
}

