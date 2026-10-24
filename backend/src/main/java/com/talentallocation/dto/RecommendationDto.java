package com.talentallocation.dto;

import com.talentallocation.domain.EmployeeSkill;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTOs for the candidate recommendation and scoring system.
 * Scores are always based on stored data — never fabricated.
 */
public class RecommendationDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CandidateScore {
        private Long employeeId;
        private String employeeName;
        private String employeeCode;
        private String location;
        private String department;
        private String seniorityLevel;

        // Score breakdown (0-100 for each dimension)
        private double totalScore;
        private double skillScore;
        private double availabilityScore;
        private double locationScore;
        private double experienceScore;

        // Eligibility
        private boolean eligible;
        private boolean allMandatorySkillsMet;

        // Available capacity in the requested period
        private int availablePercent;

        // Allocation & Availability distinction (BUG 1, BUG 2, BUG 6)
        private boolean alreadyAllocated;
        private boolean hasSufficientCapacity;
        private String allocationStatus;
        private String allocationStatusReason;
        private String fitClassification;

        // Detailed skill match info
        private List<SkillMatchDetail> skillMatches;
        private List<SkillGapDetail> skillGaps;

        // Data transparency
        private boolean hasCompleteSkillData;
        private boolean hasAvailabilityData;
        private String dataDisclosure; // If any data is missing, state it clearly
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkillMatchDetail {
        private Long skillId;
        private String skillName;
        private boolean mandatory;
        private EmployeeSkill.ProficiencyLevel requiredLevel;
        private EmployeeSkill.ProficiencyLevel actualLevel;
        private BigDecimal yearsExperience;
        private LocalDate lastValidatedAt;
        private boolean certified;
        private boolean meets;
        private double scoreContribution;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkillGapDetail {
        private Long skillId;
        private String skillName;
        private boolean mandatory;
        private EmployeeSkill.ProficiencyLevel requiredLevel;
        private EmployeeSkill.ProficiencyLevel actualLevel; // null if not present at all
        private String suggestedCourse;
        private Integer estimatedDurationDays;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecommendationResponse {
        private Long demandId;
        private String projectName;
        private String role;
        private List<CandidateScore> candidates;
        private List<CandidateScore> eligibleCandidates;
        private List<CandidateScore> ineligibleCandidates;
        private int totalEvaluated;
        private String scoringMethodology;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AvailabilityResult {
        private Long employeeId;
        private String employeeName;
        private int requestedPercent;
        private int currentAllocatedPercent;
        private int availablePercent;
        private boolean fullyAvailable;
        private boolean partiallyAvailable;
        private List<AllocationDto.Response> conflictingAllocations;
    }
}
