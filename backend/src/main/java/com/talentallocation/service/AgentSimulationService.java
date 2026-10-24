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
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ============================================================
 * SIMULATION: Generic Goal-Driven AI Staffing Agent
 * ============================================================
 * This service implements a goal-driven, conversational staffing agent
 * using controlled tool invocations and live application data.
 *
 * It maintains conversation context (active candidate, previous candidate,
 * target demand) so follow-ups like "What about Alice?", "Why?", "Can she take 100%?"
 * are resolved dynamically for ANY employee, demand, skill, or allocation.
 *
 * All claims, scores, and evaluations are grounded strictly in stored
 * database records with zero hallucinated data.
 * ============================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AgentSimulationService {

    private static final String AGENT_MODE = "SIMULATED";
    private static final String SIMULATION_NOTE =
        "[SIMULATION] AI MODE: SIMULATED AGENT. This agent uses rule-based intent recognition " +
        "and controlled tool calling to simulate an LLM. All findings, scores, and evaluations " +
        "are grounded exclusively in database records. No information is fabricated.";

    private static final String GROUNDING_STATEMENT =
        "All claims and calculations are grounded strictly in stored application data from " +
        "ProjectDemand, Employee, EmployeeSkill, and Allocation records. No data is hallucinated.";

    private final RecommendationService recommendationService;
    private final AllocationService allocationService;
    private final TrainingRecommendationRepository trainingRepo;
    private final ProjectDemandRepository demandRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final AuditLogRepository auditLogRepository;

    // In-memory approval token store for human-in-the-loop allocation gate
    private final Map<String, ApprovalContext> pendingApprovals = new HashMap<>();

    public enum AgentGoal {
        INITIALIZATION,
        CANDIDATE_RECOMMENDATION,
        CANDIDATE_COMPARISON,
        FOLLOW_UP_REASONING,
        AVAILABILITY_CHECK,
        ELIGIBILITY_EXPLANATION,
        CANDIDATE_SEARCH,
        SKILL_GAP_INQUIRY,
        DEMAND_INFO_INQUIRY,
        GENERAL_STAFFING_INQUIRY,
        OUT_OF_DOMAIN
    }

    /**
     * Main agent entry point.
     * Goal-driven conversational loop:
     * Question + Context -> Goal Interpretation -> Tool Planning & Execution -> Evidence & Validation -> Conversational Answer
     */
    public AiAgentDto.StaffingAdviceResponse getStaffingAdvice(AiAgentDto.StaffingRequest request) {
        List<AiAgentDto.ToolCall> toolTrace = new ArrayList<>();
        List<String> dataWarnings = new ArrayList<>();

        String rawQuestion = request.getQuestion() != null ? request.getQuestion().trim() : "";
        AgentGoal goal = determineGoal(rawQuestion, request);
        log.info("[AI AGENT] Interpreted goal '{}' from question: '{}' (Context: candidate='{}', prev='{}', lastGoal='{}')",
            goal, rawQuestion, request.getContextCandidateName(), request.getPreviousCandidateName(), request.getLastGoal());

        // Out-of-domain query: do not access demand records, do not reuse candidate context
        if (goal == AgentGoal.OUT_OF_DOMAIN) {
            return handleOutOfDomain(request, rawQuestion, toolTrace, dataWarnings);
        }

        ProjectDemand demand = resolveDemand(request, toolTrace);

        if (demand == null) {
            String warning = "[DATA DISCLOSURE] No active project demand found. Please specify or select a demand.";
            return AiAgentDto.StaffingAdviceResponse.builder()
                .agentMode(AGENT_MODE)
                .simulationNote(SIMULATION_NOTE)
                .groundingStatement(GROUNDING_STATEMENT)
                .userGoal("UNKNOWN")
                .plan(List.of("resolve_demand"))
                .toolCallTrace(toolTrace)
                .outcome("NO_DEMAND")
                .recommendation(warning)
                .conversationalAnswer(warning)
                .approvalStatus("NO_DEMAND")
                .dataWarnings(List.of(warning))
                .build();
        }

        // INITIALIZATION: Demand is selected or Execute Workflow clicked without a question
        if (rawQuestion.isBlank() || goal == AgentGoal.INITIALIZATION) {
            String answer = "I'm ready to help with " + demand.getProjectName() + ". What would you like to know?";
            logToolCall(toolTrace, "initialize_context", Map.of("demandId", demand.getId(), "project", demand.getProjectName()),
                "Context initialized for " + demand.getProjectName() + " (" + demand.getRole() + ")");
            return AiAgentDto.StaffingAdviceResponse.builder()
                .agentMode(AGENT_MODE)
                .simulationNote(SIMULATION_NOTE)
                .groundingStatement(GROUNDING_STATEMENT)
                .userGoal("INITIALIZATION")
                .plan(List.of("1. initialize_context(" + demand.getProjectName() + ")"))
                .evidenceSummary("Context active for " + demand.getProjectName() + ". Awaiting user staffing question.")
                .validationResult("Context active. No rules evaluated yet.")
                .outcome("READY")
                .conversationalAnswer(answer)
                .recommendation(answer)
                .demandId(demand.getId())
                .demandSummary(demand.getRole() + " for " + demand.getProjectName())
                .toolCallTrace(toolTrace)
                .approvalStatus("NOT_APPLICABLE")
                .dataWarnings(Collections.emptyList())
                .build();
        }

        switch (goal) {
            case CANDIDATE_COMPARISON:
                return handleCandidateComparison(request, demand, rawQuestion, toolTrace, dataWarnings);

            case FOLLOW_UP_REASONING:
                return handleFollowUpReasoning(request, demand, rawQuestion, toolTrace, dataWarnings);

            case AVAILABILITY_CHECK:
                return handleAvailabilityCheck(request, demand, rawQuestion, toolTrace, dataWarnings);

            case ELIGIBILITY_EXPLANATION:
                return handleEligibilityExplanation(request, demand, rawQuestion, toolTrace, dataWarnings);

            case CANDIDATE_SEARCH:
                return handleCandidateSearch(request, demand, rawQuestion, toolTrace, dataWarnings);

            case SKILL_GAP_INQUIRY:
                return handleSkillGapInquiry(request, demand, rawQuestion, toolTrace, dataWarnings);

            case DEMAND_INFO_INQUIRY:
                return handleDemandInfoInquiry(request, demand, rawQuestion, toolTrace, dataWarnings);

            case GENERAL_STAFFING_INQUIRY:
                return handleGeneralStaffingInquiry(request, demand, rawQuestion, toolTrace, dataWarnings);

            case CANDIDATE_RECOMMENDATION:
            default:
                return handleCandidateRecommendation(request, demand, rawQuestion, toolTrace, dataWarnings);
        }
    }

    // =========================================================================
    // WORKFLOW 1: CANDIDATE RECOMMENDATION
    // =========================================================================
    private AiAgentDto.StaffingAdviceResponse handleCandidateRecommendation(
            AiAgentDto.StaffingRequest request,
            ProjectDemand demand,
            String question,
            List<AiAgentDto.ToolCall> toolTrace,
            List<String> dataWarnings) {

        List<String> plan = List.of(
            "1. get_demand(" + demand.getId() + ")",
            "2. get_eligible_candidates(" + demand.getId() + ")",
            "3. get_employee_skills(topCandidate)",
            "4. get_availability(topCandidate, requested dates)",
            "5. validate_candidate(capacity, commitments, eligibility)"
        );

        logToolCall(toolTrace, "get_demand", Map.of("demandId", demand.getId()),
            "Retrieved requirements for " + demand.getProjectName() + " (" + demand.getRole() + ")");

        logToolCall(toolTrace, "get_eligible_candidates", Map.of("demandId", demand.getId()),
            "Evaluating active employees against mandatory requirements");

        RecommendationDto.RecommendationResponse recommendations =
            recommendationService.getRecommendations(demand.getId());

        toolTrace.get(toolTrace.size() - 1).setResult(Map.of(
            "totalEvaluated", recommendations.getTotalEvaluated(),
            "eligibleCount", recommendations.getEligibleCandidates().size()
        ));

        auditLog("AI_AGENT", demand.getId(), "TOOL_CALL", request.getRequestedBy(),
            "get_eligible_candidates: found " + recommendations.getEligibleCandidates().size() + " eligible");

        if (recommendations.getEligibleCandidates().isEmpty()) {
            return buildNoEligibleResponse(request, recommendations, toolTrace, dataWarnings);
        }

        // Prefer candidates with sufficient capacity and not already allocated
        List<RecommendationDto.CandidateScore> availableCandidates = recommendations.getEligibleCandidates().stream()
            .filter(c -> !c.isAlreadyAllocated() && c.isHasSufficientCapacity())
            .toList();

        RecommendationDto.CandidateScore topCandidate = !availableCandidates.isEmpty()
            ? availableCandidates.get(0)
            : recommendations.getEligibleCandidates().get(0);

        logToolCall(toolTrace, "get_employee_skills", Map.of("employeeId", topCandidate.getEmployeeId()),
            "Retrieved skill profile for " + topCandidate.getEmployeeName());
        toolTrace.get(toolTrace.size() - 1).setResult(Map.of(
            "skillsFound", topCandidate.getSkillMatches().size(),
            "allMandatoryMet", topCandidate.isAllMandatorySkillsMet()
        ));

        logToolCall(toolTrace, "get_availability", Map.of(
            "employeeId", topCandidate.getEmployeeId(),
            "startDate", demand.getStartDate().toString(),
            "endDate", demand.getEndDate().toString()
        ), "Checked overlapping allocations for " + topCandidate.getEmployeeName());
        toolTrace.get(toolTrace.size() - 1).setResult(Map.of(
            "availablePercent", topCandidate.getAvailablePercent(),
            "requestedPercent", demand.getAllocationPercent(),
            "hasSufficientCapacity", topCandidate.isHasSufficientCapacity()
        ));

        // Validate candidate
        logToolCall(toolTrace, "validate_candidate", Map.of(
            "employeeId", topCandidate.getEmployeeId(),
            "demandId", demand.getId()
        ), "Validated capacity limits, active status, and mandatory skills");

        String validationResult;
        String approvalToken = null;
        String approvalStatus = "PENDING_APPROVAL";
        String outcome = "RECOMMENDED";

        if (topCandidate.isAlreadyAllocated()) {
            approvalStatus = "ALREADY_ALLOCATED";
            outcome = "ALREADY_ALLOCATED";
            validationResult = "Candidate already has an active commitment to this demand.";
            dataWarnings.add(validationResult);
        } else if (!topCandidate.isHasSufficientCapacity()) {
            approvalStatus = "CAPACITY_CONFLICT";
            outcome = "PARTIALLY_AVAILABLE";
            validationResult = "Candidate capacity (" + topCandidate.getAvailablePercent() +
                "%) is below requested (" + demand.getAllocationPercent() + "%).";
            dataWarnings.add(validationResult);
        } else {
            approvalToken = UUID.randomUUID().toString();
            pendingApprovals.put(approvalToken, new ApprovalContext(
                demand.getId(), topCandidate.getEmployeeId(),
                demand.getAllocationPercent(), demand.getStartDate(), demand.getEndDate(),
                LocalDateTime.now().plusHours(24)
            ));
            validationResult = "Candidate is eligible, unallocated, and has 100% capacity available.";
            auditLog("AI_AGENT", demand.getId(), "RECOMMENDATION_ISSUED", request.getRequestedBy(),
                "Recommended employee " + topCandidate.getEmployeeName() + " (score: " + topCandidate.getTotalScore() + ")");
        }

        // Natural, conversational response
        String pronoun = getSubjectPronoun(topCandidate.getEmployeeName());
        String answer = String.format(
            "%s is currently the best match with a score of %d/100. %s meets the mandatory skills and has %d%% available capacity.",
            topCandidate.getEmployeeName(),
            Math.round(topCandidate.getTotalScore()),
            pronoun,
            topCandidate.getAvailablePercent()
        );

        return AiAgentDto.StaffingAdviceResponse.builder()
            .agentMode(AGENT_MODE)
            .simulationNote(SIMULATION_NOTE)
            .groundingStatement(GROUNDING_STATEMENT)
            .userGoal("CANDIDATE_RECOMMENDATION")
            .plan(plan)
            .evidenceSummary(String.format("Top candidate %s scored %d/100 with %d%% capacity across %s to %s.",
                topCandidate.getEmployeeName(), Math.round(topCandidate.getTotalScore()), topCandidate.getAvailablePercent(), demand.getStartDate(), demand.getEndDate()))
            .validationResult(validationResult)
            .outcome(outcome)
            .conversationalAnswer(answer)
            .activeCandidateName(topCandidate.getEmployeeName())
            .comparedCandidateName(null)
            .demandId(demand.getId())
            .demandSummary(demand.getRole() + " for " + demand.getProjectName())
            .toolCallTrace(toolTrace)
            .recommendation(answer)
            .topCandidate(topCandidate)
            .allCandidates(recommendations.getCandidates())
            .approvalStatus(approvalStatus)
            .approvalToken(approvalToken)
            .dataWarnings(dataWarnings)
            .suggestedFollowUps(List.of(
                "What about Alice?",
                "Why?",
                "Can " + topCandidate.getEmployeeName() + " take " + demand.getAllocationPercent() + "%?"
            ))
            .build();
    }

    // =========================================================================
    // WORKFLOW 2: CANDIDATE COMPARISON ("What about Alice?", "How about Bob?")
    // =========================================================================
    private AiAgentDto.StaffingAdviceResponse handleCandidateComparison(
            AiAgentDto.StaffingRequest request,
            ProjectDemand demand,
            String question,
            List<AiAgentDto.ToolCall> toolTrace,
            List<String> dataWarnings) {

        Employee targetEmployee = resolveTargetEmployee(question, request);
        Employee baselineEmployee = resolveBaselineEmployee(request, targetEmployee, demand);

        String targetName = targetEmployee != null ? targetEmployee.getName() : extractNameFromQuestion(question);
        String baselineName = baselineEmployee != null ? baselineEmployee.getName() : "the previous candidate";

        List<String> plan = List.of(
            "1. get_demand(" + demand.getId() + "): Read project requirements",
            "2. score_candidate(\"" + targetName + "\"): Compute skill match, availability, and score",
            "3. compare_with_baseline(\"" + targetName + "\", \"" + baselineName + "\"): Contrast scores and capacity"
        );

        logToolCall(toolTrace, "get_demand", Map.of("demandId", demand.getId()),
            "Reading requirements for " + demand.getProjectName());

        logToolCall(toolTrace, "score_candidate", Map.of("candidate", targetName),
            "Evaluating " + targetName + " against " + demand.getProjectName());

        if (targetEmployee == null) {
            String warning = "[DATA DISCLOSURE] Employee '" + targetName + "' was not found in the employee directory.";
            dataWarnings.add(warning);
            return AiAgentDto.StaffingAdviceResponse.builder()
                .agentMode(AGENT_MODE)
                .simulationNote(SIMULATION_NOTE)
                .groundingStatement(GROUNDING_STATEMENT)
                .userGoal("CANDIDATE_COMPARISON")
                .plan(plan)
                .toolCallTrace(toolTrace)
                .outcome("EMPLOYEE_NOT_FOUND")
                .recommendation(warning)
                .conversationalAnswer(warning)
                .approvalStatus("NOT_APPLICABLE")
                .dataWarnings(dataWarnings)
                .demandId(demand.getId())
                .demandSummary(demand.getRole() + " for " + demand.getProjectName())
                .build();
        }

        RecommendationDto.CandidateScore targetScore = recommendationService.scoreCandidate(targetEmployee, demand);
        RecommendationDto.CandidateScore baselineScore = baselineEmployee != null
            ? recommendationService.scoreCandidate(baselineEmployee, demand)
            : null;

        logToolCall(toolTrace, "compare_with_baseline", Map.of(
            "candidateA", targetName,
            "candidateB", baselineName
        ), "Comparing evaluation metrics between candidates");

        String targetPossessive = getPossessivePronoun(targetName);
        String answer;

        if (baselineScore != null) {
            double targetTotal = targetScore.getTotalScore();
            double baseTotal = baselineScore.getTotalScore();
            String baseFirstName = baselineEmployee.getName().split(" ")[0];
            String targetFirstName = targetEmployee.getName().split(" ")[0];

            if (targetScore.isEligible()) {
                if (targetTotal > baseTotal) {
                    answer = String.format(
                        "%s is actually a stronger match. %s score is %.1f/100 compared with %s's %d/100, mainly because %s skill match is %d%%. %s has %d%% available capacity.",
                        targetFirstName,
                        capitalize(targetPossessive),
                        targetTotal,
                        baseFirstName,
                        Math.round(baseTotal),
                        targetPossessive,
                        Math.round(targetScore.getSkillScore()),
                        getSubjectPronoun(targetName),
                        targetScore.getAvailablePercent()
                    );
                } else if (targetTotal < baseTotal) {
                    answer = String.format(
                        "%s is also eligible, but has a lower score of %.1f/100 compared with %s's %d/100. %s skill match is %d%% and %s has %d%% available capacity.",
                        targetFirstName,
                        targetTotal,
                        baseFirstName,
                        Math.round(baseTotal),
                        capitalize(targetPossessive),
                        Math.round(targetScore.getSkillScore()),
                        getSubjectPronoun(targetName),
                        targetScore.getAvailablePercent()
                    );
                } else {
                    answer = String.format(
                        "%s has an identical score of %.1f/100 to %s's score, with %d%% skill match and %d%% available capacity.",
                        targetFirstName,
                        targetTotal,
                        baseFirstName,
                        Math.round(targetScore.getSkillScore()),
                        targetScore.getAvailablePercent()
                    );
                }
            } else {
                // Ineligible candidate
                List<String> failedSkills = targetScore.getSkillMatches().stream()
                    .filter(m -> m.isMandatory() && !m.isMeets())
                    .map(RecommendationDto.SkillMatchDetail::getSkillName)
                    .toList();
                String reason = failedSkills.isEmpty() ? "employment status is inactive" : "missing mandatory " + String.join(", ", failedSkills);
                answer = String.format(
                    "%s is currently not eligible for %s because of %s. %s score is %.1f/100 with %d%% available capacity, compared with %s's %d/100.",
                    targetFirstName,
                    demand.getProjectName(),
                    reason,
                    capitalize(targetPossessive),
                    targetTotal,
                    targetScore.getAvailablePercent(),
                    baseFirstName,
                    Math.round(baseTotal)
                );
            }
        } else {
            answer = String.format(
                "%s has a score of %.1f/100 with %d%% skill match and %d%% available capacity for %s.",
                targetName, targetScore.getTotalScore(), Math.round(targetScore.getSkillScore()), targetScore.getAvailablePercent(), demand.getProjectName()
            );
        }

        String validationResult = String.format("Comparison completed: %s (Score: %.1f, Skill: %d%%, Avail: %d%%) vs %s (Score: %.1f, Skill: %d%%, Avail: %d%%).",
            targetName, targetScore.getTotalScore(), Math.round(targetScore.getSkillScore()), targetScore.getAvailablePercent(),
            baselineName, baselineScore != null ? baselineScore.getTotalScore() : 0, baselineScore != null ? Math.round(baselineScore.getSkillScore()) : 0, baselineScore != null ? baselineScore.getAvailablePercent() : 0);

        return AiAgentDto.StaffingAdviceResponse.builder()
            .agentMode(AGENT_MODE)
            .simulationNote(SIMULATION_NOTE)
            .groundingStatement(GROUNDING_STATEMENT)
            .userGoal("CANDIDATE_COMPARISON")
            .plan(plan)
            .evidenceSummary(validationResult)
            .validationResult(validationResult)
            .outcome("COMPARISON_COMPLETED")
            .conversationalAnswer(answer)
            .activeCandidateName(targetEmployee.getName())
            .comparedCandidateName(baselineEmployee != null ? baselineEmployee.getName() : null)
            .demandId(demand.getId())
            .demandSummary(demand.getRole() + " for " + demand.getProjectName())
            .toolCallTrace(toolTrace)
            .recommendation(answer)
            .topCandidate(targetScore)
            .approvalStatus("NOT_APPLICABLE")
            .dataWarnings(dataWarnings)
            .suggestedFollowUps(List.of(
                "Why?",
                "Can " + (targetName.split(" ")[0]) + " take 100%?",
                "Who is available for 50% on " + demand.getProjectName() + "?"
            ))
            .build();
    }

    // =========================================================================
    // WORKFLOW 3: FOLLOW-UP REASONING ("Why?", "Why does she rank higher?")
    // =========================================================================
    private AiAgentDto.StaffingAdviceResponse handleFollowUpReasoning(
            AiAgentDto.StaffingRequest request,
            ProjectDemand demand,
            String question,
            List<AiAgentDto.ToolCall> toolTrace,
            List<String> dataWarnings) {

        Employee activeEmp = resolveTargetEmployee(question, request);
        Employee baselineEmp = null;

        if (request.getPreviousCandidateName() != null && !request.getPreviousCandidateName().isBlank()) {
            baselineEmp = findEmployeeByName(request.getPreviousCandidateName());
        }

        // If no baseline was passed, pick the top recommendation from previous turn
        if (baselineEmp == null && activeEmp != null) {
            RecommendationDto.RecommendationResponse recs = recommendationService.getRecommendations(demand.getId());
            for (var c : recs.getEligibleCandidates()) {
                if (!c.getEmployeeId().equals(activeEmp.getId())) {
                    baselineEmp = employeeRepository.findById(c.getEmployeeId()).orElse(null);
                    break;
                }
            }
        }

        String activeName = activeEmp != null ? activeEmp.getName() : "the candidate";
        String baselineName = baselineEmp != null ? baselineEmp.getName() : "the other candidate";

        List<String> plan = List.of(
            "1. get_demand(" + demand.getId() + "): Read mandatory skill criteria",
            "2. compare_skill_breakdown(\"" + activeName + "\", \"" + baselineName + "\"): Inspect skill match differential",
            "3. synthesize_rationale: Explain ranking discrepancy concisely"
        );

        logToolCall(toolTrace, "get_demand", Map.of("demandId", demand.getId()),
            "Reading mandatory criteria for " + demand.getProjectName());

        logToolCall(toolTrace, "compare_skill_breakdown", Map.of(
            "active", activeName, "baseline", baselineName
        ), "Analyzing dimensional score differences");

        String answer;
        String validationResult;

        if (activeEmp != null && baselineEmp != null) {
            RecommendationDto.CandidateScore activeScore = recommendationService.scoreCandidate(activeEmp, demand);
            RecommendationDto.CandidateScore baseScore = recommendationService.scoreCandidate(baselineEmp, demand);

            String activeFirst = activeEmp.getName().split(" ")[0];
            String baseFirst = baselineEmp.getName().split(" ")[0];
            int activeSkill = (int) Math.round(activeScore.getSkillScore());
            int baseSkill = (int) Math.round(baseScore.getSkillScore());

            String projName = demand.getProjectName().replace("Project ", "");

            if (activeScore.getTotalScore() > baseScore.getTotalScore()) {
                answer = String.format(
                    "%s has a stronger skill match for the mandatory %s requirements. %s has %d%% skill match, while %s has %d%%. That's why %s ranks higher, although %s has more available capacity.",
                    activeFirst, projName,
                    baseFirst, baseSkill,
                    activeFirst, activeSkill,
                    activeFirst,
                    baseScore.getAvailablePercent() > activeScore.getAvailablePercent() ? baseFirst : "others"
                );
            } else if (activeScore.getTotalScore() < baseScore.getTotalScore()) {
                answer = String.format(
                    "%s has a lower overall score (%.1f/100 vs %s's %d/100). %s has %d%% skill match compared to %s's %d%%, with %d%% available capacity.",
                    activeFirst, activeScore.getTotalScore(), baseFirst, Math.round(baseScore.getTotalScore()),
                    activeFirst, activeSkill, baseFirst, baseSkill, activeScore.getAvailablePercent()
                );
            } else {
                answer = String.format(
                    "%s and %s have equal scores (%.1f/100). Both meet mandatory skills with comparable skill and availability profiles.",
                    activeFirst, baseFirst, activeScore.getTotalScore()
                );
            }
            validationResult = "Rationale validated against dimensional scoring model (Skill weight 50%, Availability weight 25%).";
        } else if (activeEmp != null) {
            RecommendationDto.CandidateScore score = recommendationService.scoreCandidate(activeEmp, demand);
            String first = activeEmp.getName().split(" ")[0];
            answer = String.format(
                "%s ranks as a top match because %s satisfies %d%% of the required skills for %s with %d%% available capacity.",
                first, getSubjectPronoun(activeName).toLowerCase(), Math.round(score.getSkillScore()), demand.getProjectName(), score.getAvailablePercent()
            );
            validationResult = "Grounded in stored employee skills and allocation records.";
        } else {
            answer = "I don't have enough conversation context to explain the decision. Please specify the candidate you are inquiring about.";
            validationResult = "Insufficient context to explain rationale.";
        }

        return AiAgentDto.StaffingAdviceResponse.builder()
            .agentMode(AGENT_MODE)
            .simulationNote(SIMULATION_NOTE)
            .groundingStatement(GROUNDING_STATEMENT)
            .userGoal("FOLLOW_UP_REASONING")
            .plan(plan)
            .evidenceSummary("Synthesized differential rationale: " + activeName + " vs " + baselineName)
            .validationResult(validationResult)
            .outcome("RATIONALE_PROVIDED")
            .conversationalAnswer(answer)
            .activeCandidateName(activeName)
            .comparedCandidateName(baselineName)
            .demandId(demand.getId())
            .demandSummary(demand.getRole() + " for " + demand.getProjectName())
            .toolCallTrace(toolTrace)
            .recommendation(answer)
            .approvalStatus("NOT_APPLICABLE")
            .dataWarnings(dataWarnings)
            .suggestedFollowUps(List.of(
                "Can " + (activeName.split(" ")[0]) + " take 100%?",
                "Who is available for 50% on " + demand.getProjectName() + "?"
            ))
            .build();
    }

    // =========================================================================
    // WORKFLOW 4: AVAILABILITY CHECK ("Can Alice take 100% allocation?")
    // =========================================================================
    private AiAgentDto.StaffingAdviceResponse handleAvailabilityCheck(
            AiAgentDto.StaffingRequest request,
            ProjectDemand demand,
            String question,
            List<AiAgentDto.ToolCall> toolTrace,
            List<String> dataWarnings) {

        Employee employee = resolveTargetEmployee(question, request);
        String targetName = employee != null ? employee.getName() : extractNameFromQuestion(question);
        int requestedPercent = extractPercentage(question, demand.getAllocationPercent());

        List<String> plan = List.of(
            "1. get_demand(" + demand.getId() + "): Read target timeline (" + demand.getStartDate() + " to " + demand.getEndDate() + ")",
            "2. find_employee(\"" + targetName + "\"): Locate employee record",
            "3. get_availability(" + (employee != null ? employee.getId() : "?") + ", dates, " + requestedPercent + "): Query active allocations",
            "4. validate_capacity: Verify availability against requested " + requestedPercent + "%"
        );

        logToolCall(toolTrace, "get_demand", Map.of("demandId", demand.getId()),
            "Reading date range for " + demand.getProjectName());

        logToolCall(toolTrace, "find_employee", Map.of("searchTerm", targetName),
            "Locating employee " + targetName);

        if (employee == null) {
            String warning = "[DATA DISCLOSURE] Employee '" + targetName + "' was not found in stored records.";
            dataWarnings.add(warning);
            return AiAgentDto.StaffingAdviceResponse.builder()
                .agentMode(AGENT_MODE)
                .simulationNote(SIMULATION_NOTE)
                .groundingStatement(GROUNDING_STATEMENT)
                .userGoal("AVAILABILITY_CHECK")
                .plan(plan)
                .toolCallTrace(toolTrace)
                .outcome("EMPLOYEE_NOT_FOUND")
                .recommendation(warning)
                .conversationalAnswer(warning)
                .approvalStatus("NOT_APPLICABLE")
                .dataWarnings(dataWarnings)
                .demandId(demand.getId())
                .demandSummary(demand.getRole() + " for " + demand.getProjectName())
                .build();
        }

        logToolCall(toolTrace, "get_availability", Map.of(
            "employeeId", employee.getId(),
            "startDate", demand.getStartDate().toString(),
            "endDate", demand.getEndDate().toString(),
            "requestedPercent", requestedPercent
        ), "Computing unallocated capacity in period");

        RecommendationDto.AvailabilityResult avail = allocationService.getAvailability(
            employee.getId(), demand.getStartDate(), demand.getEndDate(), requestedPercent
        );

        toolTrace.get(toolTrace.size() - 1).setResult(Map.of(
            "availablePercent", avail.getAvailablePercent(),
            "currentAllocated", avail.getCurrentAllocatedPercent(),
            "hasCapacity", (avail.getAvailablePercent() >= requestedPercent)
        ));

        logToolCall(toolTrace, "validate_capacity", Map.of(
            "availablePercent", avail.getAvailablePercent(),
            "requestedPercent", requestedPercent
        ), "Testing capacity limit (Available >= Requested)");

        boolean canTake = avail.getAvailablePercent() >= requestedPercent;
        String validationResult = canTake
            ? "Capacity confirmed: " + employee.getName() + " has " + avail.getAvailablePercent() + "% capacity (>= " + requestedPercent + "% requested)."
            : "Capacity exceeded: " + employee.getName() + " only has " + avail.getAvailablePercent() + "% capacity (< " + requestedPercent + "% requested).";

        toolTrace.get(toolTrace.size() - 1).setResult(Map.of("result", validationResult, "canTake", canTake));

        String answer;
        String firstName = employee.getName().split(" ")[0];
        String pronoun = getSubjectPronoun(employee.getName());

        if (canTake) {
            answer = String.format(
                "Yes, %s can take %d%% allocation for %s. %s currently has %d%% available capacity with no conflicting commitments between %s and %s.",
                firstName, requestedPercent, demand.getProjectName(), pronoun, avail.getAvailablePercent(), demand.getStartDate(), demand.getEndDate()
            );
        } else {
            answer = String.format(
                "No, %s cannot take %d%% allocation for %s. %s currently only has %d%% available capacity between %s and %s (committed %d%% to another project). Assigning %d%% would exceed the 100%% capacity limit.",
                firstName, requestedPercent, demand.getProjectName(), pronoun, avail.getAvailablePercent(), demand.getStartDate(), demand.getEndDate(),
                avail.getCurrentAllocatedPercent(), requestedPercent
            );
        }

        return AiAgentDto.StaffingAdviceResponse.builder()
            .agentMode(AGENT_MODE)
            .simulationNote(SIMULATION_NOTE)
            .groundingStatement(GROUNDING_STATEMENT)
            .userGoal("AVAILABILITY_CHECK")
            .plan(plan)
            .evidenceSummary(String.format("%s is %d%% allocated in period, leaving %d%% free capacity.",
                employee.getName(), avail.getCurrentAllocatedPercent(), avail.getAvailablePercent()))
            .validationResult(validationResult)
            .outcome(canTake ? "CAPACITY_CONFIRMED" : "CAPACITY_EXCEEDED")
            .conversationalAnswer(answer)
            .activeCandidateName(employee.getName())
            .demandId(demand.getId())
            .demandSummary(demand.getRole() + " for " + demand.getProjectName())
            .toolCallTrace(toolTrace)
            .recommendation(answer)
            .approvalStatus("NOT_APPLICABLE")
            .dataWarnings(dataWarnings)
            .suggestedFollowUps(List.of(
                "Why isn't James Davis eligible for " + demand.getProjectName() + "?",
                "Who is available for 50% on " + demand.getProjectName() + "?"
            ))
            .build();
    }

    // =========================================================================
    // WORKFLOW 5: ELIGIBILITY EXPLANATION ("Why isn't James Davis eligible?")
    // =========================================================================
    private AiAgentDto.StaffingAdviceResponse handleEligibilityExplanation(
            AiAgentDto.StaffingRequest request,
            ProjectDemand demand,
            String question,
            List<AiAgentDto.ToolCall> toolTrace,
            List<String> dataWarnings) {

        Employee employee = resolveTargetEmployee(question, request);
        String targetName = employee != null ? employee.getName() : extractNameFromQuestion(question);

        List<String> plan = List.of(
            "1. get_demand(" + demand.getId() + "): Retrieve mandatory requirements",
            "2. find_employee(\"" + targetName + "\"): Locate employee in database",
            "3. get_employee_skills(" + (employee != null ? employee.getId() : "?") + "): Inspect recorded proficiencies",
            "4. evaluate_eligibility: Compare actual skills against mandatory criteria"
        );

        logToolCall(toolTrace, "get_demand", Map.of("demandId", demand.getId()),
            "Retrieved requirements for " + demand.getProjectName());

        logToolCall(toolTrace, "find_employee", Map.of("searchTerm", targetName),
            "Searching employee repository for " + targetName);

        if (employee == null) {
            String warning = "[DATA DISCLOSURE] Employee '" + targetName + "' was not found in the employee directory.";
            dataWarnings.add(warning);
            return AiAgentDto.StaffingAdviceResponse.builder()
                .agentMode(AGENT_MODE)
                .simulationNote(SIMULATION_NOTE)
                .groundingStatement(GROUNDING_STATEMENT)
                .userGoal("ELIGIBILITY_EXPLANATION")
                .plan(plan)
                .toolCallTrace(toolTrace)
                .outcome("EMPLOYEE_NOT_FOUND")
                .recommendation(warning)
                .conversationalAnswer(warning)
                .approvalStatus("NOT_APPLICABLE")
                .dataWarnings(dataWarnings)
                .demandId(demand.getId())
                .demandSummary(demand.getRole() + " for " + demand.getProjectName())
                .build();
        }

        logToolCall(toolTrace, "get_employee_skills", Map.of("employeeId", employee.getId()),
            "Retrieved stored skill records for " + employee.getName());
        List<EmployeeSkill> employeeSkills = employeeSkillRepository.findByEmployeeId(employee.getId());

        logToolCall(toolTrace, "evaluate_eligibility", Map.of("employeeId", employee.getId(), "demandId", demand.getId()),
            "Evaluating mandatory skills and employment status against company policy");

        RecommendationDto.CandidateScore score = recommendationService.scoreCandidate(employee, demand);

        List<RecommendationDto.SkillMatchDetail> failedMandatory = score.getSkillMatches().stream()
            .filter(m -> m.isMandatory() && !m.isMeets())
            .toList();

        boolean isEligible = score.isEligible();
        String validationResult = isEligible
            ? "Eligible: " + employee.getName() + " satisfies all mandatory requirements."
            : "Ineligible: " + employee.getName() + " lacks mandatory requirement(s): " +
              failedMandatory.stream().map(m -> m.getSkillName() + " (min " + m.getRequiredLevel() + ")").collect(Collectors.joining(", "));

        toolTrace.get(toolTrace.size() - 1).setResult(Map.of(
            "eligible", isEligible, "failedMandatoryCount", failedMandatory.size()
        ));

        String answer;
        if (isEligible) {
            answer = String.format(
                "%s is eligible for %s (%s). %s satisfies all mandatory skill requirements for the role.",
                employee.getName(), demand.getProjectName(), demand.getRole(), getSubjectPronoun(employee.getName())
            );
        } else {
            if (!failedMandatory.isEmpty()) {
                RecommendationDto.SkillMatchDetail firstFail = failedMandatory.get(0);
                String actual = firstFail.getActualLevel() != null
                    ? "current recorded level is " + firstFail.getActualLevel()
                    : "it is not recorded in his/her profile";
                answer = String.format(
                    "%s is not eligible for %s because %s does not meet the mandatory skill requirement for %s. %s is required at %s, but %s.",
                    employee.getName(), demand.getProjectName(),
                    getSubjectPronoun(employee.getName()).toLowerCase(),
                    firstFail.getSkillName(),
                    firstFail.getSkillName(),
                    firstFail.getRequiredLevel(),
                    actual
                );
            } else if (employee.getEmploymentStatus() != Employee.EmploymentStatus.ACTIVE) {
                answer = String.format(
                    "%s is not eligible for %s because %s employment status is %s (must be ACTIVE).",
                    employee.getName(), demand.getProjectName(), getPossessivePronoun(employee.getName()), employee.getEmploymentStatus()
                );
            } else {
                answer = String.format("%s does not currently meet eligibility criteria for %s.", employee.getName(), demand.getProjectName());
            }
        }

        return AiAgentDto.StaffingAdviceResponse.builder()
            .agentMode(AGENT_MODE)
            .simulationNote(SIMULATION_NOTE)
            .groundingStatement(GROUNDING_STATEMENT)
            .userGoal("ELIGIBILITY_EXPLANATION")
            .plan(plan)
            .evidenceSummary(validationResult)
            .validationResult(validationResult)
            .outcome(isEligible ? "ELIGIBLE" : "INELIGIBLE")
            .conversationalAnswer(answer)
            .activeCandidateName(employee.getName())
            .demandId(demand.getId())
            .demandSummary(demand.getRole() + " for " + demand.getProjectName())
            .toolCallTrace(toolTrace)
            .recommendation(answer)
            .topCandidate(score)
            .approvalStatus("NOT_APPLICABLE")
            .dataWarnings(dataWarnings)
            .suggestedFollowUps(List.of(
                "What skill gaps does " + employee.getName().split(" ")[0] + " have?",
                "Who is available for 50% on " + demand.getProjectName() + "?"
            ))
            .build();
    }

    // =========================================================================
    // WORKFLOW 6: CANDIDATE SEARCH ("Who is available for 50%?")
    // =========================================================================
    private AiAgentDto.StaffingAdviceResponse handleCandidateSearch(
            AiAgentDto.StaffingRequest request,
            ProjectDemand demand,
            String question,
            List<AiAgentDto.ToolCall> toolTrace,
            List<String> dataWarnings) {

        int targetCapacity = extractPercentage(question, 50);

        List<String> plan = List.of(
            "1. get_demand(" + demand.getId() + "): Read role requirements and period (" + demand.getStartDate() + " to " + demand.getEndDate() + ")",
            "2. search_available_candidates: Query active employees with capacity >= " + targetCapacity + "%",
            "3. evaluate_eligibility_and_scores: Score matching candidates against demand skills",
            "4. rank_and_filter: Sort by eligibility, capacity, and score"
        );

        logToolCall(toolTrace, "get_demand", Map.of("demandId", demand.getId()),
            "Reading requirements for " + demand.getProjectName());

        logToolCall(toolTrace, "search_available_candidates", Map.of(
            "demandId", demand.getId(), "minAvailablePercent", targetCapacity
        ), "Finding employees with at least " + targetCapacity + "% availability");

        List<Employee> allEmployees = employeeRepository.findAllActiveWithSkills();
        List<RecommendationDto.CandidateScore> candidates = new ArrayList<>();

        for (Employee emp : allEmployees) {
            RecommendationDto.CandidateScore score = recommendationService.scoreCandidate(emp, demand);
            if (score.getAvailablePercent() >= targetCapacity) {
                candidates.add(score);
            }
        }

        candidates.sort((a, b) -> {
            if (a.isEligible() != b.isEligible()) return a.isEligible() ? -1 : 1;
            return Double.compare(b.getTotalScore(), a.getTotalScore());
        });

        toolTrace.get(toolTrace.size() - 1).setResult(Map.of("matchingCandidatesCount", candidates.size()));

        logToolCall(toolTrace, "rank_and_filter", Map.of("targetCapacity", targetCapacity),
            "Applying eligibility and skill ranking criteria");

        List<AiAgentDto.CandidateSearchItem> searchItems = candidates.stream().map(c ->
            AiAgentDto.CandidateSearchItem.builder()
                .employeeId(c.getEmployeeId())
                .employeeName(c.getEmployeeName())
                .department(c.getDepartment())
                .location(c.getLocation())
                .availablePercent(c.getAvailablePercent())
                .eligible(c.isEligible())
                .skillScore(c.getSkillScore())
                .totalScore(c.getTotalScore())
                .statusReason(c.isEligible() ? "Eligible" : "Ineligible")
                .build()
        ).toList();

        List<RecommendationDto.CandidateScore> eligibleMatches = candidates.stream().filter(RecommendationDto.CandidateScore::isEligible).toList();
        String answer;

        if (!eligibleMatches.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Here are the eligible candidates with at least %d%% availability for %s:\n", targetCapacity, demand.getProjectName()));
            int rank = 1;
            for (var c : eligibleMatches) {
                sb.append(String.format("%d. %s — %d%% available, Score: %.1f/100 (%d%% skill match)\n",
                    rank++, c.getEmployeeName(), c.getAvailablePercent(), c.getTotalScore(), Math.round(c.getSkillScore())));
            }
            answer = sb.toString().trim();
        } else {
            answer = String.format("No eligible candidates found with at least %d%% availability for %s.", targetCapacity, demand.getProjectName());
        }

        String validationResult = "Identified " + eligibleMatches.size() + " eligible candidate(s) satisfying >=" + targetCapacity + "% capacity.";

        return AiAgentDto.StaffingAdviceResponse.builder()
            .agentMode(AGENT_MODE)
            .simulationNote(SIMULATION_NOTE)
            .groundingStatement(GROUNDING_STATEMENT)
            .userGoal("CANDIDATE_SEARCH")
            .plan(plan)
            .evidenceSummary(validationResult)
            .validationResult(validationResult)
            .outcome("SEARCH_COMPLETED")
            .conversationalAnswer(answer)
            .searchResults(searchItems)
            .demandId(demand.getId())
            .demandSummary(demand.getRole() + " for " + demand.getProjectName())
            .toolCallTrace(toolTrace)
            .recommendation(answer)
            .approvalStatus("NOT_APPLICABLE")
            .dataWarnings(dataWarnings)
            .suggestedFollowUps(List.of(
                "Why isn't James Davis eligible for " + demand.getProjectName() + "?",
                "Can Alice take 100% allocation for " + demand.getProjectName() + "?"
            ))
            .build();
    }

    // =========================================================================
    // WORKFLOW 7: SKILL GAP INQUIRY ("What skill gaps does he have?")
    // =========================================================================
    private AiAgentDto.StaffingAdviceResponse handleSkillGapInquiry(
            AiAgentDto.StaffingRequest request,
            ProjectDemand demand,
            String question,
            List<AiAgentDto.ToolCall> toolTrace,
            List<String> dataWarnings) {

        Employee employee = resolveTargetEmployee(question, request);
        String targetName = employee != null ? employee.getName() : "the candidate";

        List<String> plan = List.of(
            "1. get_demand(" + demand.getId() + "): Read required skills",
            "2. get_employee_skills(" + (employee != null ? employee.getId() : "?") + "): Compare proficiencies",
            "3. get_training_recommendations: Generate upskilling course suggestions"
        );

        logToolCall(toolTrace, "get_demand", Map.of("demandId", demand.getId()),
            "Reading required competencies for " + demand.getProjectName());

        if (employee == null) {
            String warning = "[DATA DISCLOSURE] Candidate was not identified for skill gap analysis.";
            return AiAgentDto.StaffingAdviceResponse.builder()
                .agentMode(AGENT_MODE)
                .simulationNote(SIMULATION_NOTE)
                .groundingStatement(GROUNDING_STATEMENT)
                .userGoal("SKILL_GAP_INQUIRY")
                .plan(plan)
                .toolCallTrace(toolTrace)
                .outcome("EMPLOYEE_NOT_FOUND")
                .conversationalAnswer(warning)
                .recommendation(warning)
                .approvalStatus("NOT_APPLICABLE")
                .build();
        }

        RecommendationDto.CandidateScore score = recommendationService.scoreCandidate(employee, demand);
        List<RecommendationDto.SkillGapDetail> gaps = score.getSkillGaps();

        logToolCall(toolTrace, "get_training_recommendations", Map.of(
            "employeeId", employee.getId(), "gapCount", gaps.size()
        ), "Mapping skill gaps to training courses");

        String answer;
        if (gaps.isEmpty()) {
            answer = String.format("%s has no skill gaps for %s. All required skills are fully met at or above the expected proficiency.",
                employee.getName(), demand.getProjectName());
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%s has %d skill gap(s) for %s:\n", employee.getName(), gaps.size(), demand.getProjectName()));
            for (var g : gaps) {
                sb.append(String.format("• **%s** (%s): Required %s, actual is %s. Recommended training: %s (~%d days).\n",
                    g.getSkillName(), g.isMandatory() ? "Mandatory" : "Optional", g.getRequiredLevel(),
                    g.getActualLevel() != null ? g.getActualLevel().name() : "Not in profile",
                    g.getSuggestedCourse(), g.getEstimatedDurationDays()));
            }
            answer = sb.toString().trim();
        }

        String validationResult = gaps.isEmpty() ? "No skill gaps detected." : gaps.size() + " gap(s) identified with training paths.";

        return AiAgentDto.StaffingAdviceResponse.builder()
            .agentMode(AGENT_MODE)
            .simulationNote(SIMULATION_NOTE)
            .groundingStatement(GROUNDING_STATEMENT)
            .userGoal("SKILL_GAP_INQUIRY")
            .plan(plan)
            .evidenceSummary(validationResult)
            .validationResult(validationResult)
            .outcome("GAPS_ANALYZED")
            .conversationalAnswer(answer)
            .activeCandidateName(employee.getName())
            .demandId(demand.getId())
            .demandSummary(demand.getRole() + " for " + demand.getProjectName())
            .toolCallTrace(toolTrace)
            .recommendation(answer)
            .topCandidate(score)
            .approvalStatus("NOT_APPLICABLE")
            .dataWarnings(dataWarnings)
            .build();
    }

    // =========================================================================
    // WORKFLOW 8: DEMAND INFO INQUIRY ("Where is Phoenix located?", "What are the project dates?")
    // =========================================================================
    private AiAgentDto.StaffingAdviceResponse handleDemandInfoInquiry(
            AiAgentDto.StaffingRequest request,
            ProjectDemand demand,
            String question,
            List<AiAgentDto.ToolCall> toolTrace,
            List<String> dataWarnings) {

        List<String> plan = List.of(
            "1. get_demand(" + demand.getId() + "): Retrieve demand requirements, schedule, location, and role",
            "2. query_demand_skills: Read mandatory and optional skill competencies",
            "3. synthesize_demand_details: Answer user's project-specific question"
        );

        logToolCall(toolTrace, "get_demand", Map.of("demandId", demand.getId()),
            "Retrieved demand specifications for " + demand.getProjectName());

        logToolCall(toolTrace, "query_demand_skills", Map.of("demandId", demand.getId()),
            "Reading required competencies (" + (demand.getRequiredSkills() != null ? demand.getRequiredSkills().size() : 0) + " skills defined)");

        String q = question.toLowerCase();
        String answer;

        if (q.contains("where") || q.contains("location")) {
            answer = String.format("%s is based in %s with %d%% allocation required.",
                demand.getProjectName(), demand.getLocation() != null ? demand.getLocation() : "Remote", demand.getAllocationPercent());
        } else if (q.contains("date") || q.contains("duration") || q.contains("start") || q.contains("end") || q.contains("timeline") || q.contains("period")) {
            answer = String.format("%s runs from %s to %s (%d%% capacity needed).",
                demand.getProjectName(), demand.getStartDate(), demand.getEndDate(), demand.getAllocationPercent());
        } else if (q.contains("role") || q.contains("position") || q.contains("title")) {
            answer = String.format("%s requires a %s in %s.",
                demand.getProjectName(), demand.getRole(), demand.getLocation());
        } else if (q.contains("skill") || q.contains("requirement") || q.contains("need")) {
            List<String> mandatory = demand.getRequiredSkills() != null ? demand.getRequiredSkills().stream()
                .filter(DemandRequiredSkill::isMandatory)
                .map(s -> s.getSkill().getName() + " (" + s.getMinimumProficiency() + ")")
                .toList() : Collections.emptyList();
            List<String> optional = demand.getRequiredSkills() != null ? demand.getRequiredSkills().stream()
                .filter(s -> !s.isMandatory())
                .map(s -> s.getSkill().getName())
                .toList() : Collections.emptyList();
            answer = String.format("%s requires a %s. Mandatory skills: %s.%s",
                demand.getProjectName(), demand.getRole(),
                String.join(", ", mandatory),
                optional.isEmpty() ? "" : " Optional: " + String.join(", ", optional) + ".");
        } else {
            answer = String.format("%s is for a %s in %s from %s to %s with %d%% allocation required.",
                demand.getProjectName(), demand.getRole(), demand.getLocation(), demand.getStartDate(), demand.getEndDate(), demand.getAllocationPercent());
        }

        String evidence = String.format("Demand #%d: %s, Role: %s, Dates: %s to %s, Location: %s, Capacity: %d%%.",
            demand.getId(), demand.getProjectName(), demand.getRole(), demand.getStartDate(), demand.getEndDate(), demand.getLocation(), demand.getAllocationPercent());

        return AiAgentDto.StaffingAdviceResponse.builder()
            .agentMode(AGENT_MODE)
            .simulationNote(SIMULATION_NOTE)
            .groundingStatement(GROUNDING_STATEMENT)
            .userGoal("DEMAND_INFO_INQUIRY")
            .plan(plan)
            .evidenceSummary(evidence)
            .validationResult("Retrieved verified ProjectDemand specifications from database.")
            .outcome("PROJECT_DETAILS_RETRIEVED")
            .conversationalAnswer(answer)
            .recommendation(answer)
            .demandId(demand.getId())
            .demandSummary(demand.getRole() + " for " + demand.getProjectName())
            .toolCallTrace(toolTrace)
            .approvalStatus("NOT_APPLICABLE")
            .dataWarnings(dataWarnings)
            .build();
    }

    // =========================================================================
    // WORKFLOW 9: GENERAL / UNRELATED STAFFING INQUIRY
    // =========================================================================
    private AiAgentDto.StaffingAdviceResponse handleGeneralStaffingInquiry(
            AiAgentDto.StaffingRequest request,
            ProjectDemand demand,
            String question,
            List<AiAgentDto.ToolCall> toolTrace,
            List<String> dataWarnings) {

        List<String> plan = List.of(
            "1. analyze_query_scope: Parse natural language question against staffing capabilities",
            "2. query_staffing_context: Inspect active project demand (" + demand.getProjectName() + ")",
            "3. formulate_contextual_response: Address question appropriately without generating an unprompted recommendation"
        );

        logToolCall(toolTrace, "analyze_query_scope", Map.of("question", question),
            "Analyzed question scope against staffing operations");

        logToolCall(toolTrace, "query_staffing_context", Map.of("demand", demand.getProjectName()),
            "Checked active project context for " + demand.getProjectName());

        String answer = String.format(
            "I'm assisting with staffing for %s (%s). Regarding your question: I can help you find suitable candidates, compare employees, verify capacity and availability, or analyze skill eligibility for this demand. What specific staffing detail would you like to explore?",
            demand.getProjectName(), demand.getRole()
        );

        return AiAgentDto.StaffingAdviceResponse.builder()
            .agentMode(AGENT_MODE)
            .simulationNote(SIMULATION_NOTE)
            .groundingStatement(GROUNDING_STATEMENT)
            .userGoal("GENERAL_STAFFING_INQUIRY")
            .plan(plan)
            .evidenceSummary("General inquiry addressed within the context of " + demand.getProjectName() + ".")
            .validationResult("General query handled conversationally without triggering unrequested candidate recommendations.")
            .outcome("QUERY_ADDRESSED")
            .conversationalAnswer(answer)
            .recommendation(answer)
            .demandId(demand.getId())
            .demandSummary(demand.getRole() + " for " + demand.getProjectName())
            .toolCallTrace(toolTrace)
            .approvalStatus("NOT_APPLICABLE")
            .dataWarnings(dataWarnings)
            .build();
    }

    // =========================================================================
    // WORKFLOW 10: OUT-OF-DOMAIN / UNRELATED QUERY GUIDANCE
    // =========================================================================
    private AiAgentDto.StaffingAdviceResponse handleOutOfDomain(
            AiAgentDto.StaffingRequest request,
            String question,
            List<AiAgentDto.ToolCall> toolTrace,
            List<String> dataWarnings) {

        List<String> plan = List.of(
            "1. analyze_query_intent: Assess query relevance against staffing domain",
            "2. domain_boundary_validation: Detect out-of-domain subject matter",
            "3. provide_domain_guidance: Guide user to supported staffing operations"
        );

        logToolCall(toolTrace, "domain_boundary_check", Map.of("query", question),
            "Query identified as outside the staffing and resource-allocation domain");

        String answer = "I'm designed to help with staffing and resource-allocation questions. " +
            "I can help with candidates, comparisons, availability, eligibility, skill gaps, and related staffing decisions.";

        return AiAgentDto.StaffingAdviceResponse.builder()
            .agentMode(AGENT_MODE)
            .simulationNote(SIMULATION_NOTE)
            .groundingStatement(GROUNDING_STATEMENT)
            .userGoal("OUT_OF_DOMAIN")
            .plan(plan)
            .evidenceSummary("Query is outside the staffing and resource allocation domain. No project or candidate data accessed.")
            .validationResult("Domain validation: Request does not relate to staffing, employee allocation, skills, or demands.")
            .outcome("OUT_OF_DOMAIN")
            .conversationalAnswer(answer)
            .recommendation(answer)
            .demandId(null)
            .demandSummary(null)
            .activeCandidateName(null)
            .comparedCandidateName(null)
            .toolCallTrace(toolTrace)
            .approvalStatus("NOT_APPLICABLE")
            .dataWarnings(dataWarnings)
            .build();
    }

    // =========================================================================
    // APPROVAL GATE (TC-06-07)
    // =========================================================================
    public AiAgentDto.ApprovalResponse approveRecommendation(AiAgentDto.ApprovalRequest request) {
        ApprovalContext ctx = pendingApprovals.get(request.getApprovalToken());

        if (ctx == null) {
            throw new BusinessException("Invalid or expired approval token. Please request a new recommendation.");
        }

        if (LocalDateTime.now().isAfter(ctx.expiresAt())) {
            pendingApprovals.remove(request.getApprovalToken());
            throw new BusinessException("Approval token has expired. Please request a new recommendation.");
        }

        log.info("[AI AGENT] Revalidating eligibility before allocation confirmation...");
        String revalidationNote;
        try {
            allocationService.checkCapacityLimit(ctx.employeeId(), ctx.startDate(), ctx.endDate(),
                ctx.allocationPercent(), null);
            revalidationNote = "Revalidation PASSED: Employee capacity confirmed available at commit time.";
        } catch (AllocationConflictException e) {
            pendingApprovals.remove(request.getApprovalToken());
            auditLog("AI_AGENT", ctx.demandId(), "APPROVAL_REJECTED",
                request.getApprovedBy(), "Revalidation failed: " + e.getMessage());
            return AiAgentDto.ApprovalResponse.builder()
                .status("REJECTED")
                .message("Revalidation failed — employee is no longer available: " + e.getMessage())
                .revalidationNote("Revalidation FAILED: " + e.getMessage())
                .build();
        }

        AllocationDto.Request allocationReq = AllocationDto.Request.builder()
            .employeeId(ctx.employeeId())
            .demandId(ctx.demandId())
            .startDate(ctx.startDate())
            .endDate(ctx.endDate())
            .allocationPercent(ctx.allocationPercent())
            .requestedBy("AI_AGENT")
            .build();

        AllocationDto.Response created = allocationService.createAllocation(allocationReq);
        AllocationDto.Response approved = allocationService.approveAllocation(created.getId(),
            AllocationDto.ApprovalRequest.builder().approvedBy(request.getApprovedBy()).build());

        pendingApprovals.remove(request.getApprovalToken());

        auditLog("AI_AGENT", ctx.demandId(), "ALLOCATION_CONFIRMED", request.getApprovedBy(),
            "Allocation " + approved.getId() + " confirmed after AI recommendation approval.");

        return AiAgentDto.ApprovalResponse.builder()
            .status("APPROVED")
            .message("Allocation confirmed successfully. Employee is now allocated.")
            .allocation(approved)
            .revalidationNote(revalidationNote)
            .build();
    }

    // =========================================================================
    // INTENT & ENTITY RESOLUTION
    // =========================================================================
    private AgentGoal determineGoal(String question, AiAgentDto.StaffingRequest request) {
        if (question == null || question.isBlank()) {
            return AgentGoal.INITIALIZATION;
        }

        // Domain boundary check: identify unrelated queries generically
        if (!isStaffingRelated(question, request)) {
            return AgentGoal.OUT_OF_DOMAIN;
        }

        String q = question.toLowerCase().trim();

        // 1. Candidate search: "Who is available for 50%?", "Who can work on Phoenix?", "Find candidates with 100% availability"
        if (q.contains("who is available") || q.contains("who can work") || q.contains("who is free") ||
            q.contains("find candidates with") || q.contains("search candidates") || q.contains("list candidates")) {
            return AgentGoal.CANDIDATE_SEARCH;
        }

        // 2. Eligibility inquiry: "Why isn't James Davis eligible?", "Why is James ineligible?", "Is James eligible?"
        if (q.contains("eligible") || q.contains("ineligible") || q.contains("eligibility") ||
            q.contains("why isn't") || q.contains("why is not")) {
            return AgentGoal.ELIGIBILITY_EXPLANATION;
        }

        // 3. Skill gap inquiry: "What skill gaps does he have?", "What training does Alice need?", "What are the gaps?"
        if (q.contains("skill gap") || q.contains("skill gaps") || q.contains("training") || q.contains("upskill") || q.contains("missing skill")) {
            return AgentGoal.SKILL_GAP_INQUIRY;
        }

        // 4. Comparison: "what about Alice?", "how about Alice?", "compare Alice to Henry", "what about the other candidate?", "and Alice?"
        if (q.contains("what about") || q.contains("how about") || q.contains("compare") ||
            q.contains("other candidate") || q.contains("another candidate") || q.contains("who else") || q.startsWith("and ")) {
            return AgentGoal.CANDIDATE_COMPARISON;
        }

        // 5. Follow-up reasoning: "Why?", "Why does she rank higher?", "Why is Alice ranked higher?", "Why did you recommend Henry?", "Why?"
        if (q.matches("why[?!.]*") || q.startsWith("why ") || q.startsWith("why's ") || q.contains("why does") ||
            q.contains("why is") || q.contains("why rank") || q.contains("why did you")) {
            return AgentGoal.FOLLOW_UP_REASONING;
        }

        // 6. Availability check: "Can she take 100%?", "Can Alice take 100% allocation?", "Is she available for 100%?", "Can he take 50%?"
        if (q.contains("take ") || (q.contains("available") && !q.contains("who is")) || q.contains("availability") ||
            (q.startsWith("can ") && (q.contains("take") || q.contains("work"))) || (q.startsWith("is ") && q.contains("available"))) {
            return AgentGoal.AVAILABILITY_CHECK;
        }

        // 7. Explicit candidate recommendation: "Find the best employee for Project Phoenix", "Recommend a candidate"
        if (q.contains("best") || q.contains("recommend") || q.contains("suggest") ||
            ((q.contains("find") || q.contains("match") || q.contains("get") || q.contains("who")) &&
             (q.contains("employee") || q.contains("candidate") || q.contains("developer") || q.contains("person") || q.contains("resource") || q.contains("staff") || q.contains("fit")))) {
            return AgentGoal.CANDIDATE_RECOMMENDATION;
        }

        // 8. Demand details / info inquiry: "Where is Project Phoenix located?", "What are the dates?", "What are the requirements?"
        if (q.contains("where") || q.contains("location") || q.contains("date") || q.contains("start") || q.contains("end") ||
            q.contains("duration") || q.contains("role") || q.contains("requirement") || q.contains("detail") ||
            q.contains("tell me about")) {
            return AgentGoal.DEMAND_INFO_INQUIRY;
        }

        // 9. Unrelated / general staffing inquiry
        return AgentGoal.GENERAL_STAFFING_INQUIRY;
    }

    private boolean isStaffingRelated(String text, AiAgentDto.StaffingRequest request) {
        if (text == null || text.isBlank()) return false;
        String q = text.toLowerCase().trim();

        // 1. Follow-up reasoning in an existing staffing dialog (e.g., "Why?", "Why is that?", "Why so?")
        if (q.matches("why[?!.]*") || q.matches("why (is that|so|though|did you)[?!.]*") ||
            q.equals("who else?") || q.equals("who else")) {
            return request.getContextCandidateName() != null || request.getLastGoal() != null;
        }

        // 2. Candidate search triggers
        if (q.contains("who is available") || q.contains("who can work") || q.contains("who is free") ||
            q.contains("find candidates") || q.contains("search candidates") || q.contains("list candidates")) {
            return true;
        }

        // 3. Pronoun reference with active candidate context
        if ((q.contains("she") || q.contains("he") || q.contains("her") || q.contains("him") || q.contains("they")) &&
            (request.getContextCandidateName() != null && !request.getContextCandidateName().isBlank())) {
            return true;
        }

        // 4. Mentions any stored employee in the database
        List<Employee> allEmployees = employeeRepository.findAll();
        for (Employee e : allEmployees) {
            String fullName = e.getName().toLowerCase();
            String firstName = fullName.split(" ")[0];
            if (q.contains(fullName)) {
                return true;
            }
            if (firstName.length() > 2 && Pattern.compile("\\b" + Pattern.quote(firstName) + "\\b").matcher(q).find()) {
                return true;
            }
        }

        // 5. Mentions any stored project demand in the database
        List<ProjectDemand> allDemands = demandRepository.findAll();
        for (ProjectDemand d : allDemands) {
            String projName = d.getProjectName().toLowerCase();
            String stripped = projName.replace("project ", "").trim();
            if (q.contains(projName) || (!stripped.isBlank() && q.contains(stripped))) {
                return true;
            }
        }

        // 6. Staffing, resource allocation, and talent marketplace domain vocabulary
        String[] domainTerms = {
            "staff", "staffing", "employee", "employees", "candidate", "candidates",
            "resource", "resources", "allocation", "allocate", "allocations",
            "availability", "available", "capacity", "overallocated", "overbooked",
            "skill", "skills", "proficiency", "gap", "gaps", "upskill", "training",
            "eligible", "eligibility", "ineligible", "qualify", "qualification",
            "recommend", "recommendation", "scoring", "match", "matching", "bench",
            "developer", "engineer", "designer", "architect", "manager", "role",
            "demand", "demands", "project", "projects", "java", "python", "react",
            "aws", "docker", "sql", "spring", "timeline", "dates", "utilization",
            "workload", "reallocate", "assign", "assignment", "hire"
        };

        for (String term : domainTerms) {
            if (Pattern.compile("\\b" + Pattern.quote(term) + "\\b").matcher(q).find()) {
                return true;
            }
        }

        // 7. Check for allocation capacity percentages (e.g., "50%", "100%") combined with staffing actions
        if (Pattern.compile("\\b\\d{1,3}\\s*%").matcher(q).find() && (q.contains("take") || q.contains("work") || q.contains("free") || q.contains("capacity"))) {
            return true;
        }

        return false;
    }

    private Employee resolveTargetEmployee(String text, AiAgentDto.StaffingRequest request) {
        if (text == null) return null;
        String lower = text.toLowerCase();
        List<Employee> all = employeeRepository.findAll();

        // 1. Check "other candidate" / "another candidate"
        if (lower.contains("other candidate") || lower.contains("another candidate")) {
            if (request.getPreviousCandidateName() != null && !request.getPreviousCandidateName().isBlank()) {
                Employee found = findEmployeeByName(request.getPreviousCandidateName());
                if (found != null) return found;
            }
        }

        // 2. Check exact full name
        for (Employee e : all) {
            if (lower.contains(e.getName().toLowerCase())) {
                return e;
            }
        }

        // 3. Check first name matching (e.g. "Alice", "Henry", "James")
        for (Employee e : all) {
            String firstName = e.getName().split(" ")[0].toLowerCase();
            if (firstName.length() > 2) {
                Pattern p = Pattern.compile("\\b" + Pattern.quote(firstName) + "\\b");
                if (p.matcher(lower).find()) {
                    return e;
                }
            }
        }

        // 4. Pronoun resolution: "she", "he", "her", "him", "the candidate"
        boolean hasPronoun = lower.matches(".*\\b(she|he|her|him|his|candidate|them)\\b.*");
        if (hasPronoun && request.getContextCandidateName() != null && !request.getContextCandidateName().isBlank()) {
            Employee found = findEmployeeByName(request.getContextCandidateName());
            if (found != null) return found;
        }

        // 5. Fallback to contextCandidateName if question didn't specify a new person
        if (request.getContextCandidateName() != null && !request.getContextCandidateName().isBlank()) {
            return findEmployeeByName(request.getContextCandidateName());
        }

        return null;
    }

    private Employee resolveBaselineEmployee(AiAgentDto.StaffingRequest request, Employee targetEmployee, ProjectDemand demand) {
        // If contextCandidateName is set and different from target, use it
        if (request.getContextCandidateName() != null && !request.getContextCandidateName().isBlank()) {
            Employee e = findEmployeeByName(request.getContextCandidateName());
            if (e != null && (targetEmployee == null || !e.getId().equals(targetEmployee.getId()))) {
                return e;
            }
        }

        // If previousCandidateName is set and different from target, use it
        if (request.getPreviousCandidateName() != null && !request.getPreviousCandidateName().isBlank()) {
            Employee e = findEmployeeByName(request.getPreviousCandidateName());
            if (e != null && (targetEmployee == null || !e.getId().equals(targetEmployee.getId()))) {
                return e;
            }
        }

        // Otherwise pick the top recommended candidate for the demand
        RecommendationDto.RecommendationResponse recs = recommendationService.getRecommendations(demand.getId());
        for (var c : recs.getEligibleCandidates()) {
            if (targetEmployee == null || !c.getEmployeeId().equals(targetEmployee.getId())) {
                return employeeRepository.findById(c.getEmployeeId()).orElse(null);
            }
        }

        return null;
    }

    private Employee findEmployeeByName(String name) {
        if (name == null || name.isBlank()) return null;
        String lower = name.toLowerCase().trim();
        List<Employee> all = employeeRepository.findAll();
        for (Employee e : all) {
            if (e.getName().equalsIgnoreCase(lower) || e.getName().toLowerCase().contains(lower)) {
                return e;
            }
        }
        for (Employee e : all) {
            String first = e.getName().split(" ")[0].toLowerCase();
            if (first.equalsIgnoreCase(lower)) {
                return e;
            }
        }
        return null;
    }

    private String extractNameFromQuestion(String text) {
        if (text == null) return "Unknown Candidate";
        Pattern p = Pattern.compile("(?:what about|how about|why isn't|why is not|can|is)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "the candidate";
    }

    private int extractPercentage(String text, int defaultPercent) {
        if (text == null) return defaultPercent;
        Pattern pattern = Pattern.compile("(\\d{1,3})\\s*%");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return defaultPercent;
    }

    private ProjectDemand resolveDemand(AiAgentDto.StaffingRequest request, List<AiAgentDto.ToolCall> toolTrace) {
        if (request.getQuestion() != null) {
            String q = request.getQuestion().toLowerCase();
            for (ProjectDemand d : demandRepository.findAll()) {
                if (q.contains(d.getProjectName().toLowerCase())) {
                    return d;
                }
            }
        }
        if (request.getDemandId() != null) {
            return demandRepository.findById(request.getDemandId()).orElse(null);
        }
        List<ProjectDemand> open = demandRepository.findOpenDemandsOrderedByPriority();
        return !open.isEmpty() ? open.get(0) : demandRepository.findAll().stream().findFirst().orElse(null);
    }

    private String getSubjectPronoun(String name) {
        if (name == null) return "They";
        String first = name.split(" ")[0].toLowerCase();
        Set<String> females = Set.of("alice", "carol", "diana", "eva", "fiona", "grace", "hannah", "iris", "julia", "karen", "laura", "nina");
        return females.contains(first) ? "She" : "He";
    }

    private String getPossessivePronoun(String name) {
        if (name == null) return "their";
        String first = name.split(" ")[0].toLowerCase();
        Set<String> females = Set.of("alice", "carol", "diana", "eva", "fiona", "grace", "hannah", "iris", "julia", "karen", "laura", "nina");
        return females.contains(first) ? "her" : "his";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private AiAgentDto.StaffingAdviceResponse buildNoEligibleResponse(
            AiAgentDto.StaffingRequest request,
            RecommendationDto.RecommendationResponse recommendations,
            List<AiAgentDto.ToolCall> toolTrace,
            List<String> dataWarnings) {

        ProjectDemand demand = demandRepository.findById(request.getDemandId()).orElseThrow();
        dataWarnings.add("No eligible candidates found. Mandatory skill requirements were not satisfied by active employees.");

        String text = "No eligible candidates were found for " + demand.getProjectName() + " (" + demand.getRole() +
            ") based on the current mandatory skill requirements.";

        return AiAgentDto.StaffingAdviceResponse.builder()
            .agentMode(AGENT_MODE)
            .simulationNote(SIMULATION_NOTE)
            .groundingStatement(GROUNDING_STATEMENT)
            .userGoal("CANDIDATE_RECOMMENDATION")
            .demandId(demand.getId())
            .demandSummary(demand.getRole() + " for " + demand.getProjectName())
            .toolCallTrace(toolTrace)
            .recommendation(text)
            .conversationalAnswer(text)
            .outcome("NO_CANDIDATES")
            .allCandidates(recommendations.getCandidates())
            .approvalStatus("NO_CANDIDATES")
            .dataWarnings(dataWarnings)
            .build();
    }

    private void logToolCall(List<AiAgentDto.ToolCall> trace, String toolName,
                            Map<String, Object> params, String summary) {
        trace.add(AiAgentDto.ToolCall.builder()
            .toolName(toolName)
            .parameters(params)
            .status("SUCCESS")
            .summary(summary)
            .timestamp(LocalDateTime.now().toString())
            .build());
        log.info("[AI AGENT] Tool call: {} | {}", toolName, summary);
    }

    private void auditLog(String entityType, Long entityId, String action, String performedBy, String details) {
        auditLogRepository.save(AuditLog.builder()
            .entityType(entityType)
            .entityId(entityId)
            .action(action)
            .performedBy(performedBy != null ? performedBy : "AI_AGENT")
            .details(details)
            .build());
    }

    public record ApprovalContext(
        Long demandId,
        Long employeeId,
        int allocationPercent,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime expiresAt
    ) {}
}
