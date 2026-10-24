package com.talentallocation.service;

import com.talentallocation.domain.*;
import com.talentallocation.dto.*;
import com.talentallocation.exception.*;
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
 * Unit tests for AllocationService.
 * Tests TC-06-05 through TC-06-12.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationService Unit Tests")
class AllocationServiceTest {

    @Mock private AllocationRepository allocationRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private ProjectDemandRepository demandRepository;
    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AllocationService allocationService;

    private Employee employee;
    private ProjectDemand demand;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
            .id(1L)
            .employeeCode("EMP001")
            .name("Alice Johnson")
            .email("alice@test.com")
            .location("New York")
            .employmentStatus(Employee.EmploymentStatus.ACTIVE)
            .build();

        demand = ProjectDemand.builder()
            .id(1L)
            .projectName("Project Phoenix")
            .role("Senior Java Developer")
            .startDate(LocalDate.of(2026, 11, 1))
            .endDate(LocalDate.of(2027, 3, 31))
            .allocationPercent(100)
            .status(ProjectDemand.DemandStatus.OPEN)
            .priority(ProjectDemand.Priority.HIGH)
            .build();
    }

    /**
     * TC-06-05: Candidate is allocated for the entire period → unavailable
     */
    @Test
    @DisplayName("TC-06-05: Full allocation conflict is rejected")
    void tc0605_fullAllocationConflict_rejected() {
        AllocationDto.Request request = AllocationDto.Request.builder()
            .employeeId(1L).demandId(1L)
            .startDate(LocalDate.of(2026, 11, 1))
            .endDate(LocalDate.of(2027, 3, 31))
            .allocationPercent(100)
            .build();

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(demandRepository.findById(1L)).thenReturn(Optional.of(demand));
        // Employee is already 100% allocated
        when(allocationRepository.sumAllocatedPercentForPeriod(eq(1L), any(), any(), isNull()))
            .thenReturn(100);

        assertThatThrownBy(() -> allocationService.createAllocation(request))
            .isInstanceOf(AllocationConflictException.class)
            .hasMessageContaining("capacity limit");

        verify(allocationRepository, never()).save(any());
    }

    /**
     * TC-06-06: Candidate is available for only part of the period → partial availability reported
     */
    @Test
    @DisplayName("TC-06-06: Partial availability is accurately reported")
    void tc0606_partialAvailability_reportedAccurately() {
        Allocation existingAlloc = Allocation.builder()
            .id(1L).employee(employee).demand(demand)
            .startDate(LocalDate.of(2026, 11, 1))
            .endDate(LocalDate.of(2026, 12, 31))
            .allocationPercent(80)
            .status(Allocation.AllocationStatus.ACTIVE)
            .build();
        demand.setProjectName("Existing Project");

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(allocationRepository.sumAllocatedPercentForPeriod(eq(1L), any(), any(), isNull()))
            .thenReturn(80);
        when(allocationRepository.findAllocationsInPeriod(eq(1L), any(), any()))
            .thenReturn(List.of(existingAlloc));

        RecommendationDto.AvailabilityResult result = allocationService.getAvailability(
            1L,
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2027, 3, 31),
            100
        );

        assertThat(result.getAvailablePercent()).isEqualTo(20);
        assertThat(result.isFullyAvailable()).isFalse();
        assertThat(result.isPartiallyAvailable()).isTrue();
        assertThat(result.getCurrentAllocatedPercent()).isEqualTo(80);
    }

    /**
     * TC-06-07: Confirm allocation after approval → record persisted and availability recalculated
     */
    @Test
    @DisplayName("TC-06-07: Allocation approved → persisted with ACTIVE status")
    void tc0607_approveAllocation_persistedCorrectly() {
        Allocation pendingAllocation = Allocation.builder()
            .id(2L).employee(employee).demand(demand)
            .startDate(LocalDate.of(2026, 11, 1))
            .endDate(LocalDate.of(2027, 3, 31))
            .allocationPercent(100)
            .status(Allocation.AllocationStatus.PENDING)
            .build();

        demand.setStatus(ProjectDemand.DemandStatus.OPEN);

        when(allocationRepository.findById(2L)).thenReturn(Optional.of(pendingAllocation));
        when(allocationRepository.sumAllocatedPercentForPeriod(eq(1L), any(), any(), eq(2L)))
            .thenReturn(0);
        when(allocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(demandRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AllocationDto.Response response = allocationService.approveAllocation(2L,
            AllocationDto.ApprovalRequest.builder().approvedBy("manager@test.com").build());

        assertThat(response.getStatus()).isEqualTo(Allocation.AllocationStatus.ACTIVE);
        assertThat(response.getApprovedBy()).isEqualTo("manager@test.com");
        verify(auditLogRepository).save(argThat(log ->
            log.getAction().equals("APPROVE")));
    }

    /**
     * TC-06-08: Attempt to allocate above capacity → rejected
     */
    @Test
    @DisplayName("TC-06-08: Over-capacity allocation is rejected")
    void tc0608_overCapacityAllocation_rejected() {
        when(allocationRepository.sumAllocatedPercentForPeriod(eq(1L), any(), any(), isNull()))
            .thenReturn(70);

        assertThatThrownBy(() -> allocationService.checkCapacityLimit(
            1L, LocalDate.of(2026, 11, 1), LocalDate.of(2027, 3, 31), 50, null))
            .isInstanceOf(AllocationConflictException.class)
            .hasMessageContaining("exceed capacity limit")
            .hasMessageContaining("120%");
    }

    /**
     * TC-06-12: Cancel allocation → capacity released and history retained
     */
    @Test
    @DisplayName("TC-06-12: Cancel allocation → history retained, capacity released")
    void tc0612_cancelAllocation_historyRetained() {
        Allocation activeAllocation = Allocation.builder()
            .id(3L).employee(employee).demand(demand)
            .startDate(LocalDate.of(2026, 11, 1))
            .endDate(LocalDate.of(2027, 3, 31))
            .allocationPercent(100)
            .status(Allocation.AllocationStatus.ACTIVE)
            .build();

        when(allocationRepository.findById(3L)).thenReturn(Optional.of(activeAllocation));
        when(allocationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AllocationDto.Response response = allocationService.cancelAllocation(3L,
            AllocationDto.CancelRequest.builder()
                .cancelledBy("admin")
                .cancellationReason("Project cancelled")
                .build());

        assertThat(response.getStatus()).isEqualTo(Allocation.AllocationStatus.CANCELLED);
        assertThat(response.getCancelledBy()).isEqualTo("admin");
        assertThat(response.getCancellationReason()).isEqualTo("Project cancelled");
        assertThat(response.getCancelledAt()).isNotNull();
        // History is retained — record still exists with CANCELLED status
        verify(allocationRepository, never()).delete(any());
        verify(auditLogRepository).save(argThat(log -> log.getAction().equals("CANCEL")));
    }

    @Test
    @DisplayName("Cannot cancel an already-cancelled allocation")
    void cancelAlreadyCancelled_throwsException() {
        Allocation cancelled = Allocation.builder()
            .id(4L).employee(employee).demand(demand)
            .status(Allocation.AllocationStatus.CANCELLED)
            .build();

        when(allocationRepository.findById(4L)).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> allocationService.cancelAllocation(4L,
            AllocationDto.CancelRequest.builder().cancelledBy("admin").build()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("already cancelled");
    }

    @Test
    @DisplayName("Cannot allocate INACTIVE employee")
    void inactiveEmployee_throwsException() {
        employee.setEmploymentStatus(Employee.EmploymentStatus.INACTIVE);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        AllocationDto.Request request = AllocationDto.Request.builder()
            .employeeId(1L).demandId(1L)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusMonths(3))
            .allocationPercent(50)
            .build();

        assertThatThrownBy(() -> allocationService.createAllocation(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("inactive employee");
    }

    @Test
    @DisplayName("Duplicate allocation for same employee and demand is rejected")
    void duplicateAllocation_sameEmployeeAndDemand_rejected() {
        AllocationDto.Request request = AllocationDto.Request.builder()
            .employeeId(1L).demandId(1L)
            .startDate(LocalDate.of(2026, 11, 1))
            .endDate(LocalDate.of(2027, 3, 31))
            .allocationPercent(50)
            .build();

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(demandRepository.findById(1L)).thenReturn(Optional.of(demand));
        when(allocationRepository.existsByEmployeeIdAndDemandIdAndStatusIn(eq(1L), eq(1L), anyCollection()))
            .thenReturn(true);

        assertThatThrownBy(() -> allocationService.createAllocation(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("is already allocated to Demand #1");

        verify(allocationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cancelled allocation allows reallocation for same employee and demand")
    void cancelledAllocation_reallocationAllowed() {
        AllocationDto.Request request = AllocationDto.Request.builder()
            .employeeId(1L).demandId(1L)
            .startDate(LocalDate.of(2026, 11, 1))
            .endDate(LocalDate.of(2027, 3, 31))
            .allocationPercent(50)
            .build();

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(demandRepository.findById(1L)).thenReturn(Optional.of(demand));
        // No active or pending allocation exists (previous was cancelled)
        when(allocationRepository.existsByEmployeeIdAndDemandIdAndStatusIn(eq(1L), eq(1L), anyCollection()))
            .thenReturn(false);
        when(allocationRepository.sumAllocatedPercentForPeriod(eq(1L), any(), any(), isNull()))
            .thenReturn(0);
        when(allocationRepository.save(any())).thenAnswer(inv -> {
            Allocation a = inv.getArgument(0);
            a.setId(99L);
            return a;
        });

        AllocationDto.Response response = allocationService.createAllocation(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(99L);
        assertThat(response.getStatus()).isEqualTo(Allocation.AllocationStatus.PENDING);
        verify(allocationRepository).save(any());
    }

    @Test
    @DisplayName("Different employee for same demand is allowed")
    void differentEmployee_sameDemand_allowed() {
        Employee bob = Employee.builder()
            .id(2L)
            .employeeCode("EMP002")
            .name("Bob Smith")
            .employmentStatus(Employee.EmploymentStatus.ACTIVE)
            .build();

        AllocationDto.Request request = AllocationDto.Request.builder()
            .employeeId(2L).demandId(1L)
            .startDate(LocalDate.of(2026, 11, 1))
            .endDate(LocalDate.of(2027, 3, 31))
            .allocationPercent(50)
            .build();

        when(employeeRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(demandRepository.findById(1L)).thenReturn(Optional.of(demand));
        when(allocationRepository.existsByEmployeeIdAndDemandIdAndStatusIn(eq(2L), eq(1L), anyCollection()))
            .thenReturn(false);
        when(allocationRepository.sumAllocatedPercentForPeriod(eq(2L), any(), any(), isNull()))
            .thenReturn(0);
        when(allocationRepository.save(any())).thenAnswer(inv -> {
            Allocation a = inv.getArgument(0);
            a.setId(100L);
            return a;
        });

        AllocationDto.Response response = allocationService.createAllocation(request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getEmployeeName()).isEqualTo("Bob Smith");
        verify(allocationRepository).save(any());
    }
}
