package com.talentallocation.dto;

import com.talentallocation.domain.EmployeeSkill;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SkillDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotBlank(message = "Skill name is required")
        @Size(max = 100)
        private String name;

        @NotBlank(message = "Category is required")
        private String category;

        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String name;
        private String category;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AddSkillRequest {
        @NotNull(message = "Skill ID is required")
        private Long skillId;

        @NotNull(message = "Proficiency level is required")
        private EmployeeSkill.ProficiencyLevel proficiencyLevel;

        @DecimalMin("0.0")
        @DecimalMax("50.0")
        private BigDecimal yearsExperience;

        private LocalDate lastValidatedAt;
        private boolean certified;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmployeeSkillResponse {
        private Long id;
        private Long skillId;
        private String skillName;
        private String skillCategory;
        private EmployeeSkill.ProficiencyLevel proficiencyLevel;
        private BigDecimal yearsExperience;
        private LocalDate lastValidatedAt;
        private boolean certified;
    }
}
