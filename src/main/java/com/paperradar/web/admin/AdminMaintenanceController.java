package com.paperradar.web.admin;

import com.paperradar.admin.maintenance.service.MaintenanceRunRegistry;
import com.paperradar.admin.maintenance.service.MaintenanceJobService;
import com.paperradar.admin.maintenance.service.InstitutionIdBackfillRunRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class AdminMaintenanceController {

    private final MaintenanceRunRegistry registry;
    private final InstitutionIdBackfillRunRegistry institutionIdRegistry;
    private final MaintenanceJobService maintenanceJobService;

    @GetMapping("/admin/maintenance")
    public String maintenance(Model model) {
        model.addAttribute("running", registry.isRunning());
        model.addAttribute("lastResult", registry.lastResult());
        model.addAttribute("lastRunAt", registry.lastRunAt());
        model.addAttribute("instIdRunning", institutionIdRegistry.isRunning());
        model.addAttribute("instIdLastResult", institutionIdRegistry.lastResult());
        model.addAttribute("instIdLastRunAt", institutionIdRegistry.lastRunAt());
        model.addAttribute("jobs", maintenanceJobService.recentJobs(20));
        return "admin/maintenance";
    }
}
