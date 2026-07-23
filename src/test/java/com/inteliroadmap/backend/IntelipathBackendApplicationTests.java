//package com.inteliroadmap.backend;
//
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
//import org.springframework.http.MediaType;
//import org.springframework.test.web.servlet.MockMvc;
//
//import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@SpringBootTest
//@AutoConfigureMockMvc
//class IntelipathBackendApplicationTests {
//
//	@Autowired
//	private MockMvc mockMvc;
//
//	@Test
//	void contextLoads() {
//	}
//
//	@Test
//	void studentEndpointRejectsUserWithoutStudentRole() throws Exception {
//		mockMvc.perform(post("/api/v1/student/skills/select")
//						.with(user("non-student@example.com").roles("USER"))
//						.contentType(MediaType.APPLICATION_JSON)
//						.content("{\"skillIds\":[\"ff77a5b1-b38d-4911-8088-e8ef022f1f26\"]}"))
//				.andExpect(status().isForbidden());
//	}
//}
