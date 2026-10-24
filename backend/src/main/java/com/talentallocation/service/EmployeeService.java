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
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final SkillRepository skillRepository;
    private final AllocationRepository allocationRepository;

    public EmployeeDto.Response createEmployee(EmployeeDto.Request request) {
        if (employeeRepository.findByEmployeeCode(request.getEmployeeCode()).isPresent()) {
            throw new BusinessException("Employee code already exists: " + request.getEmployeeCode());
        }
        if (employeeRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessException("Email already registered: " + request.getEmail());
        }

        Employee employee = Employee.builder()
            .employeeCode(request.getEmployeeCode())
            .name(request.getName())
            .email(request.getEmail())
            .location(request.getLocation())
            .department(request.getDepartment())
            .seniorityLevel(request.getSeniorityLevel())
            .employmentStatus(request.getEmploymentStatus() != null
                ? request.getEmploymentStatus() : Employee.EmploymentStatus.ACTIVE)
            .build();

        Employee saved = employeeRepository.save(employee);
        log.info("Employee created: {} ({})", saved.getName(), saved.getEmployeeCode());
        return toResponse(saved, List.of(), 0);
    }

    @Transactional(readOnly = true)
    public List<EmployeeDto.Response> getAllEmployees() {
        return employeeRepository.findAll().stream()
            .map(e -> {
                List<EmployeeSkill> skills = employeeSkillRepository.findByEmployeeId(e.getId());
                Integer allocated = allocationRepository.sumAllocatedPercentForPeriod(
                    e.getId(), LocalDate.now(), LocalDate.now().plusMonths(3), null);
                return toResponse(e, skills, allocated != null ? allocated : 0);
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EmployeeDto.Response getEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", id));
        List<EmployeeSkill> skills = employeeSkillRepository.findByEmployeeId(id);
        Integer allocated = allocationRepository.sumAllocatedPercentForPeriod(
            id, LocalDate.now(), LocalDate.now().plusMonths(3), null);
        return toResponse(employee, skills, allocated != null ? allocated : 0);
    }

    public SkillDto.EmployeeSkillResponse addSkill(Long employeeId, SkillDto.AddSkillRequest request) {
        Employee employee = employeeRepository.findById(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        Skill skill = skillRepository.findById(request.getSkillId())
            .orElseThrow(() -> new ResourceNotFoundException("Skill", request.getSkillId()));

        if (employeeSkillRepository.existsByEmployeeIdAndSkillId(employeeId, request.getSkillId())) {
            throw new BusinessException("Employee already has skill: " + skill.getName() +
                ". Use update endpoint to change proficiency.");
        }

        EmployeeSkill employeeSkill = EmployeeSkill.builder()
            .employee(employee)
            .skill(skill)
            .proficiencyLevel(request.getProficiencyLevel())
            .yearsExperience(request.getYearsExperience())
            .lastValidatedAt(request.getLastValidatedAt())
            .certified(request.isCertified())
            .build();

        EmployeeSkill saved = employeeSkillRepository.save(employeeSkill);
        log.info("Skill {} added to employee {} with level {}", skill.getName(), employee.getName(),
            request.getProficiencyLevel());
        return toSkillResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SkillDto.EmployeeSkillResponse> getEmployeeSkills(Long employeeId) {
        employeeRepository.findById(employeeId)
            .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));
        return employeeSkillRepository.findByEmployeeId(employeeId).stream()
            .map(this::toSkillResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EmployeeDto.Response> getAvailableEmployees(LocalDate startDate, LocalDate endDate,
                                                              Long skillId, String location) {
        List<Employee> employees = employeeRepository.findByEmploymentStatus(Employee.EmploymentStatus.ACTIVE);
        return employees.stream()
            .filter(e -> location == null || location.isBlank() ||
                e.getLocation().equalsIgnoreCase(location))
            .filter(e -> {
                if (skillId == null) return true;
                return employeeSkillRepository.findByEmployeeId(e.getId()).stream()
                    .anyMatch(es -> es.getSkill().getId().equals(skillId));
            })
            .map(e -> {
                List<EmployeeSkill> skills = employeeSkillRepository.findByEmployeeId(e.getId());
                Integer allocated = allocationRepository.sumAllocatedPercentForPeriod(
                    e.getId(), startDate, endDate, null);
                return toResponse(e, skills, allocated != null ? allocated : 0);
            })
            .filter(r -> r.getCurrentAllocationPercent() < 100)
            .collect(Collectors.toList());
    }

    private EmployeeDto.Response toResponse(Employee e, List<EmployeeSkill> skills, int allocated) {
        return EmployeeDto.Response.builder()
            .id(e.getId())
            .employeeCode(e.getEmployeeCode())
            .name(e.getName())
            .email(e.getEmail())
            .location(e.getLocation())
            .employmentStatus(e.getEmploymentStatus())
            .department(e.getDepartment())
            .seniorityLevel(e.getSeniorityLevel())
            .createdAt(e.getCreatedAt())
            .skills(skills.stream().map(this::toSkillResponse).collect(Collectors.toList()))
            .currentAllocationPercent(allocated)
            .build();
    }

    private SkillDto.EmployeeSkillResponse toSkillResponse(EmployeeSkill es) {
        return SkillDto.EmployeeSkillResponse.builder()
            .id(es.getId())
            .skillId(es.getSkill().getId())
            .skillName(es.getSkill().getName())
            .skillCategory(es.getSkill().getCategory())
            .proficiencyLevel(es.getProficiencyLevel())
            .yearsExperience(es.getYearsExperience())
            .lastValidatedAt(es.getLastValidatedAt())
            .certified(es.isCertified())
            .build();
    }
}
