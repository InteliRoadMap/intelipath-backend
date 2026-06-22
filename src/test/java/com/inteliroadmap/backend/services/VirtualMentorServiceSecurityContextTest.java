package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.ChatMessageRepository;
import com.inteliroadmap.backend.repositories.ChatSessionRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VirtualMentorServiceSecurityContextTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private ChatClient.Builder chatClientBuilder;
    @Mock
    private ChatClient chatClient;
    @Mock
    private VectorStore vectorStore;
    @Mock
    private ObjectProvider<SyncMcpToolCallbackProvider> documentToolCallbackProvider;

    private VirtualMentorService virtualMentorService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        virtualMentorService = new VirtualMentorService(
                chatSessionRepository,
                chatMessageRepository,
                studentRepository,
                userRepository,
                jwtService,
                chatClientBuilder,
                vectorStore,
                documentToolCallbackProvider
        );
    }

    @Test
    void getsSessionsUsingBearerToken() {
        User user = User.builder().userId(UUID.randomUUID()).email("student@example.com").build();
        when(jwtService.extractEmail("access-token")).thenReturn(user.getEmail());
        when(userRepository.findByEmail(user.getEmail())).thenReturn(user);
        when(chatSessionRepository.findByUser_UserIdOrderByCreateAtDesc(user.getUserId())).thenReturn(List.of());

        assertDoesNotThrow(() -> virtualMentorService.getUserSessions("Bearer access-token"));
    }

    @Test
    void rejectsRequestWhenTokenDoesNotResolveToUser() {
        when(jwtService.extractEmail("access-token")).thenReturn("missing@example.com");

        assertThrows(ResourceNotFoundException.class,
                () -> virtualMentorService.getUserSessions("Bearer access-token"));
    }
}
