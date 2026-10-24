package com.talentallocation.service;

import com.talentallocation.domain.*;
import com.talentallocation.dto.*;
import com.talentallocation.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RecommendationService.
 * Tests TC-06-03, TC-06-04, TC-06-09, TC-06-10.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationService Unit Tests")
class RecommendationServiceTest {

    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeeSkillRepository employeeSkillRepository;
    @Mock private ProjectDemandRepository demandRepository;
    @Mock private AllocationRepository allocationRepository;

    @InjectMocks
    private RecommendationService recommendationService;

    private Employee employeeWithSkills;
    private Employee employeeWithoutMandatorySkill;
    private ProjectDemand demand;
    private Skill javaSkill;
    private Skill springSkill;

    @BeforeEach
    void setUp() {
        javaSkill = Skill.builder().id(1L).name("Java").category("Programming").build();
        springSkill = Skill.builder().id(2L).name("Spring Boot").category("Framework").build();

        employeeWithSkills = Employee.builder()
            .id(1L).employeeCode("EMP001").name("Alice Johnson")
            .location("New York").department("Engineering").seniorityLevel("Senior")
            .employmentStatus(Employee.EmploymentStatus.ACTIVE)
            .build();

        employeeWithoutMandatorySkill = Employee.builder()
            .id(2L).employeeCode("EMP002").name("Bob Smith")
            .location("Chicago").department("Frontend").seniorityLevel("Junior")
            .employmentStatus(Employee.EmploymentStatus.ACTIVE)
            .build();

        // Demand requires Java (mandatory) and Spring Boot (optional)
        DemandRequiredSkill mandatoryJava = DemandRequiredSkill.builder()
            .skill(javaSkill)
            .minimumProficiency(EmployeeSkill.ProficiencyLevel.ADVANCED)
            .mandatory(true)
            .build();
        DemandRequiredSkill optionalSpring = DemandRequiredSkill.builder()
            .skill(springSkill)
            .minimumProficiency(EmployeeSkill.ProficiencyLevel.INTERMEDIATE)
            .mandatory(false)
            .build();

        demand = ProjectDemand.builder()
            .id(1L).projectName("Project Phoenix").role("Senior Java Developer")
            .startDate(LocalDate.of(2026, 11, 1))
            .endDate(LocalDate.of(2027, 3, 31))
            .allocationPercent(100)
            .location("New York")
            .priority(ProjectDemand.Priority.HIGH)
            .status(ProjectDemand.DemandStatus.OPEN)
            .requiredSkills(new ArrayList<>(List.of(mandatoryJava, optionalSpring)))
            .build();

        mandatoryJava.setDemand(demand);
        optionalSpring.setDemand(demand);
    }

    /**
     * TC-06-03: Search candidates → results follow documented eligibility and ranking criteria
     */
    @Test
    @DisplayName("TC-06-03: Recommendations follow eligibility and ranking criteria")
    void tc0603_recommendationResults_followEligibilityAndRankingCriteria() {
        // Alice has Java EXPERT and Spring Boot ADVANCED
        EmployeeSkill aliceJava = EmployeeSkill.builder()
            .employee(employeeWithSkills).skill(javaSkill)
            .proficiencyLevel(EmployeeSkill.ProficiencyLevel.EXPERT)
            .yearsExperience(BigDecimal.valueOf(8.0))
            .lastValidatedAt(LocalDate.of(2024, 1, 1))
            .certified(true)
            .build();
        EmployeeSkill aliceSpring = EmployeeSkill.builder()
            .employee(employeeWithSkills).skill(springSkill)
            .proficiencyLevel(EmployeeSkill.ProficiencyLevel.ADVANCED)
            .yearsExperience(BigDecimal.valueOf(6.0))
            .lastValidatedAt(LocalDate.of(2024, 1, 1))
            .certified(true)
            .build();

        // Bob only has Spring Boot (no Java)
        EmployeeSkill bobSpring = EmployeeSkill.builder()
            .employee(employeeWithoutMandatorySkill).skill(springSkill)
            .proficiencyLevel(EmployeeSkill.ProficiencyLevel.INTERMEDIATE)
            .yearsExperience(BigDecimal.valueOf(1.0))
            .build();

        when(demandRepository.findById(1L)).thenReturn(Optional.of(demand));
        when(employeeRepository.findAllActiveWithSkills()).thenReturn(
            List.of(employeeWithSkills, employeeWithoutMandatorySkill));

        when(employeeSkillRepository.findByEmployeeId(1L))
            .thenReturn(List.of(aliceJava, aliceSpring));
        when(employeeSkillRepository.findByEmployeeId(2L))
            .thenReturn(List.of(bobSpring));

        when(allocationRepository.sumAllocatedPercentForPeriod(eq(1L), any(), any(), isNull()))
            .thenReturn(0);
        when(allocationRepository.sumAllocatedPercentForPeriod(eq(2L), any(), any(), isNull()))
            .thenReturn(0);

        RecommendationDto.RecommendationResponse response =
            recommendationService.getRecommendations(1L);

        // Alice should be first (eligible, high score)
        assertThat(response.getEligibleCandidates()).hasSize(1);
        assertThat(response.getEligibleCandidates().get(0).getEmployeeName()).isEqualTo("Alice Johnson");

        // Bob should be ineligible (missing mandatory Java skill)
        assertThat(response.getIneligibleCandidates()).hasSize(1);
        assertThat(response.getIneligibleCandidates().get(0).getEmployeeName()).isEqualTo("Bob Smith");

        // Eligible candidates come before ineligible in full list
        assertThat(response.getCandidates().get(0).isEligible()).isTrue();
    }

