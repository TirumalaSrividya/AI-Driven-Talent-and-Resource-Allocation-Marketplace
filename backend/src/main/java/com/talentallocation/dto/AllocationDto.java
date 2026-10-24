package com.talentallocation.dto;

import com.talentallocation.domain.Allocation;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class AllocationDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        @NotNull(message = "Employee ID is required")
        private Long employeeId;

        @NotNull(message = "Demand ID is required")
        private Long demandId;

        @NotNull(message = "Start date is required")
        private LocalDate startDate;

        @NotNull(message = "End date is required")
        private LocalDate endDate;

        @NotNull(message = "Allocation percent is required")
        @Min(1)
        @Max(100)
        private Integer allocationPercent;

        private String requestedBy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApprovalRequest {
        @NotBlank(message = "Approved by is required")
        private String approvedBy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CancelRequest {
        @NotBlank(message = "Cancelled by is required")
        private String cancelledBy;

        private String cancellationReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long employeeId;
        private String employeeName;
        private String employeeCode;
        private Long demandId;
        private String projectName;
        private String role;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer allocationPercent;
        private Allocation.AllocationStatus status;
        private String approvedBy;
        private LocalDateTime approvedAt;
        private String cancelledBy;
        private LocalDateTime cancelledAt;
        private String cancellationReason;
        private LocalDateTime createdAt;
    }
}
