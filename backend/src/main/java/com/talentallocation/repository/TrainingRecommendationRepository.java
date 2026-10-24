package com.talentallocation.repository;

import com.talentallocation.domain.TrainingRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrainingRecommendationRepository extends JpaRepository<TrainingRecommendation, Long> {

    List<TrainingRecommendation> findByEmployeeId(Long employeeId);

    List<TrainingRecommendation> findByEmployeeIdAndStatus(
        Long employeeId, TrainingRecommendation.RecommendationStatus status
    );

    List<TrainingRecommendation> findByDemandId(Long demandId);

    boolean existsByEmployeeIdAndSkillIdAndStatus(
        Long employeeId, Long skillId, TrainingRecommendation.RecommendationStatus status
    );
}
