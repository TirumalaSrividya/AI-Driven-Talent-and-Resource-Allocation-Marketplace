package com.talentallocation.service;

import com.talentallocation.domain.*;
import com.talentallocation.dto.*;
import com.talentallocation.exception.*;
import com.talentallocation.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Scoring and recommendation service.
 * All scores are based purely on stored data — no inference or fabrication.
 * TC-06-03: Search candidates with documented eligibility and ranking criteria.
 * TC-06-04: Excludes candidates missing mandatory skills or flags them clearly.
 * TC-06-09: Score breakdown matches stored records.
 * TC-06-10: Missing data disclosed, not fabricated.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RecommendationService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final ProjectDemandRepository demandRepository;
    private final AllocationRepository allocationRepository;

    // Scoring weights (must sum to 100)
    private static final double WEIGHT_SKILLS = 50.0;
    private static final double WEIGHT_AVAILABILITY = 25.0;
    private static final double WEIGHT_LOCATION = 15.0;
    private static final double WEIGHT_EXPERIENCE = 10.0;

    /**
     * Get ranked candidate list for a demand.
     * Eligibility is determined deterministically using stored data only.
     */
    public RecommendationDto.RecommendationResponse getRecommendations(Long demandId) {
        ProjectDemand demand = demandRepository.findById(demandId)
            .orElseThrow(() -> new ResourceNotFoundException("ProjectDemand", demandId));

        List<Employee> allActiveEmployees = employeeRepository.findAllActiveWithSkills();
        List<RecommendationDto.CandidateScore> scored = new ArrayList<>();

        for (Employee employee : allActiveEmployees) {
            RecommendationDto.CandidateScore score = scoreCandidate(employee, demand);
            scored.add(score);
        }

        // Sort by: eligible first, then unallocated before already allocated, then capacity, then total score descending
        scored.sort((a, b) -> {
            if (a.isEligible() != b.isEligible()) {
                return a.isEligible() ? -1 : 1;
            }
            if (a.isAlreadyAllocated() != b.isAlreadyAllocated()) {
                return a.isAlreadyAllocated() ? 1 : -1;
            }
            if (a.isHasSufficientCapacity() != b.isHasSufficientCapacity()) {
                return a.isHasSufficientCapacity() ? -1 : 1;
            }
            return Double.compare(b.getTotalScore(), a.getTotalScore());
        });

        List<RecommendationDto.CandidateScore> eligible = scored.stream()
            .filter(RecommendationDto.CandidateScore::isEligible).collect(Collectors.toList());
        List<RecommendationDto.CandidateScore> ineligible = scored.stream()
            .filter(c -> !c.isEligible()).collect(Collectors.toList());

        return RecommendationDto.RecommendationResponse.builder()
            .demandId(demandId)
            .projectName(demand.getProjectName())
            .role(demand.getRole())
            .candidates(scored)
            .eligibleCandidates(eligible)
            .ineligibleCandidates(ineligible)
            .totalEvaluated(scored.size())
            .scoringMethodology(buildMethodologyDescription())
            .build();
    }

    /**
     * Score a single candidate against a demand.
     * All scores are evidence-based — returns data disclosure if any field is missing.
     */
    public RecommendationDto.CandidateScore scoreCandidate(Employee employee, ProjectDemand demand) {
        List<EmployeeSkill> employeeSkills = employeeSkillRepository.findByEmployeeId(employee.getId());
        List<DemandRequiredSkill> requiredSkills = demand.getRequiredSkills();

        // Skill analysis
        List<RecommendationDto.SkillMatchDetail> matches = new ArrayList<>();
        List<RecommendationDto.SkillGapDetail> gaps = new ArrayList<>();

        boolean allMandatoryMet = true;
        int totalRequiredSkills = requiredSkills != null ? requiredSkills.size() : 0;
        int passedSkills = 0;
        int mandatorySkillCount = 0;
        int mandatorySkillMet = 0;

        if (requiredSkills != null) {
            for (DemandRequiredSkill required : requiredSkills) {
                Optional<EmployeeSkill> empSkill = employeeSkills.stream()
                    .filter(es -> es.getSkill().getId().equals(required.getSkill().getId()))
                    .findFirst();

                boolean meets = empSkill.map(es ->
                    es.getProficiencyLevel().meetsOrExceeds(required.getMinimumProficiency())).orElse(false);

                if (meets) {
                    passedSkills++;
                }

                double scoreContrib = meets && totalRequiredSkills > 0
                    ? Math.round((100.0 / totalRequiredSkills) * 100.0) / 100.0
                    : 0.0;

                matches.add(RecommendationDto.SkillMatchDetail.builder()
                    .skillId(required.getSkill().getId())
                    .skillName(required.getSkill().getName())
                    .mandatory(required.isMandatory())
                    .requiredLevel(required.getMinimumProficiency())
                    .actualLevel(empSkill.map(EmployeeSkill::getProficiencyLevel).orElse(null))
                    .yearsExperience(empSkill.map(EmployeeSkill::getYearsExperience).orElse(null))
                    .lastValidatedAt(empSkill.map(EmployeeSkill::getLastValidatedAt).orElse(null))
                    .certified(empSkill.map(EmployeeSkill::isCertified).orElse(false))
                    .meets(meets)
                    .scoreContribution(scoreContrib)
                    .build());

                if (required.isMandatory()) {
                    mandatorySkillCount++;
                    if (meets) mandatorySkillMet++;
                    else allMandatoryMet = false;
                }

                if (!meets) {
                    gaps.add(RecommendationDto.SkillGapDetail.builder()
                        .skillId(required.getSkill().getId())
                        .skillName(required.getSkill().getName())
                        .mandatory(required.isMandatory())
                        .requiredLevel(required.getMinimumProficiency())
                        .actualLevel(empSkill.map(EmployeeSkill::getProficiencyLevel).orElse(null))
                        .suggestedCourse(suggestCourse(required.getSkill().getName(), required.getMinimumProficiency()))
                        .estimatedDurationDays(estimateDuration(
                            empSkill.map(EmployeeSkill::getProficiencyLevel).orElse(null),
                            required.getMinimumProficiency()))
                        .build());
                }
            }
        }

        // Skill Match = (Passed Skills ÷ Total Required Skills) × 100
        double rawSkillScore = totalRequiredSkills > 0
            ? ((double) passedSkills / totalRequiredSkills) * 100.0
            : 0.0;
        double normalizedSkillScore = Math.round(rawSkillScore * 100.0) / 100.0;

        // Availability score
        Integer currentAllocated = allocationRepository.sumAllocatedPercentForPeriod(
            employee.getId(), demand.getStartDate(), demand.getEndDate(), null);
        int allocated = currentAllocated != null ? currentAllocated : 0;
        int available = 100 - allocated;
        double availabilityScore = available >= demand.getAllocationPercent() ? 100.0
            : (double) available / demand.getAllocationPercent() * 100.0;

        // Location score
        double locationScore = 0.0;
        if (demand.getLocation() == null || demand.getLocation().isBlank()) {
            locationScore = 100.0; // No location requirement
        } else if (demand.getLocation().equalsIgnoreCase(employee.getLocation())) {
            locationScore = 100.0;
        } else {
            locationScore = 30.0; // Remote possibility, partial score
        }

        // Experience score — based on total years in matching skills
        double totalYears = matches.stream()
            .filter(m -> m.isMeets() && m.getYearsExperience() != null)
            .mapToDouble(m -> m.getYearsExperience().doubleValue())
            .sum();
        double experienceScore = Math.min(100.0, totalYears * 10.0);

        // Weighted total score
        double totalScore =
            (normalizedSkillScore * WEIGHT_SKILLS +
             availabilityScore * WEIGHT_AVAILABILITY +
             locationScore * WEIGHT_LOCATION +
             experienceScore * WEIGHT_EXPERIENCE) / 100.0;

        // TC-06-10: Data disclosure — never fabricate missing data
        List<String> dataWarnings = new ArrayList<>();
        if (employeeSkills.isEmpty()) {
            dataWarnings.add("No skill records found for " + employee.getName() +
                " — skill assessment cannot be completed");
        }

        // Check if employee already allocated to this demand
        boolean alreadyAllocated = allocationRepository.existsByEmployeeIdAndDemandIdAndStatusIn(
            employee.getId(), demand.getId(), List.of(Allocation.AllocationStatus.PENDING, Allocation.AllocationStatus.ACTIVE));
        boolean hasSufficientCapacity = available >= demand.getAllocationPercent();

        String fitClassification;
        if (totalScore >= 75.0) {
            fitClassification = "HIGH FIT";
        } else if (totalScore >= 50.0) {
            fitClassification = "MODERATE FIT";
        } else {
            fitClassification = "LOW FIT";
        }

        String allocStatus;
        String allocReason;
        if (alreadyAllocated) {
            allocStatus = "ALREADY_ALLOCATED";
            allocReason = "Already allocated to Demand #" + demand.getId();
        } else if (available <= 0) {
            allocStatus = "UNAVAILABLE";
            allocReason = "Fully committed for requested period (0% capacity)";
        } else if (!hasSufficientCapacity) {
            allocStatus = "PARTIALLY_AVAILABLE";
            allocReason = "Insufficient capacity (" + available + "% available, " + demand.getAllocationPercent() + "% required)";
        } else {
            allocStatus = "AVAILABLE";
            allocReason = "Available (" + available + "% capacity)";
        }

        String dataDisclosure = dataWarnings.isEmpty() ? null
            : "[DATA DISCLOSURE] " + String.join("; ", dataWarnings);

        boolean isEligible = allMandatoryMet && !employeeSkills.isEmpty() &&
            (employee.getEmploymentStatus() == null || employee.getEmploymentStatus() == Employee.EmploymentStatus.ACTIVE);

        return RecommendationDto.CandidateScore.builder()
            .employeeId(employee.getId())
            .employeeName(employee.getName())
            .employeeCode(employee.getEmployeeCode())
            .location(employee.getLocation())
            .department(employee.getDepartment())
            .seniorityLevel(employee.getSeniorityLevel())
            .totalScore(Math.round(totalScore * 10.0) / 10.0)
            .skillScore(normalizedSkillScore)
            .availabilityScore(Math.round(availabilityScore * 10.0) / 10.0)
            .locationScore(locationScore)
            .experienceScore(Math.round(experienceScore * 10.0) / 10.0)
            .eligible(isEligible)
            .allMandatorySkillsMet(allMandatoryMet)
            .availablePercent(available)
            .alreadyAllocated(alreadyAllocated)
            .hasSufficientCapacity(hasSufficientCapacity)
            .allocationStatus(allocStatus)
            .allocationStatusReason(allocReason)
            .fitClassification(fitClassification)
            .skillMatches(matches)
            .skillGaps(gaps)
            .hasCompleteSkillData(!employeeSkills.isEmpty())
            .hasAvailabilityData(true)
            .dataDisclosure(dataDisclosure)
            .build();
    }

    private String buildMethodologyDescription() {
        return String.format(
            "Scoring: Skills %.0f%% (passed skills / total required skills) | " +
            "Availability %.0f%% (capacity for requested period) | " +
            "Location %.0f%% (exact match=100, remote=30) | " +
            "Experience %.0f%% (years in matched skills, capped at 10yrs). " +
            "Eligibility requires verified mandatory skill criteria. Availability and commitment conflicts are managed separately.",
            WEIGHT_SKILLS, WEIGHT_AVAILABILITY, WEIGHT_LOCATION, WEIGHT_EXPERIENCE);
    }

    private String suggestCourse(String skillName, EmployeeSkill.ProficiencyLevel targetLevel) {
        return switch (targetLevel) {
            case BEGINNER -> skillName + " Fundamentals (Coursera/Udemy) - ~2 weeks";
            case INTERMEDIATE -> skillName + " Practitioner Course (Pluralsight) - ~4 weeks";
            case ADVANCED -> skillName + " Advanced Certification - ~8 weeks";
            case EXPERT -> skillName + " Expert Track + Real-world Project - ~12 weeks";
        };
    }

    private int estimateDuration(EmployeeSkill.ProficiencyLevel current,
                                  EmployeeSkill.ProficiencyLevel target) {
        if (current == null) {
            return switch (target) {
                case BEGINNER -> 14;
                case INTERMEDIATE -> 30;
                case ADVANCED -> 60;
                case EXPERT -> 90;
            };
        }
        int gap = target.getNumericValue() - current.getNumericValue();
        return Math.max(0, gap * 20);
    }
}
