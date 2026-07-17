package com.inteliroadmap.backend.controllers;

import com.inteliroadmap.backend.domain.dto.request.VirtualMentorChatRequest;
import com.inteliroadmap.backend.domain.dto.request.VirtualMentorRenameSessionRequest;
import com.inteliroadmap.backend.domain.dto.request.VirtualMentorSessionRequest;
import com.inteliroadmap.backend.domain.dto.response.mentor.VirtualMentorMessageResponse;
import com.inteliroadmap.backend.domain.dto.response.mentor.VirtualMentorSessionResponse;
import com.inteliroadmap.backend.domain.entity.ChatMessage;
import com.inteliroadmap.backend.domain.entity.ChatSession;
import com.inteliroadmap.backend.domain.entity.RagDocument;
import com.inteliroadmap.backend.services.DocumentIngestionService;
import com.inteliroadmap.backend.services.RagDocumentService;
import com.inteliroadmap.backend.services.SupabaseStorageService;
import com.inteliroadmap.backend.services.VirtualMentorService;
import com.inteliroadmap.backend.utils.FileValidationUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Chat", description = "AI Virtual Career Mentor endpoints")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
public class VirtualMentorController {

    private final VirtualMentorService virtualMentorService;
    private final SupabaseStorageService supabaseStorageService;
    private final DocumentIngestionService documentIngestionService;
    private final RagDocumentService ragDocumentService;

    @PostMapping("/sessions")
    @Operation(summary = "Create a new chat session")
    public ResponseEntity<VirtualMentorSessionResponse> createSession(
            @RequestBody(required = false) @Valid VirtualMentorSessionRequest request) {
        log.info("VirtualMentorController: Creating new AI chat session");
        String sessionName = (request != null && request.getSessionName() != null) ? request.getSessionName() : "New Chat";
        ChatSession session = virtualMentorService.createSession(sessionName);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToSessionResponse(session));
    }

    @GetMapping("/sessions")
    @Operation(summary = "Get all chat sessions for the authenticated user")
    public ResponseEntity<List<VirtualMentorSessionResponse>> getSessions() {
        log.info("VirtualMentorController: Fetching chat sessions for user");
        List<ChatSession> sessions = virtualMentorService.getUserSessions();
        List<VirtualMentorSessionResponse> response = sessions.stream()
                .map(this::mapToSessionResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/sessions/{sessionId}")
    @Operation(summary = "Rename a chat session")
    public ResponseEntity<VirtualMentorSessionResponse> renameSession(
            @PathVariable UUID sessionId,
            @RequestBody @Valid VirtualMentorRenameSessionRequest request) {
        log.info("VirtualMentorController: Renaming chat session: {}", sessionId);
        ChatSession updatedSession = virtualMentorService.renameSession(sessionId, request.getSessionName());
        return ResponseEntity.ok(mapToSessionResponse(updatedSession));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Delete a chat session and its messages")
    public ResponseEntity<Void> deleteSession(
            @PathVariable UUID sessionId) {
        log.info("VirtualMentorController: Deleting chat session: {}", sessionId);
        virtualMentorService.deleteSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions/{sessionId}/messages")
    @Operation(summary = "Get messages for a specific session")
    public ResponseEntity<List<VirtualMentorMessageResponse>> getSessionMessages(
            @PathVariable UUID sessionId) {
        log.info("VirtualMentorController: Getting messages for session: {}", sessionId);
        List<ChatMessage> messages = virtualMentorService.getSessionMessages(sessionId);
        return ResponseEntity.ok(messages.stream()
                .map(this::mapToMessageResponse)
                .collect(Collectors.toList()));
    }

    @PostMapping(value = "/sessions/{sessionId}/stream", produces = "text/event-stream;charset=UTF-8")
    @Operation(summary = "Stream chat with AI Virtual Mentor")
    public Flux<String> streamChat(
            @PathVariable UUID sessionId,
            @Valid @RequestBody VirtualMentorChatRequest request) {
        log.info("VirtualMentorController: Streaming chat for session: {}", sessionId);
        return virtualMentorService.streamChat(sessionId, request);
    }

    @PostMapping("/files/upload")
    @Operation(summary = "Upload a file for chat context",
               description = "Uploads the file to Supabase Storage and returns the public URL. Send that URL in chat when full-document extraction is needed.")
    public ResponseEntity<Map<String, String>> uploadChatFile(
            @RequestParam("file") MultipartFile file) {
        log.info("VirtualMentorController: Uploading chat file: {}", file.getOriginalFilename());
        String fileUrl = supabaseStorageService.uploadChatFile(file);
        return ResponseEntity.ok(Map.of("url", fileUrl));
    }

    @PostMapping("/knowledge/ingest")
    @Operation(summary = "Ingest a PDF document into the RAG Vector DB")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> ingestKnowledge(
            @RequestParam("file") MultipartFile file) {
        FileValidationUtil.validatePdf(file);
        // GLOBAL scope: admin knowledge is meant for every student, unlike a transcript.
        RagDocument document = ragDocumentService.startGlobalKnowledge(file);
        try {
            log.info("VirtualMentorController: Ingesting knowledge file: {}", file.getOriginalFilename());
            documentIngestionService.ingestPdfDocument(file, document);
            ragDocumentService.markCompleted(document.getDocumentId());
            return ResponseEntity.ok(Map.of("message", "Successfully ingested document into Vector Database."));
        } catch (Exception e) {
            log.error("VirtualMentorController: Failed to ingest document", e);
            ragDocumentService.markFailed(document.getDocumentId(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private VirtualMentorSessionResponse mapToSessionResponse(ChatSession session) {
        return VirtualMentorSessionResponse.builder()
                .sessionId(session.getSessionId())
                .userId(session.getUser().getUserId())
                .sessionName(session.getSessionName())
                .createAt(session.getCreatedAt())
                .build();
    }

    private VirtualMentorMessageResponse mapToMessageResponse(ChatMessage message) {
        return VirtualMentorMessageResponse.builder()
                .messageId(message.getMessageId())
                .sessionId(message.getChatSession().getSessionId())
                .role(message.getRole())
                .content(message.getContent())
                .createAt(message.getCreatedAt())
                .build();
    }
}
