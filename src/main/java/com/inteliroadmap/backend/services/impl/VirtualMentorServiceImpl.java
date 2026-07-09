package com.inteliroadmap.backend.services.impl;

import com.inteliroadmap.backend.ai.client.AiServiceClient;
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
import com.inteliroadmap.backend.services.VirtualMentorService;
import com.inteliroadmap.backend.utils.BearerTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of the {@link VirtualMentorService}.
 * Provides services for creating, managing, and streaming chat sessions with a virtual AI mentor.
 */
@Service
@Slf4j
public class VirtualMentorServiceImpl implements VirtualMentorService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final AiServiceClient aiServiceClient;
    private final String systemPromptTemplate;
    private final String ragPromptTemplate;

    /** Cap on how much extracted document text to inject, to keep prompts bounded. */
    private static final int MAX_ATTACHMENT_CHARS = 12000;

    /** When false, skip RAG retrieval (embedding + vector search) for faster replies. */
    @Value("${mentor.rag-enabled:true}")
    private boolean ragEnabled;

    /**
     * Constructs a VirtualMentorServiceImpl with the required dependencies.
     *
     * @param chatSessionRepository repository for chat sessions
     * @param chatMessageRepository repository for chat messages
     * @param studentRepository repository for student profiles
     * @param userRepository repository for user profiles
     * @param jwtService service for JWT operations
     * @param chatClientBuilder builder for creating the chat client
     * @param vectorStore the vector store for AI search and retrieval
     */
    public VirtualMentorServiceImpl(ChatSessionRepository chatSessionRepository,
                                ChatMessageRepository chatMessageRepository,
                                StudentRepository studentRepository,
                                UserRepository userRepository,
                                JwtService jwtService,
                                ChatClient chatClient,
                                VectorStore vectorStore,
                                AiServiceClient aiServiceClient,
                                @Value("classpath:prompts/virtual-mentor-system.st") Resource systemPrompt,
                                @Value("classpath:prompts/virtual-mentor-rag.st") Resource ragPrompt) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.aiServiceClient = aiServiceClient;
        try {
            this.systemPromptTemplate = systemPrompt.getContentAsString(StandardCharsets.UTF_8);
            this.ragPromptTemplate = ragPrompt.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load virtual mentor prompts", e);
        }
    }

    /**
     * Creates a new chat session for the authenticated user.
     *
     * @param authorizationHeader the authorization header containing the user's JWT token
     * @param sessionName the name for the new session, defaults to "New Chat" if blank or null
     * @return the created ChatSession
     */
    @Override
    public ChatSession createSession(String authorizationHeader, String sessionName) {
        // Retrieve authenticated user from the provided token
        User user = getAuthenticatedUser(authorizationHeader);
        
        // Build the new chat session and handle default naming
        ChatSession chatSession = ChatSession.builder()
                .user(user)
                .sessionName(sessionName != null && !sessionName.isBlank() ? sessionName : "New Chat")
                .createdAt(LocalDateTime.now())
                .build();
                
        // Persist and return the new session
        return chatSessionRepository.save(chatSession);
    }

    /**
     * Retrieves all chat sessions for the authenticated user.
     *
     * @param authorizationHeader the authorization header containing the user's JWT token
     * @return a list of chat sessions belonging to the user
     */
    @Override
    public List<ChatSession> getUserSessions(String authorizationHeader) {
        User user = getAuthenticatedUser(authorizationHeader);
        return chatSessionRepository.findByUser_UserIdOrderByCreatedAtDesc(user.getUserId());
    }

    /**
     * Retrieves all messages for a specific chat session belonging to the user.
     *
     * @param authorizationHeader the authorization header containing the user's JWT token
     * @param sessionId the ID of the chat session
     * @return a list of chat messages for the session
     * @throws ResourceNotFoundException if the session is not found or access is denied
     */
    @Override
    public List<ChatMessage> getSessionMessages(String authorizationHeader, UUID sessionId) {
        User user = getAuthenticatedUser(authorizationHeader);
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
                
        if (!session.getUser().getUserId().equals(user.getUserId())) {
            throw new ResourceNotFoundException("Access denied to this session");
        }
        
        return chatMessageRepository.findByChatSession_SessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * Renames an existing chat session for the authenticated user.
     *
     * @param authorizationHeader the authorization header containing the user's JWT token
     * @param sessionId the ID of the chat session to rename
     * @param newName the new name for the chat session
     * @return the updated ChatSession
     * @throws ResourceNotFoundException if the session is not found or access is denied
     */
    @Transactional
    @Override
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

    /**
     * Deletes a specific chat session and all associated messages.
     *
     * @param authorizationHeader the authorization header containing the user's JWT token
     * @param sessionId the ID of the chat session to delete
     * @throws ResourceNotFoundException if the session is not found or access is denied
     */
    @Transactional
    @Override
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

    /**
     * Streams a chat response from the virtual mentor in a specific chat session.
     *
     * @param authorizationHeader the authorization header containing the user's JWT token
     * @param sessionId the ID of the chat session
     * @param request the request containing the user's message
     * @return a Flux streaming the AI's response content
     * @throws ResourceNotFoundException if the session is not found or access is denied
     */
    @Override
    public Flux<String> streamChat(String authorizationHeader, UUID sessionId, VirtualMentorChatRequest request) {
        // Authenticate user and fetch the targeted chat session
        User user = getAuthenticatedUser(authorizationHeader);
        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

        // Ensure the authenticated user owns this chat session
        if (!session.getUser().getUserId().equals(user.getUserId())) {
            throw new ResourceNotFoundException("Access denied to this session");
        }

        // Save User Message to the database
        ChatMessage userMessage = ChatMessage.builder()
                .chatSession(ChatSession.builder().sessionId(session.getSessionId()).build())
                .role("USER")
                .content(request.getMessage())
                .createdAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(userMessage);

        // Prepare context for AI
        // 1. System Prompt (Context)
        Student student = studentRepository.findById(user.getUserId()).orElse(null);
        String systemPrompt = buildSystemPrompt(user, student);
        
        // 2. Build Message List
        List<Message> messageHistory = new ArrayList<>();
        
        // Add System Message first to provide instructions and persona
        messageHistory.add(new SystemMessage(systemPrompt));
        
        // Add Chat History for the LLM context window
        List<ChatMessage> chatHistory = chatMessageRepository.findByChatSession_SessionIdOrderByCreatedAtAsc(sessionId);
        for (int i = 0; i < chatHistory.size(); i++) {
            ChatMessage msg = chatHistory.get(i);
            // Skip the VERY LAST message if it's the one we just saved
            if (i == chatHistory.size() - 1 && "USER".equalsIgnoreCase(msg.getRole()) && msg.getContent().equals(request.getMessage())) {
                continue;
            }
            // Map the persisted roles to Spring AI message types
            if ("USER".equalsIgnoreCase(msg.getRole())) {
                messageHistory.add(new UserMessage(msg.getContent()));
            } else if ("ASSISTANT".equalsIgnoreCase(msg.getRole())) {
                messageHistory.add(new AssistantMessage(msg.getContent()));
            }
        }
        
        // If the user attached a document, read it once and inject its text so the
        // model answers about the actual content (reliable, and no tool round-trip).
        String userPrompt = augmentWithAttachment(request.getMessage(), request.getFileUrl());

        // We use StringBuffer to accumulate the full response for saving to DB asynchronously
        StringBuffer fullResponse = new StringBuffer();

        var promptSpec = chatClient.prompt()
                .messages(messageHistory)
                .user(userPrompt)
                .toolNames("jobMarketTool", "studentProgressTool", "markItDownTool");

        // Attach vector-store retrieval only when RAG is enabled (embedding +
        // search add latency; skipping is much faster when knowledge is unused).
        if (ragEnabled) {
            promptSpec = promptSpec.advisors(QuestionAnswerAdvisor.builder(vectorStore)
                    .searchRequest(SearchRequest.builder().build())
                    .promptTemplate(new PromptTemplate(ragPromptTemplate))
                    .build());
        }

        return promptSpec
                .stream()
                .content()
                .doOnNext(fullResponse::append)
                .doOnComplete(() -> {
                    // Save the AI response to DB when streaming is complete
                    ChatMessage assistantMessage = ChatMessage.builder()
                            .chatSession(ChatSession.builder().sessionId(session.getSessionId()).build())
                            .role("ASSISTANT")
                            .content(fullResponse.toString())
                            .createdAt(LocalDateTime.now())
                            .build();
                    chatMessageRepository.save(assistantMessage);
                });
    }

    /**
     * Builds the system prompt used for instructing the virtual mentor.
     *
     * @param user the authenticated user
     * @param student the student profile associated with the user, if any
     * @return the constructed system prompt string
     */
    /**
     * If a document was attached, read its text via the AI service once and append
     * it to the user's message so the model can answer about the real content.
     * Falls back gracefully (keeps the original message) if extraction fails.
     */
    private String augmentWithAttachment(String message, String fileUrl) {
        String base = message == null ? "" : message;
        if (fileUrl == null || fileUrl.isBlank()) {
            return base;
        }
        try {
            String docText = aiServiceClient.extractMarkdown(fileUrl);
            if (docText == null || docText.isBlank()) {
                return base;
            }
            if (docText.length() > MAX_ATTACHMENT_CHARS) {
                docText = docText.substring(0, MAX_ATTACHMENT_CHARS) + "\n...[truncated]";
            }
            String lead = base.isBlank() ? "Please review the attached document." : base;
            return lead + "\n\n[Attached document content — analyze it, but reply in the language of my message above]\n" + docText;
        } catch (Exception e) {
            log.warn("VirtualMentorServiceImpl: failed to read attachment {}: {}", fileUrl, e.getMessage());
            return base.isBlank()
                    ? "The user attached a document but it could not be read. Ask them to re-upload it."
                    : base;
        }
    }

    private String buildSystemPrompt(User user, Student student) {
        String university = student != null && student.getUniversity() != null ? student.getUniversity().getName() : "N/A";
        String major = student != null && student.getMajor() != null ? student.getMajor() : "N/A";
        String github = student != null && student.getGithubProfile() != null ? student.getGithubProfile() : "N/A";
        String transcript = student != null && student.getTranscriptUrl() != null ? student.getTranscriptUrl() : "N/A";
        return String.format(systemPromptTemplate, user.getFullName(), university, major, github, transcript);
    }

    /**
     * Helper method to authenticate and retrieve the user from the authorization header.
     *
     * @param authorizationHeader the authorization header containing the JWT token
     * @return the authenticated User
     * @throws ResourceNotFoundException if the user is not found
     */
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
