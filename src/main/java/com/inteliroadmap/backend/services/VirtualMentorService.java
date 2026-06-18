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

    @org.springframework.transaction.annotation.Transactional
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
        
        // Check for PDF URL in the message to extract text
        String userMsgText = request.getMessage();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(https?://[^\\s\\)]+\\.pdf)");
        java.util.regex.Matcher matcher = pattern.matcher(userMsgText);
        if (matcher.find()) {
            String pdfUrl = matcher.group(1);
            try {
                // [CHỈNH SỬA TẠI ĐÂY - TRÍCH XUẤT TÀI LIỆU CHAT TRỰC TIẾP]
                // Chỗ này cũng đang dùng PagePdfDocumentReader để đọc file đính kèm trong lúc chat.
                // Nếu bạn đã cài đặt LlamaParse/Tika ở bước Ingestion, bạn CŨNG PHẢI thay thế hàm đọc PDF ở đây 
                // để AI nhận được Markdown thay vì text thô.
                org.springframework.core.io.UrlResource resource = new org.springframework.core.io.UrlResource(pdfUrl);
                org.springframework.ai.reader.pdf.PagePdfDocumentReader pdfReader = new org.springframework.ai.reader.pdf.PagePdfDocumentReader(resource);
                java.util.List<org.springframework.ai.document.Document> docs = pdfReader.get();
                StringBuilder sb = new StringBuilder();
                for (org.springframework.ai.document.Document doc : docs) {
                    sb.append(doc.getContent()).append("\n");
                }
                systemPrompt += "\n\n[ATTACHED PDF CONTENT]\nThe user has attached a PDF file. Here is the extracted text from the PDF:\n" + sb.toString() + "\n[END OF PDF CONTENT]\nPlease base your analysis and advice on this content.";
                log.info("Successfully extracted text from PDF URL: {}", pdfUrl);
            } catch (Exception e) {
                log.error("Failed to read PDF from URL: " + pdfUrl, e);
            }
        }
        
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
                .advisors(new org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor(vectorStore, org.springframework.ai.vectorstore.SearchRequest.defaults()))
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
                });
    }

    private String buildSystemPrompt(User user, Student student) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert AI Virtual Career Mentor for the InteliRoadMap platform.\n");
        prompt.append("Your goal is to provide actionable, encouraging, and highly technical career advice to IT students.\n");
        prompt.append("User Name: ").append(user.getFullName()).append("\n");
        
        if (student != null) {
            prompt.append("University: ").append(student.getUniversity() != null ? student.getUniversity() : "N/A").append("\n");
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
