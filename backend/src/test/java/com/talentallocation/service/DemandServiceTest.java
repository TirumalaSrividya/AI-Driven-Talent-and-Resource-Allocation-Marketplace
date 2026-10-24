package com.talentallocation.service;

import com.talentallocation.domain.*;
import com.talentallocation.dto.*;
import com.talentallocation.exception.*;
import com.talentallocation.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DemandService.
 * Tests TC-06-01 and TC-06-02.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DemandService Unit Tests")
class DemandServiceTest {

    @Mock private ProjectDemandRepository demandRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private AllocationRepository allocationRepository;
    @Mock private TrainingRecommendationRepository trainingRecommendationRepository;

    @InjectMocks
    private DemandService demandService;

    /**
     * TC-06-01: Create a demand with valid fields → created
     */
    @Test
    @DisplayName("TC-06-01: Create demand with valid fields → created successfully")
    void tc0601_createDemandWithValidFields_created() {
        DemandDto.Request request = DemandDto.Request.builder()
            .projectName("Project Alpha")
            .role("Senior Java Developer")
            .startDate(LocalDate.of(2027, 1, 1))
            .endDate(LocalDate.of(2027, 6, 30))
            .allocationPercent(100)
            .priority(ProjectDemand.Priority.HIGH)
            .location("New York")
            .createdBy("admin")
            .build();

        ProjectDemand savedDemand = ProjectDemand.builder()
            .id(1L)
            .projectName("Project Alpha")
            .role("Senior Java Developer")
            .startDate(LocalDate.of(2027, 1, 1))
            .endDate(LocalDate.of(2027, 6, 30))
            .allocationPercent(100)
            .priority(ProjectDemand.Priority.HIGH)
            .status(ProjectDemand.DemandStatus.OPEN)
            .requiredSkills(new ArrayList<>())
            .build();

        when(demandRepository.save(any())).thenReturn(savedDemand);

        DemandDto.Response response = demandService.createDemand(request);

        assertThat(response).isNotNull();
        assertThat(response.getProjectName()).isEqualTo("Project Alpha");
        assertThat(response.getRole()).isEqualTo("Senior Java Developer");
        assertThat(response.getStatus()).isEqualTo(ProjectDemand.DemandStatus.OPEN);
        verify(demandRepository, times(1)).save(any());
    }

    /**
     * TC-06-02: Create a demand with invalid dates → rejected
     */
    @Test
    @DisplayName("TC-06-02a: End date before start date → rejected")
    void tc0602_endDateBeforeStartDate_rejected() {
        DemandDto.Request request = DemandDto.Request.builder()
            .projectName("Bad Project")
            .role("Developer")
            .startDate(LocalDate.of(2027, 6, 1))
            .endDate(LocalDate.of(2027, 1, 1)) // INVALID: end before start
            .allocationPercent(100)
            .build();

        assertThatThrownBy(() -> demandService.createDemand(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("End date must be on or after start date");

        verify(demandRepository, never()).save(any());
    }

    /**
     * TC-06-02: Create demand with end date in the past → rejected
     */
    @Test
    @DisplayName("TC-06-02b: End date in the past → rejected")
    void tc0602_endDateInPast_rejected() {
        DemandDto.Request request = DemandDto.Request.builder()
            .projectName("Past Project")
            .role("Developer")
            .startDate(LocalDate.of(2020, 1, 1))
            .endDate(LocalDate.of(2020, 6, 1))
            .allocationPercent(100)
            .build();

        assertThatThrownBy(() -> demandService.createDemand(request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("past");

        verify(demandRepository, never()).save(any());
    }

    @Test
    @DisplayName("Default priority is MEDIUM when not specified")
    void createDemand_defaultPriorityIsMedium() {
        DemandDto.Request request = DemandDto.Request.builder()
            .projectName("Project Beta")
            .role("Analyst")
            .startDate(LocalDate.now().plusDays(10))
            .endDate(LocalDate.now().plusMonths(3))
            .allocationPercent(50)
            .build(); // No priority set

        ProjectDemand savedDemand = ProjectDemand.builder()
            .id(2L)
            .projectName("Project Beta")
            .role("Analyst")
            .startDate(LocalDate.now().plusDays(10))
            .endDate(LocalDate.now().plusMonths(3))
            .allocationPercent(50)
            .priority(ProjectDemand.Priority.MEDIUM)
            .status(ProjectDemand.DemandStatus.OPEN)
            .requiredSkills(new ArrayList<>())
            .build();

        when(demandRepository.save(any())).thenReturn(savedDemand);

        DemandDto.Response response = demandService.createDemand(request);
        assertThat(response.getPriority()).isEqualTo(ProjectDemand.Priority.MEDIUM);
    }

    @Test
    @DisplayName("Delete demand removes demand and associated records")
    void deleteDemand_success() {
        ProjectDemand demand = ProjectDemand.builder().id(1L).projectName("Project Alpha").build();
        when(demandRepository.findById(1L)).thenReturn(Optional.of(demand));
        when(allocationRepository.findByDemandId(1L)).thenReturn(List.of());
        when(trainingRecommendationRepository.findByDemandId(1L)).thenReturn(List.of());

        demandService.deleteDemand(1L);

        verify(demandRepository).delete(demand);
    }
}
