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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class VirtualMentorService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public VirtualMentorService(ChatSessionRepository chatSessionRepository,
                                ChatMessageRepository chatMessageRepository,
                                StudentRepository studentRepository,
                                UserRepository userRepository,
                                JwtService jwtService,
                                ChatClient.Builder chatClientBuilder,
                                VectorStore vectorStore) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder.build();
    }

    public ChatSession createSession(String authorizationHeader, String sessionName) {
        User user = getAuthenticatedUser(authorizationHeader);
        
        ChatSession chatSession = ChatSession.builder()
                .user(user)
                .sessionName(sessionName != null && !sessionName.isBlank() ? sessionName : "New Chat")
                .createAt(LocalDateTime.now())
                .build();
                
        return chatSessionRepository.save(chatSession);
    }

    public List<ChatSession> getUserSessions(String authorizationHeader) {
        User user = getAuthenticatedUser(authorizationHeader);
        return chatSessionRepository.findByUser_UserIdOrderByCreateAtDesc(user.getUserId());
    }

    public List<ChatMessage> getSessionMessages(String authorizationHeader, UUID sessionId) {
        User user = getAuthenticatedUser(authorizationHeader);
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
                
        if (!session.getUser().getUserId().equals(user.getUserId())) {
            throw new ResourceNotFoundException("Access denied to this session");
        }
        
        return chatMessageRepository.findByChatSession_SessionIdOrderByCreateAtAsc(sessionId);
    }

    @org.springframework.transaction.annotation.Transactional
    public ChatSession renameSession(String authorizationHeader, UUID sessionId, String newName) {
        User user = getAuthenticatedUser(authorizationHeader);
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        if (!session.getUser().getUserId().equals(user.getUserId())) {
            throw new ResourceNotFoundException("Access denied to this session");
        }

        session.setSessionName(newName);
        return chatSessionRepository.save(session);
    }

    @Transactional
    public void deleteSession(String authorizationHeader, UUID sessionId) {
        User user = getAuthenticatedUser(authorizationHeader);
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        if (!session.getUser().getUserId().equals(user.getUserId())) {
            throw new ResourceNotFoundException("Access denied to this session");
        }

        // Delete all messages belonging to this session first to prevent foreign key constraint violations
        chatMessageRepository.deleteByChatSession_SessionId(sessionId);
        // Delete the session itself
        chatSessionRepository.delete(session);
    }

    @Transactional
    public Flux<String> streamChat(String authorizationHeader, UUID sessionId, VirtualMentorChatRequest request) {
        User user = getAuthenticatedUser(authorizationHeader);
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        if (!session.getUser().getUserId().equals(user.getUserId())) {
            throw new ResourceNotFoundException("Access denied to this session");
        }

        // Save User Message
        ChatMessage userMessage = ChatMessage.builder()
                .chatSession(session)
                .role("USER")
                .content(request.getMessage())
                .createAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(userMessage);

        // Prepare context for AI
        // 1. System Prompt (Context)
        Student student = studentRepository.findById(user.getUserId()).orElse(null);
        String systemPrompt = buildSystemPrompt(user, student);
        // System prompt already built
        // 2. Build Message List
        List<org.springframework.ai.chat.messages.Message> messageHistory = new ArrayList<>();
        
        // Add System Message first
        messageHistory.add(new org.springframework.ai.chat.messages.SystemMessage(systemPrompt));
        
        // Add Chat History
        List<ChatMessage> previousMessages = chatMessageRepository.findByChatSession_SessionIdOrderByCreateAtAsc(sessionId);
        for (int i = 0; i < previousMessages.size(); i++) {
            ChatMessage msg = previousMessages.get(i);
            // Skip the VERY LAST message if it's the one we just saved
            if (i == previousMessages.size() - 1 && "USER".equalsIgnoreCase(msg.getRole()) && msg.getContent().equals(request.getMessage())) {
                continue;
            }
            if ("USER".equalsIgnoreCase(msg.getRole())) {
                messageHistory.add(new org.springframework.ai.chat.messages.UserMessage(msg.getContent()));
            } else if ("ASSISTANT".equalsIgnoreCase(msg.getRole())) {
                messageHistory.add(new org.springframework.ai.chat.messages.AssistantMessage(msg.getContent()));
            }
        }
        
        // We use StringBuffer to accumulate the full response for saving to DB
        StringBuffer fullResponse = new StringBuffer();

        return chatClient.prompt()
                .messages(messageHistory)
                .user(request.getMessage())
                // [CHỈNH SỬA TẠI ĐÂY - RAG & TÌM KIẾM VECTOR]
                // Advisor này tự động biến câu hỏi của User thành Vector (dùng EmbeddingModel) 
                // rồi tìm kiếm trong PostgreSQL những đoạn text giống nhất.
                // Nếu bạn muốn đổi thuật toán tìm kiếm (vd: chỉnh k=5, threshold=0.8), bạn sửa ở SearchRequest.defaults().
                .advisors(new org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor(
                        vectorStore, 
                        org.springframework.ai.vectorstore.SearchRequest.defaults()
                                .withFilterExpression("userId == '" + user.getUserId().toString() + "'"),
                        "\n\n[OPTIONAL RETRIEVED CONTEXT]\n" +
                                "---------------------\n" +
                                "{question_answer_context}\n" +
                                "---------------------\n" +
                                "If the above context is relevant to the user's question, use it. Otherwise, ignore it and rely completely on the conversation history, the attached PDF (if any), and your own knowledge. DO NOT say you cannot answer just because the context is empty or irrelevant."
                ))
                .functions("jobMarketTool", "studentProgressTool", "markItDownTool")
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    // Save the AI response to DB when streaming is complete
                    ChatMessage assistantMessage = ChatMessage.builder()
                            .chatSession(session)
                            .role("ASSISTANT")
                            .content(fullResponse.toString())
                            .createAt(LocalDateTime.now())
                            .build();
                    chatMessageRepository.save(assistantMessage);
                })
                .doOnError(error -> {
                    log.error("AI Stream failed", error);
                    ChatMessage errorMessage = ChatMessage.builder()
                            .chatSession(session)
                            .role("ASSISTANT")
                            .content(fullResponse.toString() + "\n\n**[System: Gặp lỗi khi tạo phản hồi. Vui lòng thử lại.]**")
                            .createAt(LocalDateTime.now())
                            .build();
                    chatMessageRepository.save(errorMessage);
                });
    }

    private String buildSystemPrompt(User user, Student student) {
        String university = (student != null && student.getUniversity() != null) ? student.getUniversity().getName() : "Unknown";
        String major = (student != null && student.getMajor() != null) ? student.getMajor() : "Unknown";
        String github = (student != null && student.getGithubProfile() != null) ? student.getGithubProfile() : null;
        String transcriptUrl = (student != null && student.getTranscriptUrl() != null) ? student.getTranscriptUrl() : null;

        StringBuilder prompt = new StringBuilder();

        prompt.append("""
                        ## IDENTITY
                        You are **InteliPath AI Mentor** — an elite, highly technical AI career advisor embedded in the InteliRoadMap platform, dedicated exclusively to helping Vietnamese IT students build winning tech careers.
                        
                        ## STUDENT CONTEXT
                        """);
        prompt.append("- **Name**: ").append(user.getFullName()).append("\n");
        prompt.append("- **University**: ").append(university).append("\n");
        prompt.append("- **Major**: ").append(major).append("\n");
        if (github != null) {
            prompt.append("- **GitHub**: ").append(github).append("\n");
        }
        if (transcriptUrl != null) {
            prompt.append("- **Transcript URL**: ").append(transcriptUrl).append("\n");
        }

        prompt.append("""

                        ## TOOL USAGE — MANDATORY RULES (NON-NEGOTIABLE)
                        You have access to the following tools. You MUST use them proactively — never tell the user you "cannot access" a file or URL if a tool exists to do it.
                        
                        ### `markItDownTool`
                        - **TRIGGER**: ANY time the user's message contains a URL (http/https) pointing to a PDF, DOCX, image, or any document.
                        - **ACTION**: IMMEDIATELY call `markItDownTool` with that URL. Do NOT ask the user to paste the content manually.
                        - **CRITICAL**: If the student's Transcript URL is provided in the context above, call `markItDownTool` on it automatically when the user asks about their grades, GPA, or academic performance — without waiting to be asked.
                        - **LANGUAGE**: The documents may be in Vietnamese. Extract and interpret all Vietnamese text faithfully, including diacritical marks (ă, â, đ, ê, ô, ơ, ư and tones).
                        
                        ### `studentProgressTool`
                        - **TRIGGER**: When the user asks about their roadmap, skill progress, completed nodes, or learning path status.
                        - **ACTION**: Call this tool to get real-time data. Do NOT guess or fabricate progress numbers.
                        
                        ### `jobMarketTool`
                        - **TRIGGER**: When the user asks about job market trends, in-demand skills, salary benchmarks, or hiring companies in Vietnam's tech industry.
                        - **ACTION**: Call this tool to fetch current data before answering.
                        
                        ## RESPONSE RULES
                        1. **Language**: ALWAYS respond in the same language the user writes in. If they write Vietnamese → respond Vietnamese. English → English.
                        2. **Format**: Use Markdown. Structure answers with headers, bullet points, and code blocks where appropriate.
                        3. **Accuracy**: NEVER fabricate data, statistics, or company names. If uncertain, say so clearly.
                        4. **Scope**: Focus exclusively on IT career guidance, learning paths, skills, job market, and portfolio advice. If the user asks about unrelated topics (cooking, politics, etc.), politely but firmly redirect them.
                        5. **Encouragement**: Be direct, professional, and motivating. Avoid filler phrases like "Great question!".
                        6. **No hallucination**: If a tool returns an error or empty data, tell the user honestly instead of making things up.
                        
                        ## ABSOLUTE PROHIBITIONS
                        - NEVER say "I don't have access to your file" if the user has shared a URL — use `markItDownTool`.
                        - NEVER invent roadmap progress numbers — use `studentProgressTool`.
                        - NEVER refuse to answer career questions by claiming lack of knowledge — use available tools and your training data.

                        ## TOOL ERROR HANDLING
                        - If a tool returns a string starting with `[TOOL_ERROR]`, read the message carefully and relay it to the user in a friendly, natural way in their language.
                        - Do NOT expose the raw `[TOOL_ERROR]` tag to the user.
                        - Example: if tool returns `[TOOL_ERROR] service unavailable`, tell user: "Hiện tại dịch vụ đọc tài liệu đang bảo trì, bạn vui lòng thử lại sau nhé."
                        """);

        return prompt.toString();
    }

    private User getAuthenticatedUser(String authorizationHeader) {
        String token = BearerTokenUtil.extractToken(authorizationHeader);
        String email = jwtService.extractEmail(token);
        User user = userRepository.findByEmail(email);
        if (user == null) {
            throw new ResourceNotFoundException("User not found");
        }
        return user;
    }
}
