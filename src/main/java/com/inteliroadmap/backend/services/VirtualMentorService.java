package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.VirtualMentorChatRequest;
import com.inteliroadmap.backend.domain.entity.ChatMessage;
import com.inteliroadmap.backend.domain.entity.ChatSession;
import com.inteliroadmap.backend.domain.entity.Student;
import com.inteliroadmap.backend.domain.entity.User;
import com.inteliroadmap.backend.exceptions.ResourceNotFoundException;
import com.inteliroadmap.backend.repositories.ChatMessageRepository;
import com.inteliroadmap.backend.repositories.ChatSessionRepository;
import com.inteliroadmap.backend.repositories.StudentRepository;
import com.inteliroadmap.backend.repositories.UserRepository;
import com.inteliroadmap.backend.security.JwtService;
import com.inteliroadmap.backend.utils.BearerTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public interface VirtualMentorService {

    public ChatSession createSession(String authorizationHeader, String sessionName) ;

    public List<ChatSession> getUserSessions(String authorizationHeader) ;

    public List<ChatMessage> getSessionMessages(String authorizationHeader, UUID sessionId) ;

    public ChatSession renameSession(String authorizationHeader, UUID sessionId, String newName) ;

    public void deleteSession(String authorizationHeader, UUID sessionId) ;

    public Flux<String> streamChat(String authorizationHeader, UUID sessionId, VirtualMentorChatRequest request) ;
}
