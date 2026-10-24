package com.talentallocation.service;

import com.talentallocation.domain.*;
import com.talentallocation.dto.*;
import com.talentallocation.exception.*;
import com.talentallocation.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AllocationService {

    private final AllocationRepository allocationRepository;
    private final EmployeeRepository employeeRepository;
    private final ProjectDemandRepository demandRepository;
    private final AuditLogRepository auditLogRepository;

    private static final int MAX_CAPACITY = 100;

    /**
     * TC-06-07: Confirm allocation after approval → record persisted and availability recalculated.
     * Uses @Transactional to prevent race conditions.
     */
    public AllocationDto.Response createAllocation(AllocationDto.Request request) {
        Employee employee = employeeRepository.findById(request.getEmployeeId())
            .orElseThrow(() -> new ResourceNotFoundException("Employee", request.getEmployeeId()));

        if (employee.getEmploymentStatus() == Employee.EmploymentStatus.INACTIVE) {
            throw new BusinessException("Cannot allocate inactive employee: " + employee.getName());
        }

        ProjectDemand demand = demandRepository.findById(request.getDemandId())
            .orElseThrow(() -> new ResourceNotFoundException("ProjectDemand", request.getDemandId()));

        if (demand.getStatus() == ProjectDemand.DemandStatus.CANCELLED ||
            demand.getStatus() == ProjectDemand.DemandStatus.FULFILLED) {
            throw new BusinessException("Demand is not open for allocation: " + demand.getStatus());
        }

        // Prevent duplicate allocations for the same employee and demand
        boolean alreadyAllocated = allocationRepository.existsByEmployeeIdAndDemandIdAndStatusIn(
            employee.getId(), demand.getId(), List.of(Allocation.AllocationStatus.PENDING, Allocation.AllocationStatus.ACTIVE));
        if (alreadyAllocated) {
            throw new BusinessException(
                "Employee " + employee.getName() + " is already allocated to Demand #" + demand.getId() + ".",
                HttpStatus.CONFLICT, "DUPLICATE_ALLOCATION");
        }

        validateDateRange(request.getStartDate(), request.getEndDate());

        // TC-06-08: Attempt to allocate above capacity → rejected
        checkCapacityLimit(employee.getId(), request.getStartDate(), request.getEndDate(),
            request.getAllocationPercent(), null);

        Allocation allocation = Allocation.builder()
            .employee(employee)
            .demand(demand)
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .allocationPercent(request.getAllocationPercent())
            .status(Allocation.AllocationStatus.PENDING)
            .build();

        Allocation saved = allocationRepository.save(allocation);

        auditLog("ALLOCATION", saved.getId(), "CREATE",
            request.getRequestedBy(),
            "Allocation created PENDING approval for employee " + employee.getName());

        log.info("Allocation {} created (PENDING) for employee {} on demand {}",
            saved.getId(), employee.getName(), demand.getProjectName());

        return toResponse(saved);
    }

    /**
     * Approve an allocation — revalidates eligibility at commit time (AI agent requirement).
     */
    public AllocationDto.Response approveAllocation(Long allocationId, AllocationDto.ApprovalRequest request) {
        Allocation allocation = getAllocationOrThrow(allocationId);

        if (allocation.getStatus() != Allocation.AllocationStatus.PENDING) {
            throw new BusinessException("Allocation is not in PENDING state: " + allocation.getStatus());
        }

        // Revalidate capacity at approval time (recheck at commit)
        checkCapacityLimit(
            allocation.getEmployee().getId(),
            allocation.getStartDate(),
            allocation.getEndDate(),
            allocation.getAllocationPercent(),
            allocation.getId()
        );

        allocation.setStatus(Allocation.AllocationStatus.ACTIVE);
        allocation.setApprovedBy(request.getApprovedBy());
        allocation.setApprovedAt(java.time.LocalDateTime.now());

        // Update demand status if needed
        ProjectDemand demand = allocation.getDemand();
        if (demand.getStatus() == ProjectDemand.DemandStatus.OPEN) {
            demand.setStatus(ProjectDemand.DemandStatus.IN_PROGRESS);
            demandRepository.save(demand);
        }

        Allocation saved = allocationRepository.save(allocation);

        auditLog("ALLOCATION", saved.getId(), "APPROVE",
            request.getApprovedBy(),
            "Allocation approved for employee " + allocation.getEmployee().getName());

        log.info("Allocation {} approved by {}", allocationId, request.getApprovedBy());
        return toResponse(saved);
    }

    /**
     * TC-06-12: Cancel allocation → capacity released and history retained.
     */
    public AllocationDto.Response cancelAllocation(Long allocationId, AllocationDto.CancelRequest request) {
        Allocation allocation = getAllocationOrThrow(allocationId);

        if (allocation.getStatus() == Allocation.AllocationStatus.CANCELLED) {
            throw new BusinessException("Allocation is already cancelled");
        }

        allocation.setStatus(Allocation.AllocationStatus.CANCELLED);
        allocation.setCancelledBy(request.getCancelledBy());
        allocation.setCancelledAt(java.time.LocalDateTime.now());
        allocation.setCancellationReason(request.getCancellationReason());

        Allocation saved = allocationRepository.save(allocation);

        auditLog("ALLOCATION", saved.getId(), "CANCEL",
            request.getCancelledBy(),
            "Allocation cancelled. Reason: " + request.getCancellationReason());

        log.info("Allocation {} cancelled by {}", allocationId, request.getCancelledBy());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AllocationDto.Response> getAllAllocations() {
        return allocationRepository.findAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AllocationDto.Response getAllocation(Long id) {
        return toResponse(getAllocationOrThrow(id));
    }

    /**
     * TC-06-08: Capacity check — rejects allocation if it would exceed MAX_CAPACITY.
     * Uses SUM of all ACTIVE+PENDING allocations in the overlapping date range.
     */
    public void checkCapacityLimit(Long employeeId, LocalDate startDate, LocalDate endDate,
                                    int requestedPercent, Long excludeAllocationId) {
        Integer alreadyAllocated = allocationRepository.sumAllocatedPercentForPeriod(
            employeeId, startDate, endDate, excludeAllocationId);

        int total = (alreadyAllocated != null ? alreadyAllocated : 0) + requestedPercent;

        if (total > MAX_CAPACITY) {
            throw new AllocationConflictException(
                String.format("Allocation would exceed capacity limit. Current: %d%%, Requested: %d%%, " +
                    "Total would be: %d%% (max: %d%%)",
                    alreadyAllocated != null ? alreadyAllocated : 0,
                    requestedPercent, total, MAX_CAPACITY));
        }
    }

    /**
     * TC-06-06: Calculate available percentage for employee in a date range.
     */
    @Transactional(readOnly = true)
    public RecommendationDto.AvailabilityResult getAvailability(Long employeeId,
                                                                 LocalDate startDate,
                                                                 LocalDate endDate,
                                                                 int requestedPercent) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        Integer currentAllocated = allocationRepository.sumAllocatedPercentForPeriod(
            employeeId, startDate, endDate, null);
        int allocated = currentAllocated != null ? currentAllocated : 0;
        int available = MAX_CAPACITY - allocated;

        List<Allocation> conflicts = allocationRepository.findAllocationsInPeriod(
            employeeId, startDate, endDate);

        return RecommendationDto.AvailabilityResult.builder()
            .employeeId(employeeId)
            .employeeName(employee.getName())
            .requestedPercent(requestedPercent)
            .currentAllocatedPercent(allocated)
            .availablePercent(available)
            .fullyAvailable(available >= requestedPercent)
            .partiallyAvailable(available > 0 && available < requestedPercent)
            .conflictingAllocations(conflicts.stream().map(this::toResponse).collect(Collectors.toList()))
            .build();
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException("End date must be on or after start date",
                HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE");
        }
    }

    private Allocation getAllocationOrThrow(Long id) {
        return allocationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Allocation", id));
    }

    private void auditLog(String entityType, Long entityId, String action,
                           String performedBy, String details) {
        auditLogRepository.save(AuditLog.builder()
            .entityType(entityType)
            .entityId(entityId)
            .action(action)
            .performedBy(performedBy)
            .details(details)
            .build());
    }

    public AllocationDto.Response toResponse(Allocation a) {
        return AllocationDto.Response.builder()
            .id(a.getId())
            .employeeId(a.getEmployee().getId())
            .employeeName(a.getEmployee().getName())
            .employeeCode(a.getEmployee().getEmployeeCode())
            .demandId(a.getDemand().getId())
            .projectName(a.getDemand().getProjectName())
            .role(a.getDemand().getRole())
            .startDate(a.getStartDate())
            .endDate(a.getEndDate())
            .allocationPercent(a.getAllocationPercent())
            .status(a.getStatus())
            .approvedBy(a.getApprovedBy())
            .approvedAt(a.getApprovedAt())
            .cancelledBy(a.getCancelledBy())
            .cancelledAt(a.getCancelledAt())
            .cancellationReason(a.getCancellationReason())
            .createdAt(a.getCreatedAt())
            .build();
    }
}