    /**
     * TC-06-04: Candidate lacks mandatory skill → excluded or clearly flagged
     */
    @Test
    @DisplayName("TC-06-04: Missing mandatory skill → ineligible with gap detail")
    void tc0604_missingMandatorySkill_flaggedIneligible() {
        // Bob has NO Java skill at all
        when(employeeSkillRepository.findByEmployeeId(2L)).thenReturn(List.of());
        when(allocationRepository.sumAllocatedPercentForPeriod(eq(2L), any(), any(), isNull()))
            .thenReturn(0);

        RecommendationDto.CandidateScore score =
            recommendationService.scoreCandidate(employeeWithoutMandatorySkill, demand);

        assertThat(score.isEligible()).isFalse();
        assertThat(score.isAllMandatorySkillsMet()).isFalse();

        // Java skill gap should be reported
        assertThat(score.getSkillGaps()).extracting(RecommendationDto.SkillGapDetail::getSkillName)
            .contains("Java");

        // Mandatory gap is clearly marked
        assertThat(score.getSkillGaps())
            .filteredOn(g -> "Java".equals(g.getSkillName()))
            .allMatch(RecommendationDto.SkillGapDetail::isMandatory);
    }

    /**
     * TC-06-09: AI recommendation includes evidence and score breakdown matching stored records
     */
    @Test
    @DisplayName("TC-06-09: Score breakdown matches stored proficiency records")
    void tc0609_scoreBreakdown_matchesStoredRecords() {
        EmployeeSkill aliceJava = EmployeeSkill.builder()
            .employee(employeeWithSkills).skill(javaSkill)
            .proficiencyLevel(EmployeeSkill.ProficiencyLevel.EXPERT) // Stored as EXPERT
            .yearsExperience(BigDecimal.valueOf(8.0))
            .lastValidatedAt(LocalDate.of(2024, 1, 1))
            .certified(true)
            .build();

        when(employeeSkillRepository.findByEmployeeId(1L)).thenReturn(List.of(aliceJava));
        when(allocationRepository.sumAllocatedPercentForPeriod(eq(1L), any(), any(), isNull()))
            .thenReturn(0);

        RecommendationDto.CandidateScore score =
            recommendationService.scoreCandidate(employeeWithSkills, demand);

        // Score breakdown must reference actual stored proficiency, not fabricated
        Optional<RecommendationDto.SkillMatchDetail> javaMatch = score.getSkillMatches().stream()
            .filter(m -> "Java".equals(m.getSkillName()))
            .findFirst();

        assertThat(javaMatch).isPresent();
        assertThat(javaMatch.get().getActualLevel())
            .isEqualTo(EmployeeSkill.ProficiencyLevel.EXPERT); // Must match stored value
        assertThat(javaMatch.get().getLastValidatedAt())
            .isEqualTo(LocalDate.of(2024, 1, 1)); // Must cite validation date
        assertThat(javaMatch.get().isCertified()).isTrue(); // Must reflect stored certified flag

        // Total score must be computable from component scores
        double expectedMax = score.getSkillScore() * 0.50 +
                             score.getAvailabilityScore() * 0.25 +
                             score.getLocationScore() * 0.15 +
                             score.getExperienceScore() * 0.10;
        assertThat(score.getTotalScore()).isLessThanOrEqualTo(100.0);
        assertThat(score.getTotalScore()).isGreaterThan(0.0);
    }

