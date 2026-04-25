package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.ConversationParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    Optional<ConversationParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);

    List<ConversationParticipant> findByConversationId(Long conversationId);

    List<ConversationParticipant> findByConversationIdIn(List<Long> conversationIds);

    /**
     * Per-conversation unread count for the given user across a set of
     * conversations. A message counts as unread when:
     *   - the caller is a participant of its conversation, AND
     *   - the caller did not send it, AND
     *   - its id is greater than the participant's lastReadMessageId (or no
     *     read marker has been set yet).
     *
     * Returns Object[] rows: [conversationId: Long, unread: Long].
     */
    @Query("""
        SELECT m.conversation.id, COUNT(m)
        FROM Message m
        JOIN ConversationParticipant p ON p.conversation = m.conversation
        WHERE p.userId = :userId
          AND m.senderUserId <> :userId
          AND (p.lastReadMessageId IS NULL OR m.id > p.lastReadMessageId)
          AND m.conversation.id IN :conversationIds
        GROUP BY m.conversation.id
    """)
    List<Object[]> countUnreadPerConversation(@Param("userId") Long userId,
                                              @Param("conversationIds") List<Long> conversationIds);

    /**
     * Total unread across every conversation the user is in (for the badge).
     */
    @Query("""
        SELECT COUNT(m)
        FROM Message m
        JOIN ConversationParticipant p ON p.conversation = m.conversation
        WHERE p.userId = :userId
          AND m.conversation.scid = :scid
          AND m.senderUserId <> :userId
          AND (p.lastReadMessageId IS NULL OR m.id > p.lastReadMessageId)
    """)
    long countTotalUnreadForUser(@Param("userId") Long userId, @Param("scid") Long scid);
}
