package com.inteliroadmap.backend.repositories;

import com.inteliroadmap.backend.domain.entity.ChatMessage;
import com.inteliroadmap.backend.domain.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByChatSessionInOrderByCreateAtAsc(List<ChatSession> chatSessions);
    List<ChatMessage> findByChatSession_SessionIdOrderByCreateAtAsc(UUID sessionId);
    void deleteByChatSession_SessionId(UUID sessionId);
}
