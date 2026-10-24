package com.talentallocation.controller;

import com.talentallocation.dto.*;
import com.talentallocation.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReportController {

    private final ReportService reportService;
    private final DemandService demandService;
    private final SkillController.SkillService skillService;

    /** GET /api/reports/utilization — Utilization report */
    @GetMapping("/utilization")
    public ResponseEntity<ReportDto.UtilizationReport> getUtilizationReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate from = startDate != null ? startDate : LocalDate.now();
        LocalDate to = endDate != null ? endDate : LocalDate.now().plusMonths(3);
        return ResponseEntity.ok(reportService.getUtilizationReport(from, to));
    }

    /** GET /api/reports/skill-gaps — Skill gap report */
    @GetMapping("/skill-gaps")
    public ResponseEntity<ReportDto.SkillGapReport> getSkillGapReport() {
        return ResponseEntity.ok(reportService.getSkillGapReport());
    }

    /** GET /api/reports/open-demands — Open demands with no allocation */
    @GetMapping("/open-demands")
    public ResponseEntity<?> getOpenDemandsReport() {
        return ResponseEntity.ok(demandService.getDemandsWithNoAllocation());
    }

    /** GET /api/reports/skills-matrix — Resources grouped by skill and proficiency (SQL Task 2) */
    @GetMapping({"/skills-matrix", "/skill-matrix"})
    public ResponseEntity<ReportDto.SkillMatrixReport> getSkillsMatrixReport() {
        return ResponseEntity.ok(reportService.getSkillMatrixGroupedProficiency());
    }
}
