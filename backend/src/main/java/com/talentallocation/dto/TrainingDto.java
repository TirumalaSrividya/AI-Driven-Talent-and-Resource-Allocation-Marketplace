package com.talentallocation.dto;

import com.talentallocation.domain.TrainingRecommendation;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

public class TrainingDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long employeeId;
        private String employeeName;
        private Long skillId;
        private String skillName;
        private Long demandId;
        private String reason;
        private String suggestedCourse;
        private Integer estimatedDurationDays;
        private TrainingRecommendation.RecommendationStatus status;
        private LocalDateTime createdAt;
    }
}
