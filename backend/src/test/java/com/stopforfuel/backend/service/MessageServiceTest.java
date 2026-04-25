package com.stopforfuel.backend.service;

import com.stopforfuel.backend.entity.Conversation;
import com.stopforfuel.backend.entity.ConversationParticipant;
import com.stopforfuel.backend.entity.Message;
import com.stopforfuel.backend.entity.Roles;
import com.stopforfuel.backend.entity.User;
import com.stopforfuel.backend.enums.ConversationType;
import com.stopforfuel.backend.exception.BusinessException;
import com.stopforfuel.backend.repository.ConversationParticipantRepository;
import com.stopforfuel.backend.repository.ConversationRepository;
import com.stopforfuel.backend.repository.EmployeeRepository;
import com.stopforfuel.backend.repository.MessageRepository;
import com.stopforfuel.backend.repository.UserRepository;
import com.stopforfuel.config.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Auth-boundary tests for MessageService. These are the load-bearing checks
 * that prevent cross-tenant or role-mismatched chats — test them before
 * anything else.
 */
@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationParticipantRepository participantRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private NotificationBroadcaster notificationBroadcaster;
    @Mock private PushNotificationService pushNotificationService;

    @InjectMocks private MessageService service;

    private MockedStatic<SecurityUtils> security;

    private static final Long ADMIN_ID = 10L;
    private static final Long CASHIER_ID = 20L;
    private static final Long OTHER_CASHIER_ID = 21L;
    private static final Long SCID = 1L;
    private static final Long OTHER_SCID = 2L;

    @BeforeEach
    void setUp() {
        security = Mockito.mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getCurrentUserId).thenReturn(ADMIN_ID);
        security.when(SecurityUtils::getScid).thenReturn(SCID);
    }

    @AfterEach
    void tearDown() {
        security.close();
    }

    private User userWith(Long id, Long scid, String roleType) {
        User u = new User();
        u.setId(id);
        u.setScid(scid);
        u.setName("User " + id);
        Roles role = new Roles();
        role.setRoleType(roleType);
        u.setRole(role);
        return u;
    }

    @Test
    void startDirectConversation_rejectsCrossTenant() {
        User admin = userWith(ADMIN_ID, SCID, "ADMIN");

        when(userRepository.findByIdAndScid(ADMIN_ID, SCID)).thenReturn(Optional.of(admin));
        // Recipient is not findable in the caller's scid — this is what actually protects us
        // against cross-tenant access; simulate that.
        when(userRepository.findByIdAndScid(CASHIER_ID, SCID)).thenReturn(Optional.empty());

        assertThrows(Exception.class,
                () -> service.findOrCreateDirectConversation(CASHIER_ID),
                "Cross-tenant start must be rejected");
    }

    @Test
    void startDirectConversation_rejectsCashierToCashier() {
        security.when(SecurityUtils::getCurrentUserId).thenReturn(CASHIER_ID);
        User caller = userWith(CASHIER_ID, SCID, "CASHIER");
        User other = userWith(OTHER_CASHIER_ID, SCID, "CASHIER");

        when(userRepository.findByIdAndScid(CASHIER_ID, SCID)).thenReturn(Optional.of(caller));
        when(userRepository.findByIdAndScid(OTHER_CASHIER_ID, SCID)).thenReturn(Optional.of(other));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.findOrCreateDirectConversation(OTHER_CASHIER_ID));
        assertTrue(ex.getMessage().toLowerCase().contains("admins"));
    }

    @Test
    void startDirectConversation_rejectsSelf() {
        assertThrows(BusinessException.class,
                () -> service.findOrCreateDirectConversation(ADMIN_ID));
    }

    @Test
    void sendMessage_rejectsNonParticipant() {
        when(participantRepository.existsByConversationIdAndUserId(99L, ADMIN_ID)).thenReturn(false);
        assertThrows(BusinessException.class,
                () -> service.sendMessage(99L, "hello"));
    }

    @Test
    void sendMessage_rejectsBlankText() {
        when(participantRepository.existsByConversationIdAndUserId(5L, ADMIN_ID)).thenReturn(true);
        assertThrows(BusinessException.class,
                () -> service.sendMessage(5L, "   "));
    }

    @Test
    void sendMessage_rejectsTooLong() {
        when(participantRepository.existsByConversationIdAndUserId(5L, ADMIN_ID)).thenReturn(true);
        String tooLong = "a".repeat(4001);
        assertThrows(BusinessException.class,
                () -> service.sendMessage(5L, tooLong));
    }

    @Test
    void sendMessage_happyPath_fansOutSse() {
        User admin = userWith(ADMIN_ID, SCID, "ADMIN");
        Conversation conv = new Conversation();
        conv.setId(7L);
        conv.setType(ConversationType.DIRECT);
        conv.setScid(SCID);

        ConversationParticipant pAdmin = new ConversationParticipant();
        pAdmin.setConversation(conv);
        pAdmin.setUserId(ADMIN_ID);
        ConversationParticipant pCashier = new ConversationParticipant();
        pCashier.setConversation(conv);
        pCashier.setUserId(CASHIER_ID);

        when(participantRepository.existsByConversationIdAndUserId(7L, ADMIN_ID)).thenReturn(true);
        when(conversationRepository.findById(7L)).thenReturn(Optional.of(conv));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId(101L);
            return m;
        });
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(participantRepository.findByConversationId(7L)).thenReturn(List.of(pAdmin, pCashier));

        service.sendMessage(7L, "pump 3 stuck");

        // Recipient + sender both receive the SSE fan-out.
        verify(notificationBroadcaster, times(2))
                .sendToUser(anyLong(), eq("message"), any());
        // Push fires only for the non-sender participant.
        verify(pushNotificationService, times(1))
                .notifyMessageCreated(eq(CASHIER_ID), anyString(), eq("pump 3 stuck"), eq(7L));
    }

    @Test
    void markRead_rejectsNonParticipant() {
        when(participantRepository.findByConversationIdAndUserId(5L, ADMIN_ID))
                .thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.markRead(5L));
    }
}
