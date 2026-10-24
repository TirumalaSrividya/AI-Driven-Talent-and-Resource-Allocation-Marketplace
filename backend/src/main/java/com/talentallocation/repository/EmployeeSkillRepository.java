package com.talentallocation.repository;

import com.talentallocation.domain.EmployeeSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, Long> {

    List<EmployeeSkill> findByEmployeeId(Long employeeId);

    Optional<EmployeeSkill> findByEmployeeIdAndSkillId(Long employeeId, Long skillId);

    boolean existsByEmployeeIdAndSkillId(Long employeeId, Long skillId);

    /**
     * Get all skills for a list of employees (for bulk scoring)
     */
    @Query("SELECT es FROM EmployeeSkill es JOIN FETCH es.skill WHERE es.employee.id IN :employeeIds")
    List<EmployeeSkill> findByEmployeeIdIn(@Param("employeeIds") List<Long> employeeIds);
}
