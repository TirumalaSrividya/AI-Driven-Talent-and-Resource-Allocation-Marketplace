package com.talentallocation.controller;

import com.talentallocation.dto.*;
import com.talentallocation.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ResourceController {

    private final EmployeeService employeeService;
    private final AllocationService allocationService;

    /**
     * GET /api/resources/available — Available resources for date range and skill filters
     * SQL Task: Available resources for a date range
     */
    @GetMapping("/resources/available")
    public ResponseEntity<List<EmployeeDto.Response>> getAvailableResources(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long skillId,
            @RequestParam(required = false) String location) {
        return ResponseEntity.ok(
            employeeService.getAvailableEmployees(startDate, endDate, skillId, location));
    }

    /**
     * POST /api/allocations — Create allocation
     */
    @PostMapping("/allocations")
    public ResponseEntity<AllocationDto.Response> createAllocation(
            @Valid @RequestBody AllocationDto.Request request) {
        return ResponseEntity.status(201).body(allocationService.createAllocation(request));
    }

    /**
     * GET /api/allocations — List allocations
     */
    @GetMapping("/allocations")
    public ResponseEntity<List<AllocationDto.Response>> getAllAllocations() {
        return ResponseEntity.ok(allocationService.getAllAllocations());
    }

    /**
     * GET /api/allocations/{id} — Get allocation
     */
    @GetMapping("/allocations/{id}")
    public ResponseEntity<AllocationDto.Response> getAllocation(@PathVariable Long id) {
        return ResponseEntity.ok(allocationService.getAllocation(id));
    }

    /**
     * PUT /api/allocations/{id}/approve — Approve allocation (with revalidation)
     */
    @PutMapping("/allocations/{id}/approve")
    public ResponseEntity<AllocationDto.Response> approveAllocation(
            @PathVariable Long id,
            @Valid @RequestBody AllocationDto.ApprovalRequest request) {
        return ResponseEntity.ok(allocationService.approveAllocation(id, request));
    }

    /**
     * PUT /api/allocations/{id}/cancel — Cancel allocation
     * TC-06-12: Cancel allocation → capacity released and history retained
     */
    @PutMapping("/allocations/{id}/cancel")
    public ResponseEntity<AllocationDto.Response> cancelAllocation(
            @PathVariable Long id,
            @Valid @RequestBody AllocationDto.CancelRequest request) {
        return ResponseEntity.ok(allocationService.cancelAllocation(id, request));
    }

    /**
     * GET /api/resources/availability/{employeeId} — Check specific employee availability
     */
    @GetMapping("/resources/availability/{employeeId}")
    public ResponseEntity<RecommendationDto.AvailabilityResult> getAvailability(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "100") int requestedPercent) {
        return ResponseEntity.ok(
            allocationService.getAvailability(employeeId, startDate, endDate, requestedPercent));
    }
}
