package com.talentallocation.controller;

import com.talentallocation.domain.ProjectDemand;
import com.talentallocation.dto.*;
import com.talentallocation.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/demands")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DemandController {

    private final DemandService demandService;
    private final RecommendationService recommendationService;

    /** POST /api/demands — Create demand */
    @PostMapping
    public ResponseEntity<DemandDto.Response> createDemand(
            @Valid @RequestBody DemandDto.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(demandService.createDemand(request));
    }

    /** GET /api/demands — List with filters */
    @GetMapping
    public ResponseEntity<List<DemandDto.Response>> getDemands(
            @RequestParam(required = false) ProjectDemand.DemandStatus status,
            @RequestParam(required = false) Long skillId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(demandService.getDemands(status, skillId, startDate, endDate));
    }

    /** GET /api/demands/{id} — Get demand by ID */
    @GetMapping("/{id}")
    public ResponseEntity<DemandDto.Response> getDemand(@PathVariable Long id) {
        return ResponseEntity.ok(demandService.getDemand(id));
    }

    /** GET /api/demands/open-by-priority — Open demands ordered by priority */
    @GetMapping("/open-by-priority")
    public ResponseEntity<List<DemandDto.Response>> getOpenDemandsOrderedByPriority() {
        return ResponseEntity.ok(demandService.getOpenDemandsOrderedByPriority());
    }

    /** GET /api/demands/unallocated — Demands with no eligible candidate */
    @GetMapping("/unallocated")
    public ResponseEntity<List<DemandDto.Response>> getUnallocatedDemands() {
        return ResponseEntity.ok(demandService.getDemandsWithNoAllocation());
    }

    /** POST /api/demands/{id}/recommendations — Get candidate recommendations */
    @PostMapping("/{id}/recommendations")
    public ResponseEntity<RecommendationDto.RecommendationResponse> getRecommendations(
            @PathVariable Long id) {
        return ResponseEntity.ok(recommendationService.getRecommendations(id));
    }

    /** DELETE /api/demands/{id} — Delete demand */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDemand(@PathVariable Long id) {
        demandService.deleteDemand(id);
        return ResponseEntity.noContent().build();
    }
}
