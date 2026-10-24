package com.talentallocation.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * DTOs for the AI Staffing Agent.
 * SIMULATION: The agent workflow is implemented in Java with goal-driven logic.
 * In production, replace AgentSimulationService with LLM API calls with function calling.
 */
public class AiAgentDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StaffingRequest {
        private Long demandId;
        private String question; // Optional: free-form question about the demand
        private String requestedBy;
        private String contextCandidateName; // Primary candidate in current context (e.g. "Alice")
        private String previousCandidateName; // Secondary candidate in context (e.g. "Henry Wilson")
        private String lastGoal; // Previous turn's goal (e.g. "CANDIDATE_RECOMMENDATION")
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ToolCall {
        private String toolName;
        private Map<String, Object> parameters;
        private Object result;
        private String timestamp;
        private String status;  // e.g. "SUCCESS", "FAILED"
        private String summary; // Human-readable trace summary
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CandidateSearchItem {
        private Long employeeId;
        private String employeeName;
        private String department;
        private String location;
        private int availablePercent;
        private boolean eligible;
        private double skillScore;
        private double totalScore;
        private String statusReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StaffingAdviceResponse {
        // SIMULATION label — always present
        private String agentMode; // "SIMULATED" or "LIVE"
        private String simulationNote;

        private Long demandId;
        private String demandSummary;

        // Goal-driven workflow fields
        private String userGoal; // e.g. "CANDIDATE_RECOMMENDATION", "ELIGIBILITY_EXPLANATION", "AVAILABILITY_CHECK", "CANDIDATE_SEARCH", "CANDIDATE_COMPARISON", "FOLLOW_UP_REASONING"
        private List<String> plan; // Planned steps
        private String evidenceSummary; // Summary of evidence retrieved
        private String validationResult; // Rule validation summary
        private String outcome; // e.g. "RECOMMENDED", "COMPARISON_COMPLETED", "INELIGIBLE", "CAPACITY_CONFIRMED", etc.
        private String conversationalAnswer; // Conversational response answering the specific question naturally
        private String activeCandidateName; // Candidate currently in focus
        private String comparedCandidateName; // Second candidate if compared
        private List<CandidateSearchItem> searchResults; // For candidate search
        private List<String> suggestedFollowUps; // Contextual follow-up suggestions

        // Agent's goal-driven workflow trace
        private List<ToolCall> toolCallTrace;

        // Natural language recommendation (template-generated in simulation)
        private String recommendation;
        private String reasoning;
        private String skillAnalysis;
        private String availabilityAnalysis;
        private String locationAnalysis;
        private String learningPath;

        // Top candidate recommended
        private RecommendationDto.CandidateScore topCandidate;
        private List<RecommendationDto.CandidateScore> allCandidates;

        // Training recommendations
        private List<TrainingDto.Response> trainingRecommendations;

        // Approval state
        private String approvalStatus; // PENDING_APPROVAL, APPROVED, REJECTED, NOT_APPLICABLE
        private String approvalToken; // Token for confirming allocation

        // Data transparency
        private List<String> dataWarnings; // "Employee X has no availability data"
        private String groundingStatement; // States that all claims are based on stored data
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApprovalRequest {
        private String approvalToken;
        private String approvedBy;
        private Long demandId;
        private Long employeeId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApprovalResponse {
        private String status; // APPROVED / REJECTED (revalidation failed)
        private String message;
        private AllocationDto.Response allocation;
        private String revalidationNote;
    }
}
