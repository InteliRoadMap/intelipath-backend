package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.ai.client.AiServiceClient;
import com.inteliroadmap.backend.ai.tool.JobMarketTool;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.ChatMessageRepository;
import com.inteliroadmap.backend.repositories.ChatSessionRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.services.impl.VirtualMentorServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies the virtual mentor resolves the current user from the security context. */
class VirtualMentorServiceSecurityContextTest {

    private ChatSessionRepository chatSessionRepository;
    private UserRepository userRepository;
    private VirtualMentorService virtualMentorService;

    @BeforeEach
    void setUp() throws IOException {
        chatSessionRepository = mock(ChatSessionRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        StudentRepository studentRepository = mock(StudentRepository.class);
        userRepository = mock(UserRepository.class);
        ChatClient chatClient = mock(ChatClient.class);
        VectorStore vectorStore = mock(VectorStore.class);
        AiServiceClient aiServiceClient = mock(AiServiceClient.class);
        Resource systemPrompt = mock(Resource.class);
        Resource ragPrompt = mock(Resource.class);
        when(systemPrompt.getContentAsString(StandardCharsets.UTF_8)).thenReturn("system");
        when(ragPrompt.getContentAsString(StandardCharsets.UTF_8)).thenReturn("{query}");

        virtualMentorService = new VirtualMentorServiceImpl(
                chatSessionRepository,
                chatMessageRepository,
                studentRepository,
                userRepository,
                chatClient,
                vectorStore,
                aiServiceClient,
                mock(StudentLevelService.class),
                mock(JobMarketTool.class),
                systemPrompt,
                ragPrompt
        );
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    @Test
    void getsSessionsForAuthenticatedUser() {
        User user = User.builder().userId(UUID.randomUUID()).email("student@example.com").build();
        authenticateAs(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);
        when(chatSessionRepository.findByUser_UserIdOrderByCreatedAtDesc(user.getUserId())).thenReturn(List.of());

        assertDoesNotThrow(() -> virtualMentorService.getUserSessions());
    }

    @Test
    void rejectsWhenUserNotFound() {
        authenticateAs("missing@example.com");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> virtualMentorService.getUserSessions());
    }
}
