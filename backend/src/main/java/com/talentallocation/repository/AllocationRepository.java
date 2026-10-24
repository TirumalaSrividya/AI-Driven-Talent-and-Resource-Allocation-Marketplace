package com.talentallocation.repository;

import com.talentallocation.domain.Allocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface AllocationRepository extends JpaRepository<Allocation, Long> {

    List<Allocation> findByEmployeeId(Long employeeId);

    List<Allocation> findByDemandId(Long demandId);

    List<Allocation> findByStatus(Allocation.AllocationStatus status);

    boolean existsByEmployeeIdAndDemandIdAndStatusIn(
        Long employeeId, Long demandId, Collection<Allocation.AllocationStatus> statuses);

    /**
     * Check for overlapping allocations for an employee.
     * Includes ACTIVE and PENDING to prevent race conditions.
     * TC-06-05, TC-06-11: Conflict detection
     */
    @Query("""
        SELECT a FROM Allocation a
        WHERE a.employee.id = :employeeId
        AND a.status IN ('ACTIVE','PENDING')
        AND a.startDate <= :endDate
        AND a.endDate >= :startDate
        AND (:excludeId IS NULL OR a.id != :excludeId)
        """)
    List<Allocation> findConflictingAllocations(
        @Param("employeeId") Long employeeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("excludeId") Long excludeId
    );

    /**
     * Calculate total allocated percentage for an employee in a date range.
     * Used to enforce the capacity limit.
     */
    @Query("""
        SELECT COALESCE(SUM(a.allocationPercent), 0)
        FROM Allocation a
        WHERE a.employee.id = :employeeId
        AND a.status IN ('ACTIVE','PENDING')
        AND a.startDate <= :endDate
        AND a.endDate >= :startDate
        AND (:excludeId IS NULL OR a.id != :excludeId)
        """)
    Integer sumAllocatedPercentForPeriod(
        @Param("employeeId") Long employeeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("excludeId") Long excludeId
    );

    /**
     * TC-06-06: Find allocations covering part of a requested period
     */
    @Query("""
        SELECT a FROM Allocation a
        WHERE a.employee.id = :employeeId
        AND a.status IN ('ACTIVE','PENDING')
        AND a.startDate <= :endDate
        AND a.endDate >= :startDate
        ORDER BY a.startDate ASC
        """)
    List<Allocation> findAllocationsInPeriod(
        @Param("employeeId") Long employeeId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find all active allocations for utilization report
     */
    @Query("""
        SELECT a FROM Allocation a
        JOIN FETCH a.employee
        JOIN FETCH a.demand
        WHERE a.status IN ('ACTIVE','PENDING')
        AND a.startDate <= :endDate
        AND a.endDate >= :startDate
        """)
    List<Allocation> findActiveAllocationsInPeriod(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
