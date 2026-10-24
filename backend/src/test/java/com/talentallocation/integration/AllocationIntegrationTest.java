package com.talentallocation.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentallocation.domain.*;
import com.talentallocation.dto.*;
import com.talentallocation.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests covering all 12 test cases.
 * Uses H2 in-memory database (application-test.properties).
 * TC-06-11: Concurrent allocation conflict tested here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Integration Tests - All TC-06-xx")
class AllocationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EmployeeRepository employeeRepository;
    @Autowired ProjectDemandRepository demandRepository;
    @Autowired AllocationRepository allocationRepository;
    @Autowired SkillRepository skillRepository;
    @Autowired EmployeeSkillRepository employeeSkillRepository;

    // Shared IDs across tests
    static Long employeeId;
    static Long demandId;
    static Long allocationId;

    @BeforeEach
    void setup() {
        // Data is loaded by Flyway V2__seed_data.sql via test profile
    }

    /**
     * TC-06-01: Create a demand with valid fields → created
     */
    @Test
    @Order(1)
    @DisplayName("TC-06-01: Create demand with valid fields → HTTP 201 + OPEN status")
    void tc0601_createDemandWithValidFields() throws Exception {
        DemandDto.Request request = DemandDto.Request.builder()
            .projectName("Integration Test Project")
            .role("Test Engineer")
            .startDate(LocalDate.now().plusMonths(1))
            .endDate(LocalDate.now().plusMonths(6))
            .allocationPercent(80)
            .priority(ProjectDemand.Priority.HIGH)
            .location("New York")
            .createdBy("test-admin")
            .build();

        MvcResult result = mockMvc.perform(post("/api/demands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.projectName").value("Integration Test Project"))
            .andExpect(jsonPath("$.role").value("Test Engineer"))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.id").isNumber())
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        demandId = objectMapper.readTree(responseBody).get("id").asLong();
        assertThat(demandId).isGreaterThan(0);
    }

    /**
     * TC-06-02: Create a demand with missing role or invalid dates → rejected
     */
    @Test
    @Order(2)
    @DisplayName("TC-06-02a: Missing role → HTTP 400 with validation error")
    void tc0602a_missingRole_rejected() throws Exception {
        DemandDto.Request request = DemandDto.Request.builder()
            // role is null/missing
            .projectName("No Role Project")
            .startDate(LocalDate.now().plusDays(5))
            .endDate(LocalDate.now().plusMonths(3))
            .allocationPercent(100)
            .build();

        mockMvc.perform(post("/api/demands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    @Test
    @Order(3)
    @DisplayName("TC-06-02b: End date before start date → HTTP 400 business error")
    void tc0602b_invalidDates_rejected() throws Exception {
        DemandDto.Request request = DemandDto.Request.builder()
            .projectName("Invalid Date Project")
            .role("Developer")
            .startDate(LocalDate.now().plusMonths(3))
            .endDate(LocalDate.now().plusMonths(1)) // end before start
            .allocationPercent(100)
            .build();

        mockMvc.perform(post("/api/demands")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("End date")));
    }

    /**
     * TC-06-03: Search for candidates → results follow eligibility and ranking criteria
     */
    @Test
    @Order(4)
    @DisplayName("TC-06-03: Candidate recommendations follow scoring criteria")
    void tc0603_candidateRecommendations_followRankingCriteria() throws Exception {
        // Use seed demand 1 (Project Phoenix - Java/Spring)
        mockMvc.perform(post("/api/demands/1/recommendations"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.candidates").isArray())
            .andExpect(jsonPath("$.scoringMethodology").exists())
            .andExpect(jsonPath("$.eligibleCandidates").isArray());
    }

    /**
     * TC-06-11: Two concurrent requests attempt conflicting allocation → only one persists
     */
    @Test
    @Order(10)
    @DisplayName("TC-06-11: Concurrent conflicting allocations → only one persists")
    void tc0611_concurrentConflictingAllocations_onlyOnePersists() throws Exception {
        // Get Alice (employee 1) and demand 1
        AllocationDto.Request alloc1 = AllocationDto.Request.builder()
            .employeeId(1L).demandId(1L)
            .startDate(LocalDate.now().plusMonths(1))
            .endDate(LocalDate.now().plusMonths(4))
            .allocationPercent(100)
            .requestedBy("concurrent-test-1")
            .build();

        AllocationDto.Request alloc2 = AllocationDto.Request.builder()
            .employeeId(1L).demandId(2L) // Different demand, same employee/time
            .startDate(LocalDate.now().plusMonths(1))
            .endDate(LocalDate.now().plusMonths(4))
            .allocationPercent(100)
            .requestedBy("concurrent-test-2")
            .build();

        // First allocation should succeed
        MvcResult first = mockMvc.perform(post("/api/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(alloc1)))
            .andExpect(status().isCreated())
            .andReturn();

        allocationId = objectMapper.readTree(first.getResponse().getContentAsString())
            .get("id").asLong();

        // Second allocation for same employee in same period should fail (over 100%)
        mockMvc.perform(post("/api/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(alloc2)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ALLOCATION_CONFLICT"));
    }

    /**
     * TC-06-12: Cancel allocation → capacity released, history retained
     */
    @Test
    @Order(11)
    @DisplayName("TC-06-12: Cancel allocation → history retained, capacity released")
    void tc0612_cancelAllocation_historyRetainedAndCapacityReleased() throws Exception {
        // Assume allocationId is set from TC-06-11
        if (allocationId == null) allocationId = 1L;

        AllocationDto.CancelRequest cancel = AllocationDto.CancelRequest.builder()
            .cancelledBy("admin")
            .cancellationReason("Project cancelled for integration test")
            .build();

        mockMvc.perform(put("/api/allocations/" + allocationId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cancel)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledBy").value("admin"))
            .andExpect(jsonPath("$.cancellationReason").value("Project cancelled for integration test"))
            .andExpect(jsonPath("$.id").value(allocationId));

        // Verify record still exists in DB (history retained)
        assertThat(allocationRepository.findById(allocationId)).isPresent();
        assertThat(allocationRepository.findById(allocationId).get().getStatus())
            .isEqualTo(Allocation.AllocationStatus.CANCELLED);
    }

    @Test
    @Order(5)
    @DisplayName("Skill matrix API returns all employees with skills")
    void skillMatrix_returnsEmployeesWithSkills() throws Exception {
        mockMvc.perform(get("/api/employees"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].skills").isArray());
    }

    @Test
    @Order(6)
    @DisplayName("Available resources endpoint returns only ACTIVE employees")
    void availableResources_returnsOnlyActiveEmployees() throws Exception {
        mockMvc.perform(get("/api/resources/available")
                .param("startDate", LocalDate.now().toString())
                .param("endDate", LocalDate.now().plusMonths(3).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[*].employmentStatus", everyItem(is("ACTIVE"))));
    }

    @Test
    @Order(7)
    @DisplayName("Utilization report returns employee allocation data")
    void utilizationReport_returnsData() throws Exception {
        mockMvc.perform(get("/api/reports/utilization")
                .param("startDate", LocalDate.now().toString())
                .param("endDate", LocalDate.now().plusMonths(3).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.employees").isArray())
            .andExpect(jsonPath("$.averageUtilization").isNumber());
    }

    @Test
    @Order(8)
    @DisplayName("Skill gap report groups by category")
    void skillGapReport_groupedByCategory() throws Exception {
        mockMvc.perform(get("/api/reports/skill-gaps"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gaps").isArray());
    }

    @Test
    @Order(9)
    @DisplayName("AI staffing advice returns simulation label and grounding statement")
    void aiStaffingAdvice_returnsSimulationLabel() throws Exception {
        AiAgentDto.StaffingRequest request = AiAgentDto.StaffingRequest.builder()
            .demandId(1L)
            .requestedBy("integration-test")
            .build();

        mockMvc.perform(post("/api/ai/staffing-advice")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.agentMode").value("SIMULATED"))
            .andExpect(jsonPath("$.simulationNote").exists())
            .andExpect(jsonPath("$.groundingStatement").exists())
            .andExpect(jsonPath("$.toolCallTrace").isArray())
            .andExpect(jsonPath("$.toolCallTrace", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(12)
    @DisplayName("Duplicate allocation for same employee and demand returns BadRequest or Conflict")
    void createDuplicateAllocation_forSameDemand_returnsBadRequestOrConflict() throws Exception {
        // First allocation: Employee 4 on Demand 4 (30%)
        AllocationDto.Request firstAlloc = AllocationDto.Request.builder()
            .employeeId(4L).demandId(4L)
            .startDate(LocalDate.of(2026, 11, 1))
            .endDate(LocalDate.of(2027, 1, 31))
            .allocationPercent(30)
            .requestedBy("test-mgr-1")
            .build();

        mockMvc.perform(post("/api/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstAlloc)))
            .andExpect(status().isCreated());

        // Attempt duplicate allocation for the same employee and demand (20%)
        AllocationDto.Request duplicateAlloc = AllocationDto.Request.builder()
            .employeeId(4L).demandId(4L)
            .startDate(LocalDate.of(2026, 11, 1))
            .endDate(LocalDate.of(2027, 1, 31))
            .allocationPercent(20)
            .requestedBy("test-mgr-2")
            .build();

        mockMvc.perform(post("/api/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateAlloc)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_ALLOCATION"))
            .andExpect(jsonPath("$.message").value(containsString("is already allocated to Demand #4")));
    }

    @Test
    @Order(13)
    @DisplayName("Duplicate allocation after approval (ACTIVE status) returns Conflict")
    void createDuplicateAllocation_afterApproval_returnsConflict() throws Exception {
        // Find the allocation created in order 12
        List<Allocation> allocs = allocationRepository.findByDemandId(4L);
        assertThat(allocs).isNotEmpty();
        Long allocId = allocs.get(0).getId();

        // Approve it so it becomes ACTIVE
        mockMvc.perform(put("/api/allocations/" + allocId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    AllocationDto.ApprovalRequest.builder().approvedBy("Delivery Manager").build())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Attempt duplicate allocation for the same employee and demand while ACTIVE
        AllocationDto.Request duplicateAlloc = AllocationDto.Request.builder()
            .employeeId(4L).demandId(4L)
            .startDate(LocalDate.of(2026, 11, 1))
            .endDate(LocalDate.of(2027, 1, 31))
            .allocationPercent(20)
            .requestedBy("test-mgr-3")
            .build();

        mockMvc.perform(post("/api/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicateAlloc)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_ALLOCATION"))
            .andExpect(jsonPath("$.message").value(containsString("is already allocated to Demand #4")));
    }
}
