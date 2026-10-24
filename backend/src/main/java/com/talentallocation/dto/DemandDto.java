package com.talentallocation.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.talentallocation.domain.ProjectDemand;
import com.talentallocation.domain.EmployeeSkill;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class DemandDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotBlank(message = "Project name is required")
        @Size(max = 200)
        private String projectName;

        @NotBlank(message = "Role is required")
        @Size(max = 100)
        private String role;

        private String description;

        @NotNull(message = "Start date is required")
        private LocalDate startDate;

        @NotNull(message = "End date is required")
        private LocalDate endDate;

        private String location;

        @NotNull(message = "Allocation percent is required")
        @Min(value = 1, message = "Allocation must be at least 1%")
        @Max(value = 100, message = "Allocation cannot exceed 100%")
        private Integer allocationPercent;

        private ProjectDemand.Priority priority;
        private String createdBy;
        private List<RequiredSkillRequest> requiredSkills;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RequiredSkillRequest {
        @NotNull
        private Long skillId;
        private EmployeeSkill.ProficiencyLevel minimumProficiency;

        @JsonProperty("mandatory")
        @JsonAlias("isMandatory")
        private boolean mandatory;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String projectName;
        private String role;
        private String description;
        private LocalDate startDate;
        private LocalDate endDate;
        private String location;
        private Integer allocationPercent;
        private ProjectDemand.Priority priority;
        private ProjectDemand.DemandStatus status;
        private String createdBy;
        private LocalDateTime createdAt;
        private List<RequiredSkillResponse> requiredSkills;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RequiredSkillResponse {
        private Long skillId;
        private String skillName;
        private String skillCategory;
        private EmployeeSkill.ProficiencyLevel minimumProficiency;

        @JsonProperty("mandatory")
        @JsonAlias("isMandatory")
        private boolean mandatory;
    }
}
