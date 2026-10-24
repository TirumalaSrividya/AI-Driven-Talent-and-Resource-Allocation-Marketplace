package com.talentallocation.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class ReportDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UtilizationReport {
        private List<EmployeeUtilization> employees;
        private String reportPeriodStart;
        private String reportPeriodEnd;
        private double averageUtilization;
        private int overAllocatedCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EmployeeUtilization {
        private Long employeeId;
        private String employeeName;
        private String department;
        private int totalAllocatedPercent;
        private int availablePercent;
        private String utilizationStatus; // UNDER, OPTIMAL, OVER
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkillGapReport {
        private List<SkillGapEntry> gaps;
        private int totalGaps;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkillGapEntry {
        private String category;
        private String skillName;
        private Long skillId;
        private long demandCount;
        private long employeeCount;
        private long gapScore;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkillMatrixReport {
        private List<SkillMatrixEntry> entries;
        private int totalGroups;
        private long totalResources;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkillMatrixEntry {
        private String skillName;
        private String skillCategory;
        private String proficiencyLevel;
        private long resourceCount;
    }
}
