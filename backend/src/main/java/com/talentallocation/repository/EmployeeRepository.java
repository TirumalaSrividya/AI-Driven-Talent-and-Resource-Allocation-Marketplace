package com.talentallocation.repository;

import com.talentallocation.domain.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmployeeCode(String employeeCode);

    Optional<Employee> findByEmail(String email);

    List<Employee> findByEmploymentStatus(Employee.EmploymentStatus status);

    List<Employee> findByLocation(String location);

    /**
     * Find employees who have a specific skill meeting minimum proficiency,
     * and are ACTIVE (not on leave), excluding those fully allocated in the given period.
     * SQL Task: Available resources for a date range
     */
    @Query("""
        SELECT DISTINCT e FROM Employee e
        JOIN e.skills es
        JOIN es.skill s
        WHERE e.employmentStatus = 'ACTIVE'
        AND s.id = :skillId
        AND es.proficiencyLevel IN :proficiencyLevels
        AND e.id NOT IN (
            SELECT a.employee.id FROM Allocation a
            WHERE a.status IN ('ACTIVE','PENDING')
            AND a.startDate <= :endDate
            AND a.endDate >= :startDate
            AND a.allocationPercent = 100
        )
        """)
    List<Employee> findAvailableBySkillAndDates(
        @Param("skillId") Long skillId,
        @Param("proficiencyLevels") List<String> proficiencyLevels,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * SQL Task: Resources grouped by skill and proficiency
     */
    @Query("""
        SELECT e FROM Employee e
        JOIN FETCH e.skills es
        WHERE e.employmentStatus = 'ACTIVE'
        ORDER BY e.name
        """)
    List<Employee> findAllActiveWithSkills();

    /**
     * SQL Task 2: Resources grouped by skill and proficiency
     */
    @Query(value = """
        SELECT s.name AS skill_name,
               s.category AS skill_category,
               es.proficiency_level AS proficiency_level,
               COUNT(DISTINCT e.id) AS resource_count
        FROM employee_skills es
        JOIN skills s ON es.skill_id = s.id
        JOIN employees e ON es.employee_id = e.id
        WHERE e.employment_status = 'ACTIVE'
        GROUP BY s.name, s.category, es.proficiency_level
        ORDER BY s.category ASC, s.name ASC, es.proficiency_level ASC
        """, nativeQuery = true)
    List<Object[]> findResourcesGroupedBySkillAndProficiency();

    /**
     * SQL Task: Utilization by employee and department
     */
    @Query(value = """
        SELECT e.id, e.name, e.department,
               COALESCE(SUM(a.allocation_percent), 0) as total_allocated
        FROM employees e
        LEFT JOIN allocations a ON e.id = a.employee_id
            AND a.status IN ('ACTIVE', 'PENDING')
            AND a.start_date <= :endDate
            AND a.end_date >= :startDate
        GROUP BY e.id, e.name, e.department
        ORDER BY total_allocated DESC
        """, nativeQuery = true)
    List<Object[]> findUtilizationByEmployeeAndDepartment(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );
}
