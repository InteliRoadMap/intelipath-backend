package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.components.RoadmapRefreshTrigger;
import com.inteliroadmap.backend.services.CareerAffinityService;
import com.inteliroadmap.backend.services.GradedAssessmentService;
import com.inteliroadmap.backend.services.SkillService;
import com.inteliroadmap.backend.services.StudentAssessmentService;
import com.inteliroadmap.backend.services.StudentLevelService;
import com.inteliroadmap.backend.services.StudentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StudentControllerValidationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        StudentController controller = new StudentController(
                mock(StudentService.class),
                mock(SkillService.class),
                mock(StudentAssessmentService.class),
                mock(GradedAssessmentService.class),
                mock(StudentLevelService.class),
                mock(CareerAffinityService.class),
                mock(RoadmapRefreshTrigger.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void rejectsMissingSkillIds() throws Exception {
        mockMvc.perform(post("/api/v1/student/skills/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEmptySkillIds() throws Exception {
        mockMvc.perform(post("/api/v1/student/skills/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNullSkillId() throws Exception {
        mockMvc.perform(post("/api/v1/student/skills/select")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillIds\":[null]}"))
                .andExpect(status().isBadRequest());
    }
}
