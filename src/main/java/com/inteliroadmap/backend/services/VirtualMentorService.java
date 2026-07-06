package com.inteliroadmap.backend.services;

import com.inteliroadmap.backend.domain.dto.request.VirtualMentorChatRequest;
import com.inteliroadmap.backend.domain.entity.ChatMessage;
import com.inteliroadmap.backend.domain.entity.ChatSession;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

public interface VirtualMentorService {

    ChatSession createSession(String authorizationHeader, String sessionName) ;

    List<ChatSession> getUserSessions(String authorizationHeader) ;

    List<ChatMessage> getSessionMessages(String authorizationHeader, UUID sessionId) ;

    ChatSession renameSession(String authorizationHeader, UUID sessionId, String newName) ;

    void deleteSession(String authorizationHeader, UUID sessionId) ;

    Flux<String> streamChat(String authorizationHeader, UUID sessionId, VirtualMentorChatRequest request) ;
}
