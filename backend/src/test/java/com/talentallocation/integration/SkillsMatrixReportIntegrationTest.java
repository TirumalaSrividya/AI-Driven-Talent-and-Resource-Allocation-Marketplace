package com.talentallocation.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verification test for SQL Task 2: Resources grouped by skill and proficiency.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SQL Task 2 - Skills Matrix Report Integration Test")
class SkillsMatrixReportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/reports/skills-matrix returns resources grouped by skill and proficiency with resource counts")
    void testGetSkillsMatrixReport() throws Exception {
        mockMvc.perform(get("/api/reports/skills-matrix")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries").isArray())
            .andExpect(jsonPath("$.entries", hasSize(greaterThan(0))))
            .andExpect(jsonPath("$.totalGroups", greaterThan(0)))
            .andExpect(jsonPath("$.totalResources", greaterThan(0)))
            .andExpect(jsonPath("$.entries[0].skillName").isNotEmpty())
            .andExpect(jsonPath("$.entries[0].proficiencyLevel").isNotEmpty())
            .andExpect(jsonPath("$.entries[0].resourceCount", greaterThanOrEqualTo(1)));
    }
}
