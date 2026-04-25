package com.stopforfuel.backend.controller;

import com.stopforfuel.backend.dto.ContactDTO;
import com.stopforfuel.backend.dto.ConversationDTO;
import com.stopforfuel.backend.dto.MessageDTO;
import com.stopforfuel.backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Messaging API under {@code /api/messaging}. Per-conversation access
 * (membership, role pair, tenant) is enforced by {@link MessageService};
 * controllers only require authentication.
 */
@RestController
@RequestMapping("/api/messaging")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class MessagingController {

    private final MessageService messageService;

    @GetMapping("/conversations")
    public List<ConversationDTO> listConversations() {
        return messageService.listConversationsForCurrentUser();
    }

    @PostMapping("/conversations")
    public ConversationDTO startConversation(@RequestBody StartRequest body) {
        return messageService.findOrCreateDirectConversation(body.recipientUserId());
    }

    @GetMapping("/conversations/{id}/messages")
    public List<MessageDTO> listMessages(@PathVariable("id") Long conversationId,
                                         @RequestParam(value = "before", required = false) Long before,
                                         @RequestParam(value = "size", required = false) Integer size) {
        return messageService.listMessages(conversationId, before, size);
    }

    @PostMapping("/conversations/{id}/messages")
    public MessageDTO sendMessage(@PathVariable("id") Long conversationId,
                                  @RequestBody SendRequest body) {
        return messageService.sendMessage(conversationId, body != null ? body.text() : null);
    }

    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<Void> markRead(@PathVariable("id") Long conversationId) {
        messageService.markRead(conversationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/contacts")
    public List<ContactDTO> listContacts() {
        return messageService.listContactsForCurrentUser();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", messageService.getTotalUnreadForCurrentUser());
    }

    public record StartRequest(Long recipientUserId) {}
    public record SendRequest(String text) {}
}
