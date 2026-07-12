package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.VirtualMentorChatRequest;
import com.inteliroadmap.backend.domain.entity.ChatMessage;
import com.inteliroadmap.backend.domain.entity.ChatSession;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

public interface VirtualMentorService {

    ChatSession createSession(String sessionName);

    List<ChatSession> getUserSessions();

    List<ChatMessage> getSessionMessages(UUID sessionId);

    ChatSession renameSession(UUID sessionId, String newName);

    void deleteSession(UUID sessionId);

    Flux<String> streamChat(UUID sessionId, VirtualMentorChatRequest request);
}
