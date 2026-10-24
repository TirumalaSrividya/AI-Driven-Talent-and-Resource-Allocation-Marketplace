package com.talentallocation.service;

import com.talentallocation.domain.*;
import com.talentallocation.dto.*;
import com.talentallocation.exception.*;
import com.talentallocation.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ReportService {

    private final EmployeeRepository employeeRepository;
    private final AllocationRepository allocationRepository;
    private final SkillRepository skillRepository;
    private final ProjectDemandRepository demandRepository;

    /**
     * SQL Task: Utilization by employee and department
     */
    public ReportDto.UtilizationReport getUtilizationReport(LocalDate startDate, LocalDate endDate) {
        List<Object[]> rows = employeeRepository.findUtilizationByEmployeeAndDepartment(startDate, endDate);

        List<ReportDto.EmployeeUtilization> utilizationList = rows.stream().map(row -> {
            int allocated = ((Number) row[3]).intValue();
            int available = 100 - Math.min(100, allocated);
            String status = allocated == 0 ? "UNDER" : (allocated <= 100 ? "OPTIMAL" : "OVER");

            return ReportDto.EmployeeUtilization.builder()
                .employeeId(((Number) row[0]).longValue())
                .employeeName((String) row[1])
                .department((String) row[2])
                .totalAllocatedPercent(Math.min(100, allocated))
                .availablePercent(available)
                .utilizationStatus(status)
                .build();
        }).collect(Collectors.toList());

        double avg = utilizationList.stream()
            .mapToInt(ReportDto.EmployeeUtilization::getTotalAllocatedPercent)
            .average().orElse(0.0);

        long over = utilizationList.stream()
            .filter(u -> u.getTotalAllocatedPercent() > 80).count();

        return ReportDto.UtilizationReport.builder()
            .employees(utilizationList)
            .reportPeriodStart(startDate.toString())
            .reportPeriodEnd(endDate.toString())
            .averageUtilization(Math.round(avg * 10.0) / 10.0)
            .overAllocatedCount((int) over)
            .build();
    }

    /**
     * SQL Task: Skill gaps grouped by skill category
     */
    public ReportDto.SkillGapReport getSkillGapReport() {
        List<Object[]> rows = skillRepository.findSkillGapsGroupedByCategory();

        List<ReportDto.SkillGapEntry> gaps = rows.stream().map(row -> ReportDto.SkillGapEntry.builder()
            .category((String) row[0])
            .skillName((String) row[1])
            .skillId(((Number) row[2]).longValue())
            .demandCount(((Number) row[3]).longValue())
            .employeeCount(((Number) row[4]).longValue())
            .gapScore(((Number) row[5]).longValue())
            .build())
            .collect(Collectors.toList());

        return ReportDto.SkillGapReport.builder()
            .gaps(gaps)
            .totalGaps(gaps.size())
            .build();
    }

    /**
     * SQL Task 2: Resources grouped by skill and proficiency
     */
    public ReportDto.SkillMatrixReport getSkillMatrixGroupedProficiency() {
        List<Object[]> rows = employeeRepository.findResourcesGroupedBySkillAndProficiency();

        List<ReportDto.SkillMatrixEntry> entries = rows.stream().map(row -> ReportDto.SkillMatrixEntry.builder()
            .skillName((String) row[0])
            .skillCategory((String) row[1])
            .proficiencyLevel(String.valueOf(row[2]))
            .resourceCount(((Number) row[3]).longValue())
            .build())
            .collect(Collectors.toList());

        long totalResources = entries.stream()
            .mapToLong(ReportDto.SkillMatrixEntry::getResourceCount)
            .sum();

        return ReportDto.SkillMatrixReport.builder()
            .entries(entries)
            .totalGroups(entries.size())
            .totalResources(totalResources)
            .build();
    }
}
