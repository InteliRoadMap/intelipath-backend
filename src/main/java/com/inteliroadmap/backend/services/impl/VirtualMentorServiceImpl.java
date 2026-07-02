package com.inteliroadmap.backend.services.impl;

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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

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
    @org.springframework.transaction.annotation.Transactional
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
    @org.springframework.transaction.annotation.Transactional
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
        
        // We use StringBuffer to accumulate the full response for saving to DB asynchronously
        StringBuffer fullResponse = new StringBuffer();

        return chatClient.prompt()
                .messages(messageHistory)
                .user(request.getMessage())
                // Configure Vector Store Search request to attach relevant context retrieved from embedding DB
                .advisors(QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(SearchRequest.builder().build())
                        .promptTemplate(new PromptTemplate("\n\n[OPTIONAL RETRIEVED CONTEXT]\n" +
                        "---------------------\n" +
                        "{question_answer_context}\n" +
                        "---------------------\n" +
                        "If the above context is relevant to the user's question, use it. Otherwise, ignore it and rely completely on the conversation history, the attached PDF (if any), and your own knowledge. DO NOT say you cannot answer just because the context is empty or irrelevant."))
                        .build())
                .toolNames("jobMarketTool", "studentProgressTool", "markItDownTool")
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
    private String buildSystemPrompt(User user, Student student) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert AI Virtual Career Mentor for the InteliRoadMap platform.\n");
        prompt.append("Your goal is to provide actionable, encouraging, and highly technical career advice to IT students.\n");
        prompt.append("User Name: ").append(user.getFullName()).append("\n");
        
        if (student != null) {
            prompt.append("University: ").append(student.getUniversity() != null ? student.getUniversity().getName() : "N/A").append("\n");
            prompt.append("Major: ").append(student.getMajor() != null ? student.getMajor() : "N/A").append("\n");
            prompt.append("GitHub Profile: ").append(student.getGithubProfile() != null ? student.getGithubProfile() : "N/A").append("\n");
            prompt.append("Transcript Info: ").append(student.getTranscriptUrl() != null ? student.getTranscriptUrl() : "N/A").append("\n");
            // Here we could implement the Retrieval Augmented Generation (RAG) by downloading and parsing 
            // the transcript URL or fetching GitHub API directly.
        }
        
        prompt.append("\nRespond in Markdown format. Keep your answers concise, structured, and helpful. ");
        prompt.append("If the user asks about something unrelated to IT careers or learning paths, politely redirect them.");
        return prompt.toString();
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