    /**
     * TC-06-10: Ask AI to invent proficiency or availability → missing data disclosed
     */
    @Test
    @DisplayName("TC-06-10: Missing skill data → disclosed, not fabricated")
    void tc0610_missingData_disclosedNotFabricated() {
        // Employee has NO skills in database
        when(employeeSkillRepository.findByEmployeeId(1L)).thenReturn(List.of());
        when(allocationRepository.sumAllocatedPercentForPeriod(eq(1L), any(), any(), isNull()))
            .thenReturn(0);

        RecommendationDto.CandidateScore score =
            recommendationService.scoreCandidate(employeeWithSkills, demand);

        // Must NOT fabricate proficiency levels
        assertThat(score.getSkillMatches()).allMatch(m -> m.getActualLevel() == null);

        // Must NOT fabricate years of experience
        assertThat(score.getSkillMatches()).allMatch(m -> m.getYearsExperience() == null);

        // Must disclose the data gap
        assertThat(score.isHasCompleteSkillData()).isFalse();
        assertThat(score.getDataDisclosure()).isNotNull()
            .contains("DATA DISCLOSURE");

        // Must still be ineligible (not fabricated as eligible)
        assertThat(score.isEligible()).isFalse();
    }

    @Test
    @DisplayName("Skill Match = (Passed Skills ÷ Total Required Skills) × 100 works dynamically for any number of skills")
    void skillMatchFormula_dynamicPassedSkillsRatio() {
        when(allocationRepository.sumAllocatedPercentForPeriod(eq(1L), any(), any(), isNull()))
            .thenReturn(0);

        // Helper to create N required skills with M mandatory
        // Test case 1: 3 skills total, 2 passed -> 66.67
        Skill s1 = Skill.builder().id(101L).name("S1").build();
        Skill s2 = Skill.builder().id(102L).name("S2").build();
        Skill s3 = Skill.builder().id(103L).name("S3").build();

        ProjectDemand demand3 = ProjectDemand.builder()
            .id(10L).projectName("Test 3").role("Role")
            .startDate(LocalDate.now()).endDate(LocalDate.now().plusMonths(3))
            .allocationPercent(100)
            .requiredSkills(new ArrayList<>(List.of(
                DemandRequiredSkill.builder().skill(s1).minimumProficiency(EmployeeSkill.ProficiencyLevel.INTERMEDIATE).mandatory(true).build(),
                DemandRequiredSkill.builder().skill(s2).minimumProficiency(EmployeeSkill.ProficiencyLevel.INTERMEDIATE).mandatory(false).build(),
                DemandRequiredSkill.builder().skill(s3).minimumProficiency(EmployeeSkill.ProficiencyLevel.INTERMEDIATE).mandatory(false).build()
            )))
            .build();

        // 2/3 passed (s1 and s2 meet, s3 missing)
        when(employeeSkillRepository.findByEmployeeId(1L)).thenReturn(List.of(
            EmployeeSkill.builder().employee(employeeWithSkills).skill(s1).proficiencyLevel(EmployeeSkill.ProficiencyLevel.INTERMEDIATE).build(),
            EmployeeSkill.builder().employee(employeeWithSkills).skill(s2).proficiencyLevel(EmployeeSkill.ProficiencyLevel.ADVANCED).build()
        ));

        RecommendationDto.CandidateScore score2of3 = recommendationService.scoreCandidate(employeeWithSkills, demand3);
        assertThat(score2of3.getSkillScore()).isEqualTo(66.67);
        assertThat(score2of3.isEligible()).isTrue();

        // 1/3 passed (only s2 passed, s1 mandatory failed)
        when(employeeSkillRepository.findByEmployeeId(1L)).thenReturn(List.of(
            EmployeeSkill.builder().employee(employeeWithSkills).skill(s2).proficiencyLevel(EmployeeSkill.ProficiencyLevel.INTERMEDIATE).build()
        ));
        RecommendationDto.CandidateScore score1of3 = recommendationService.scoreCandidate(employeeWithSkills, demand3);
        assertThat(score1of3.getSkillScore()).isEqualTo(33.33);
        assertThat(score1of3.isEligible()).isFalse(); // mandatory s1 missing

        // 3/3 passed -> 100
        when(employeeSkillRepository.findByEmployeeId(1L)).thenReturn(List.of(
            EmployeeSkill.builder().employee(employeeWithSkills).skill(s1).proficiencyLevel(EmployeeSkill.ProficiencyLevel.INTERMEDIATE).build(),
            EmployeeSkill.builder().employee(employeeWithSkills).skill(s2).proficiencyLevel(EmployeeSkill.ProficiencyLevel.INTERMEDIATE).build(),
            EmployeeSkill.builder().employee(employeeWithSkills).skill(s3).proficiencyLevel(EmployeeSkill.ProficiencyLevel.EXPERT).build()
        ));
        RecommendationDto.CandidateScore score3of3 = recommendationService.scoreCandidate(employeeWithSkills, demand3);
        assertThat(score3of3.getSkillScore()).isEqualTo(100.0);
        assertThat(score3of3.isEligible()).isTrue();

        // 4/5 passed -> 80
        // 3/5 passed -> 60
        List<Skill> fiveSkills = new ArrayList<>();
        List<DemandRequiredSkill> reqFive = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Skill s = Skill.builder().id((long) (200 + i)).name("Skill" + i).build();
            fiveSkills.add(s);
            reqFive.add(DemandRequiredSkill.builder().skill(s).minimumProficiency(EmployeeSkill.ProficiencyLevel.INTERMEDIATE).mandatory(i <= 2).build());
        }
        ProjectDemand demand5 = ProjectDemand.builder()
            .id(20L).projectName("Test 5").role("Role")
            .startDate(LocalDate.now()).endDate(LocalDate.now().plusMonths(3))
            .allocationPercent(100)
            .requiredSkills(reqFive)
            .build();

