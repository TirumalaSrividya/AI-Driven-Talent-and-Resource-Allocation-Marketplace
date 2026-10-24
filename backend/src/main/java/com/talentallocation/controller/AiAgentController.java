package com.talentallocation.controller;

import com.talentallocation.dto.*;
import com.talentallocation.service.AgentSimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AiAgentController {

    private final AgentSimulationService agentSimulationService;

    /**
     * POST /api/ai/staffing-advice
     * Run the AI staffing agent workflow for a demand.
     * Returns recommendation + approval token (no allocation yet).
     */
    @PostMapping("/staffing-advice")
    public ResponseEntity<AiAgentDto.StaffingAdviceResponse> getStaffingAdvice(
            @Valid @RequestBody AiAgentDto.StaffingRequest request) {
        return ResponseEntity.ok(agentSimulationService.getStaffingAdvice(request));
    }

    /**
     * POST /api/ai/approve-allocation
     * Approve the AI recommendation — triggers revalidation then creates allocation.
     */
    @PostMapping("/approve-allocation")
    public ResponseEntity<AiAgentDto.ApprovalResponse> approveAllocation(
            @Valid @RequestBody AiAgentDto.ApprovalRequest request) {
        return ResponseEntity.ok(agentSimulationService.approveRecommendation(request));
    }
}
