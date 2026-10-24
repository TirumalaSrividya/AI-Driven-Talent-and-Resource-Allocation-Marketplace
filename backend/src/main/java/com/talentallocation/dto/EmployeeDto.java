package com.talentallocation.dto;

import com.talentallocation.domain.Employee;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public class EmployeeDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotBlank(message = "Employee code is required")
        @Size(max = 20)
        private String employeeCode;

        @NotBlank(message = "Name is required")
        @Size(max = 200)
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Valid email is required")
        private String email;

        @NotBlank(message = "Location is required")
        private String location;

        private String department;
        private String seniorityLevel;
        private Employee.EmploymentStatus employmentStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String employeeCode;
        private String name;
        private String email;
        private String location;
        private Employee.EmploymentStatus employmentStatus;
        private String department;
        private String seniorityLevel;
        private LocalDateTime createdAt;
        private List<SkillDto.EmployeeSkillResponse> skills;
        private Integer currentAllocationPercent;
    }
}
