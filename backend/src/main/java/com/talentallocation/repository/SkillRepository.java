package com.talentallocation.repository;

import com.talentallocation.domain.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SkillRepository extends JpaRepository<Skill, Long> {

    Optional<Skill> findByNameIgnoreCase(String name);

    List<Skill> findByCategory(String category);

    List<Skill> findByCategoryOrderByName(String category);

    /**
     * SQL Task: Skill gaps grouped by skill category
     */
    @Query(value = """
        SELECT s.category, s.name, s.id,
               COUNT(DISTINCT drs.demand_id) as demand_count,
               COUNT(DISTINCT es.employee_id) as employee_count,
               (COUNT(DISTINCT drs.demand_id) - COUNT(DISTINCT es.employee_id)) as gap_score
        FROM skills s
        LEFT JOIN demand_required_skills drs ON s.id = drs.skill_id
            AND drs.is_mandatory = TRUE
        LEFT JOIN employee_skills es ON s.id = es.skill_id
            AND es.proficiency_level IN ('ADVANCED','EXPERT')
        LEFT JOIN project_demands pd ON drs.demand_id = pd.id
            AND pd.status IN ('OPEN','IN_PROGRESS')
        GROUP BY s.category, s.name, s.id
        HAVING COUNT(DISTINCT drs.demand_id) > 0
        ORDER BY gap_score DESC, s.category
        """, nativeQuery = true)
    List<Object[]> findSkillGapsGroupedByCategory();
}