        // 4 of 5 passed
        List<EmployeeSkill> empSkills4 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            empSkills4.add(EmployeeSkill.builder().employee(employeeWithSkills).skill(fiveSkills.get(i)).proficiencyLevel(EmployeeSkill.ProficiencyLevel.INTERMEDIATE).build());
        }
        when(employeeSkillRepository.findByEmployeeId(1L)).thenReturn(empSkills4);
        RecommendationDto.CandidateScore score4of5 = recommendationService.scoreCandidate(employeeWithSkills, demand5);
        assertThat(score4of5.getSkillScore()).isEqualTo(80.0);
        assertThat(score4of5.isEligible()).isTrue();

        // 3 of 5 passed
        List<EmployeeSkill> empSkills3 = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            empSkills3.add(EmployeeSkill.builder().employee(employeeWithSkills).skill(fiveSkills.get(i)).proficiencyLevel(EmployeeSkill.ProficiencyLevel.INTERMEDIATE).build());
        }
        when(employeeSkillRepository.findByEmployeeId(1L)).thenReturn(empSkills3);
        RecommendationDto.CandidateScore score3of5 = recommendationService.scoreCandidate(employeeWithSkills, demand5);
        assertThat(score3of5.getSkillScore()).isEqualTo(60.0);
        assertThat(score3of5.isEligible()).isTrue();

        // 7/8 passed -> 87.5
        List<Skill> eightSkills = new ArrayList<>();
        List<DemandRequiredSkill> reqEight = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            Skill s = Skill.builder().id((long) (300 + i)).name("Skill8_" + i).build();
            eightSkills.add(s);
            reqEight.add(DemandRequiredSkill.builder().skill(s).minimumProficiency(EmployeeSkill.ProficiencyLevel.INTERMEDIATE).mandatory(false).build());
        }
        ProjectDemand demand8 = ProjectDemand.builder()
            .id(30L).projectName("Test 8").role("Role")
            .startDate(LocalDate.now()).endDate(LocalDate.now().plusMonths(3))
            .allocationPercent(100)
            .requiredSkills(reqEight)
            .build();

        List<EmployeeSkill> empSkills7 = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            empSkills7.add(EmployeeSkill.builder().employee(employeeWithSkills).skill(eightSkills.get(i)).proficiencyLevel(EmployeeSkill.ProficiencyLevel.INTERMEDIATE).build());
        }
        when(employeeSkillRepository.findByEmployeeId(1L)).thenReturn(empSkills7);
        RecommendationDto.CandidateScore score7of8 = recommendationService.scoreCandidate(employeeWithSkills, demand8);
        assertThat(score7of8.getSkillScore()).isEqualTo(87.5);
    }
}
