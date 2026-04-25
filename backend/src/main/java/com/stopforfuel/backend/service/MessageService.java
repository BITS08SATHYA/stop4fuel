package com.stopforfuel.backend.service;

import com.stopforfuel.backend.dto.ContactDTO;
import com.stopforfuel.backend.dto.ConversationDTO;
import com.stopforfuel.backend.dto.MessageDTO;
import com.stopforfuel.backend.entity.Conversation;
import com.stopforfuel.backend.entity.ConversationParticipant;
import com.stopforfuel.backend.entity.Employee;
import com.stopforfuel.backend.entity.Message;
import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.enums.ConversationType;
import com.stopforfuel.backend.enums.EntityStatus;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.exception.ResourceNotFoundException;
import com.stopforfuel.backend.repository.ConversationParticipantRepository;
import com.stopforfuel.backend.repository.ConversationRepository;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.backend.repository.MessageRepository;
import com.stopforfuel.backend.repository.UserRepository;
import com.stopforfuel.config.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin ↔ cashier direct messaging. Authoritative auth checks
 * (participant membership, role pair, same tenant) live here — controllers
 * only enforce {@code isAuthenticated()}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MessageService {

    private static final int MAX_TEXT_LEN = 4000;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int PREVIEW_LEN = 140;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationBroadcaster notificationBroadcaster;
    private final PushNotificationService pushNotificationService;

    // ─── Conversations ────────────────────────────────────────────

    @Transactional
    public ConversationDTO findOrCreateDirectConversation(Long recipientUserId) {
        Long callerId = SecurityUtils.getCurrentUserId();
        Long scid = SecurityUtils.getScid();
        if (callerId == null) throw new BusinessException("Not authenticated");
        if (recipientUserId == null) throw new BusinessException("recipientUserId is required");
        if (recipientUserId.equals(callerId)) throw new BusinessException("Cannot message yourself");

        User caller = userRepository.findByIdAndScid(callerId, scid)
                .orElseThrow(() -> new ResourceNotFoundException("Caller user not found"));
        User recipient = userRepository.findByIdAndScid(recipientUserId, scid)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found in this tenant"));
        assertRolePair(caller, recipient);

        Conversation conv = conversationRepository
                .findDirectBetween(ConversationType.DIRECT, scid, callerId, recipientUserId)
                .stream().findFirst()
                .orElseGet(() -> createDirectConversation(callerId, recipientUserId));

        // Emit CONVERSATION_CREATED to both so open tabs update without a refresh.
        if (conv.getLastMessageId() == null) {
            Map<Long, User> userMap = Map.of(callerId, caller, recipientUserId, recipient);
            fanoutConversationCreated(conv, callerId, recipientUserId, userMap);
        }

        Map<Long, Long> unread = unreadByConversation(callerId, List.of(conv.getId()));
        Map<Long, User> userMap = Map.of(callerId, caller, recipientUserId, recipient);
        return toConversationDto(conv, callerId, userMap, unread);
    }

    private Conversation createDirectConversation(Long callerId, Long recipientUserId) {
        Conversation conv = new Conversation();
        conv.setType(ConversationType.DIRECT);
        conv.setCreatedBy(callerId);
        Conversation saved = conversationRepository.save(conv);

        ConversationParticipant p1 = new ConversationParticipant();
        p1.setConversation(saved);
        p1.setUserId(callerId);
        ConversationParticipant p2 = new ConversationParticipant();
        p2.setConversation(saved);
        p2.setUserId(recipientUserId);
        participantRepository.save(p1);
        participantRepository.save(p2);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ConversationDTO> listConversationsForCurrentUser() {
        Long callerId = SecurityUtils.getCurrentUserId();
        Long scid = SecurityUtils.getScid();
        if (callerId == null) return List.of();

        List<Conversation> conversations = conversationRepository.findForUser(callerId, scid);
        if (conversations.isEmpty()) return List.of();

        List<Long> convIds = conversations.stream().map(Conversation::getId).toList();

        // Batch: all participants across these conversations → find "other" user ids.
        List<ConversationParticipant> allParticipants = participantRepository.findByConversationIdIn(convIds);
        Set<Long> otherUserIds = allParticipants.stream()
                .map(ConversationParticipant::getUserId)
                .filter(uid -> !uid.equals(callerId))
                .collect(Collectors.toSet());

        Map<Long, User> userMap = userRepository.findAllById(otherUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        userMap.putIfAbsent(callerId, userRepository.findById(callerId).orElse(null));

        Map<Long, Long> unread = unreadByConversation(callerId, convIds);

        List<ConversationDTO> out = new ArrayList<>(conversations.size());
        for (Conversation c : conversations) {
            out.add(toConversationDto(c, callerId, userMap, unread));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public long getTotalUnreadForCurrentUser() {
        Long callerId = SecurityUtils.getCurrentUserId();
        Long scid = SecurityUtils.getScid();
        if (callerId == null || scid == null) return 0L;
        return participantRepository.countTotalUnreadForUser(callerId, scid);
    }

    // ─── Messages ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MessageDTO> listMessages(Long conversationId, Long beforeId, Integer size) {
        Long callerId = SecurityUtils.getCurrentUserId();
        assertParticipant(callerId, conversationId);

        int pageSize = size != null ? Math.min(MAX_PAGE_SIZE, Math.max(1, size)) : DEFAULT_PAGE_SIZE;

        List<Message> messages = beforeId != null
                ? messageRepository.findOlderThan(conversationId, beforeId, PageRequest.of(0, pageSize))
                : messageRepository.findTop50ByConversationIdOrderByIdDesc(conversationId);

        // Reverse to oldest → newest for render.
        Collections.reverse(messages);

        Set<Long> senderIds = messages.stream().map(Message::getSenderUserId).collect(Collectors.toSet());
        Map<Long, User> senders = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<MessageDTO> out = new ArrayList<>(messages.size());
        for (Message m : messages) {
            out.add(toMessageDto(m, senders.get(m.getSenderUserId())));
        }
        return out;
    }

    @Transactional
    public MessageDTO sendMessage(Long conversationId, String text) {
        Long callerId = SecurityUtils.getCurrentUserId();
        assertParticipant(callerId, conversationId);

        String trimmed = text != null ? text.strip() : "";
        if (trimmed.isEmpty()) throw new BusinessException("Message text is required");
        if (trimmed.length() > MAX_TEXT_LEN) {
            throw new BusinessException("Message exceeds " + MAX_TEXT_LEN + " characters");
        }

        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));

        Message msg = new Message();
        msg.setConversation(conv);
        msg.setSenderUserId(callerId);
        msg.setText(trimmed);
        Message saved = messageRepository.save(msg);

        conv.setLastMessageId(saved.getId());
        conv.setLastMessageAt(saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now());
        conv.setLastMessagePreview(trimmed.substring(0, Math.min(PREVIEW_LEN, trimmed.length())));
        conversationRepository.save(conv);

        User sender = userRepository.findById(callerId).orElse(null);
        String senderName = displayName(sender);

        // Dispatch SSE to all other participants AND echo to the sender's other tabs.
        List<ConversationParticipant> participants = participantRepository.findByConversationId(conversationId);
        for (ConversationParticipant p : participants) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "MESSAGE_CREATED");
            payload.put("conversationId", conversationId);
            payload.put("messageId", saved.getId());
            payload.put("senderId", callerId);
            payload.put("senderName", senderName);
            payload.put("text", trimmed);
            payload.put("preview", conv.getLastMessagePreview());
            payload.put("createdAt", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : "");
            try {
                notificationBroadcaster.sendToUser(p.getUserId(), "message", payload);
            } catch (Exception ignored) {
                // SSE is best-effort — never fail a send because a client is down.
            }
        }

        // Push to the recipient (not the sender).
        participants.stream()
                .map(ConversationParticipant::getUserId)
                .filter(uid -> !uid.equals(callerId))
                .forEach(uid -> {
                    try {
                        pushNotificationService.notifyMessageCreated(uid, senderName, trimmed, conversationId);
                    } catch (Exception e) {
                        log.debug("Push for message {} to user {} failed: {}", saved.getId(), uid, e.getMessage());
                    }
                });

        return toMessageDto(saved, sender);
    }

    @Transactional
    public void markRead(Long conversationId) {
        Long callerId = SecurityUtils.getCurrentUserId();
        ConversationParticipant p = participantRepository
                .findByConversationIdAndUserId(conversationId, callerId)
                .orElseThrow(() -> new BusinessException("Not a participant of this conversation"));

        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found: " + conversationId));

        p.setLastReadAt(LocalDateTime.now());
        if (conv.getLastMessageId() != null) {
            p.setLastReadMessageId(conv.getLastMessageId());
        }
        participantRepository.save(p);

        // Emit to the other participant so read-receipts could be rendered later.
        participantRepository.findByConversationId(conversationId).stream()
                .map(ConversationParticipant::getUserId)
                .filter(uid -> !uid.equals(callerId))
                .forEach(uid -> {
                    try {
                        notificationBroadcaster.sendToUser(uid, "message", Map.of(
                                "type", "MESSAGE_READ",
                                "conversationId", conversationId,
                                "userId", callerId,
                                "lastReadMessageId", p.getLastReadMessageId() != null ? p.getLastReadMessageId() : ""
                        ));
                    } catch (Exception ignored) {}
                });
    }

    // ─── Contacts ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ContactDTO> listContactsForCurrentUser() {
        Long callerId = SecurityUtils.getCurrentUserId();
        Long scid = SecurityUtils.getScid();
        if (callerId == null || scid == null) return List.of();

        User caller = userRepository.findById(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("Caller not found"));

        boolean callerIsAdmin = isAdminOrOwner(caller);
        boolean callerIsCashier = isCashier(caller);
        if (!callerIsAdmin && !callerIsCashier) {
            // Other roles not supported for messaging in MVP.
            return List.of();
        }

        Set<User> candidates = new HashSet<>();
        if (callerIsAdmin) {
            // Admins/owners see cashiers.
            candidates.addAll(findActiveCashiers(scid));
        } else {
            // Cashiers see admins and owners.
            candidates.addAll(userRepository.findByRoleRoleTypeAndScidAndStatus("ADMIN", scid, EntityStatus.ACTIVE));
            candidates.addAll(userRepository.findByRoleRoleTypeAndScidAndStatus("OWNER", scid, EntityStatus.ACTIVE));
        }

        return candidates.stream()
                .filter(u -> !u.getId().equals(callerId))
                .sorted((a, b) -> safeCompare(displayName(a), displayName(b)))
                .map(this::toContactDto)
                .toList();
    }

    // ─── Auth / role helpers ──────────────────────────────────────

    private void assertParticipant(Long userId, Long conversationId) {
        if (userId == null || conversationId == null
                || !participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new BusinessException("Not a participant of this conversation");
        }
    }

    private void assertRolePair(User a, User b) {
        if (a.getScid() == null || !a.getScid().equals(b.getScid())) {
            throw new BusinessException("Users are not in the same tenant");
        }
        boolean aAdmin = isAdminOrOwner(a);
        boolean bAdmin = isAdminOrOwner(b);
        boolean aCashier = isCashier(a);
        boolean bCashier = isCashier(b);
        boolean allowed = (aAdmin && bCashier) || (aCashier && bAdmin);
        if (!allowed) {
            throw new BusinessException("Messaging is allowed only between admins/owners and cashiers");
        }
    }

    private boolean isAdminOrOwner(User u) {
        String r = u.getRole() != null ? u.getRole().getRoleType() : null;
        return r != null && (r.equalsIgnoreCase("ADMIN") || r.equalsIgnoreCase("OWNER"));
    }

    private boolean isCashier(User u) {
        String r = u.getRole() != null ? u.getRole().getRoleType() : null;
        if (r != null && r.equalsIgnoreCase("CASHIER")) return true;
        // Designation fallback (some tenants store the cashier identity on Employee only).
        String d = designationOf(u);
        if (d != null && d.equalsIgnoreCase("Cashier")) {
            if (r != null && !r.equalsIgnoreCase("CASHIER") && !r.equalsIgnoreCase("EMPLOYEE")) {
                log.warn("User {} has designation=Cashier but role={} — treating as cashier via designation", u.getId(), r);
            }
            return true;
        }
        return false;
    }

    private String designationOf(User u) {
        // User → Employee via joined-inheritance lookup.
        if (u instanceof Employee e) return e.getDesignation();
        return employeeRepository.findById(u.getId()).map(Employee::getDesignation).orElse(null);
    }

    private List<User> findActiveCashiers(Long scid) {
        Set<User> found = new HashSet<>();
        found.addAll(userRepository.findByRoleRoleTypeAndScidAndStatus("CASHIER", scid, EntityStatus.ACTIVE));
        // Designation fallback for EMPLOYEE-role users whose designation is "Cashier".
        for (Employee e : employeeRepository.findByStatusAndScid(EntityStatus.ACTIVE, scid)) {
            if ("Cashier".equalsIgnoreCase(e.getDesignation())) {
                found.add(e);
            }
        }
        return new ArrayList<>(found);
    }

    // ─── Projection helpers ───────────────────────────────────────

    private Map<Long, Long> unreadByConversation(Long userId, List<Long> conversationIds) {
        if (conversationIds.isEmpty()) return Map.of();
        Map<Long, Long> out = new HashMap<>();
        for (Object[] row : participantRepository.countUnreadPerConversation(userId, conversationIds)) {
            out.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return out;
    }

    private ConversationDTO toConversationDto(Conversation c,
                                              Long callerId,
                                              Map<Long, User> userMap,
                                              Map<Long, Long> unread) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(c.getId());
        dto.setType(c.getType());
        dto.setCreatedAt(c.getCreatedAt());
        dto.setUpdatedAt(c.getUpdatedAt());
        dto.setLastMessageAt(c.getLastMessageAt());
        dto.setLastMessagePreview(c.getLastMessagePreview());
        dto.setUnreadCount(unread.getOrDefault(c.getId(), 0L));

        // Find the *other* user by scanning participants — fall back to a
        // per-conversation query if we didn't preload them (list path already did).
        Long otherUserId = participantRepository.findByConversationId(c.getId()).stream()
                .map(ConversationParticipant::getUserId)
                .filter(uid -> !uid.equals(callerId))
                .findFirst()
                .orElse(null);
        if (otherUserId != null) {
            User other = userMap.get(otherUserId);
            if (other == null) other = userRepository.findById(otherUserId).orElse(null);
            if (other != null) dto.setOtherParticipant(toContactDto(other));
        }
        return dto;
    }

    private MessageDTO toMessageDto(Message m, User sender) {
        MessageDTO dto = new MessageDTO();
        dto.setId(m.getId());
        dto.setConversationId(m.getConversation() != null ? m.getConversation().getId() : null);
        dto.setSenderUserId(m.getSenderUserId());
        dto.setSenderName(displayName(sender));
        dto.setText(m.getText());
        dto.setCreatedAt(m.getCreatedAt());
        return dto;
    }

    private ContactDTO toContactDto(User u) {
        ContactDTO dto = new ContactDTO();
        dto.setUserId(u.getId());
        dto.setName(displayName(u));
        dto.setRole(u.getRole() != null ? u.getRole().getRoleType() : null);
        dto.setDesignation(designationOf(u));
        dto.setStatus(u.getStatus() != null ? u.getStatus().name() : null);
        return dto;
    }

    private void fanoutConversationCreated(Conversation conv, Long userA, Long userB, Map<Long, User> userMap) {
        for (Long uid : List.of(userA, userB)) {
            Long other = uid.equals(userA) ? userB : userA;
            User otherUser = userMap.get(other);
            if (otherUser == null) continue;
            Map<String, Object> otherParticipant = new HashMap<>();
            otherParticipant.put("userId", otherUser.getId());
            otherParticipant.put("name", displayName(otherUser));
            otherParticipant.put("role", otherUser.getRole() != null ? otherUser.getRole().getRoleType() : null);
            otherParticipant.put("designation", designationOf(otherUser));
            try {
                notificationBroadcaster.sendToUser(uid, "message", Map.of(
                        "type", "CONVERSATION_CREATED",
                        "conversationId", conv.getId(),
                        "otherParticipant", otherParticipant
                ));
            } catch (Exception ignored) {}
        }
    }

    private static String displayName(User u) {
        if (u == null) return "Unknown";
        String name = u.getName();
        if (name == null || name.isBlank()) name = u.getUsername();
        return name != null && !name.isBlank() ? name : ("User #" + u.getId());
    }

    private static int safeCompare(String a, String b) {
        return Optional.ofNullable(a).orElse("").compareToIgnoreCase(Optional.ofNullable(b).orElse(""));
    }
}
