package com.talentallocation.repository;

import com.talentallocation.domain.ProjectDemand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ProjectDemandRepository extends JpaRepository<ProjectDemand, Long> {

    List<ProjectDemand> findByStatus(ProjectDemand.DemandStatus status);

    List<ProjectDemand> findByStatusAndPriority(
        ProjectDemand.DemandStatus status,
        ProjectDemand.Priority priority
    );

    /**
     * SQL Task: Open demands by role and priority
     */
    @Query("""
        SELECT pd FROM ProjectDemand pd
        WHERE pd.status IN ('OPEN','IN_PROGRESS')
        ORDER BY
            CASE pd.priority
                WHEN 'CRITICAL' THEN 1
                WHEN 'HIGH' THEN 2
                WHEN 'MEDIUM' THEN 3
                WHEN 'LOW' THEN 4
            END,
            pd.startDate ASC
        """)
    List<ProjectDemand> findOpenDemandsOrderedByPriority();

    /**
     * Find demands filtered by status and/or skill and/or date range
     */
    @Query("""
        SELECT DISTINCT pd FROM ProjectDemand pd
        LEFT JOIN pd.requiredSkills drs
        WHERE (:status IS NULL OR pd.status = :status)
        AND (:skillId IS NULL OR drs.skill.id = :skillId)
        AND (:startDate IS NULL OR pd.endDate >= :startDate)
        AND (:endDate IS NULL OR pd.startDate <= :endDate)
        ORDER BY pd.createdAt DESC
        """)
    List<ProjectDemand> findWithFilters(
        @Param("status") ProjectDemand.DemandStatus status,
        @Param("skillId") Long skillId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * SQL Task: Demands with no eligible candidate
     * Demands that are OPEN but have no ACTIVE or PENDING allocation
     */
    @Query("""
        SELECT pd FROM ProjectDemand pd
        WHERE pd.status = 'OPEN'
        AND pd.id NOT IN (
            SELECT a.demand.id FROM Allocation a
            WHERE a.status IN ('ACTIVE','PENDING')
        )
        ORDER BY pd.priority DESC, pd.startDate ASC
        """)
    List<ProjectDemand> findOpenDemandsWithNoAllocation();
}
