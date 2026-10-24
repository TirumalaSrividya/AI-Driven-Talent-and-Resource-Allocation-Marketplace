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
public class DemandService {

    private final ProjectDemandRepository demandRepository;
    private final SkillRepository skillRepository;
    private final AllocationRepository allocationRepository;
    private final TrainingRecommendationRepository trainingRecommendationRepository;

    /**
     * TC-06-01: Create demand with valid fields → created.
     * TC-06-02: Missing role or invalid dates → rejected (via @Valid in controller).
     */
    public DemandDto.Response createDemand(DemandDto.Request request) {
        // Business rule: end date must be after or equal to start date
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException("End date must be on or after start date");
        }

        // Business rule: demand cannot be in the past
        if (request.getEndDate().isBefore(LocalDate.now())) {
            throw new BusinessException("End date cannot be in the past");
        }

        ProjectDemand demand = ProjectDemand.builder()
            .projectName(request.getProjectName())
            .role(request.getRole())
            .description(request.getDescription())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .location(request.getLocation())
            .allocationPercent(request.getAllocationPercent())
            .priority(request.getPriority() != null ? request.getPriority() : ProjectDemand.Priority.MEDIUM)
            .status(ProjectDemand.DemandStatus.OPEN)
            .createdBy(request.getCreatedBy())
            .build();

        ProjectDemand saved = demandRepository.save(demand);

        // Add required skills
        if (request.getRequiredSkills() != null) {
            for (DemandDto.RequiredSkillRequest skillReq : request.getRequiredSkills()) {
                Skill skill = skillRepository.findById(skillReq.getSkillId())
                    .orElseThrow(() -> new ResourceNotFoundException("Skill", skillReq.getSkillId()));

                DemandRequiredSkill drs = DemandRequiredSkill.builder()
                    .demand(saved)
                    .skill(skill)
                    .minimumProficiency(skillReq.getMinimumProficiency() != null
                        ? skillReq.getMinimumProficiency() : EmployeeSkill.ProficiencyLevel.INTERMEDIATE)
                    .mandatory(skillReq.isMandatory())
                    .build();
                saved.getRequiredSkills().add(drs);
            }
            saved = demandRepository.save(saved);
        }

        log.info("Demand created: {} ({})", saved.getProjectName(), saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DemandDto.Response> getDemands(ProjectDemand.DemandStatus status,
                                                Long skillId,
                                                LocalDate startDate,
                                                LocalDate endDate) {
        return demandRepository.findWithFilters(status, skillId, startDate, endDate).stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DemandDto.Response getDemand(Long id) {
        return toResponse(demandRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ProjectDemand", id)));
    }

    @Transactional(readOnly = true)
    public List<DemandDto.Response> getOpenDemandsOrderedByPriority() {
        return demandRepository.findOpenDemandsOrderedByPriority().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DemandDto.Response> getDemandsWithNoAllocation() {
        return demandRepository.findOpenDemandsWithNoAllocation().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    public DemandDto.Response updateDemandStatus(Long id, ProjectDemand.DemandStatus newStatus) {
        ProjectDemand demand = demandRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ProjectDemand", id));
        demand.setStatus(newStatus);
        return toResponse(demandRepository.save(demand));
    }

    public void deleteDemand(Long id) {
        ProjectDemand demand = demandRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ProjectDemand", id));

        // Remove any allocations associated with this demand
        List<Allocation> allocations = allocationRepository.findByDemandId(id);
        if (allocations != null && !allocations.isEmpty()) {
            allocationRepository.deleteAll(allocations);
        }

        // Unlink any training recommendations associated with this demand
        List<TrainingRecommendation> trs = trainingRecommendationRepository.findByDemandId(id);
        if (trs != null && !trs.isEmpty()) {
            for (TrainingRecommendation tr : trs) {
                tr.setDemand(null);
                trainingRecommendationRepository.save(tr);
            }
        }

        demandRepository.delete(demand);
        log.info("Demand deleted: {} ({})", demand.getProjectName(), id);
    }

    private DemandDto.Response toResponse(ProjectDemand d) {
        List<DemandDto.RequiredSkillResponse> skillResponses = d.getRequiredSkills().stream()
            .map(drs -> DemandDto.RequiredSkillResponse.builder()
                .skillId(drs.getSkill().getId())
                .skillName(drs.getSkill().getName())
                .skillCategory(drs.getSkill().getCategory())
                .minimumProficiency(drs.getMinimumProficiency())
                .mandatory(drs.isMandatory())
                .build())
            .collect(Collectors.toList());

        return DemandDto.Response.builder()
            .id(d.getId())
            .projectName(d.getProjectName())
            .role(d.getRole())
            .description(d.getDescription())
            .startDate(d.getStartDate())
            .endDate(d.getEndDate())
            .location(d.getLocation())
            .allocationPercent(d.getAllocationPercent())
            .priority(d.getPriority())
            .status(d.getStatus())
            .createdBy(d.getCreatedBy())
            .createdAt(d.getCreatedAt())
            .requiredSkills(skillResponses)
            .build();
    }
}
