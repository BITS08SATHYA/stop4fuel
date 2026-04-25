package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Conversation;
import com.stopforfuel.backend.enums.ConversationType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationRepository extends ScidRepository<Conversation> {

    /**
     * All conversations the given user is a participant in, inside the given
     * tenant, ordered by most-recent-message first (nulls last so brand-new
     * empty conversations still show up at the top when they were just created).
     */
    @Query("""
        SELECT c FROM Conversation c
        JOIN ConversationParticipant p ON p.conversation = c
        WHERE p.userId = :userId
          AND c.scid = :scid
        ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC
    """)
    List<Conversation> findForUser(@Param("userId") Long userId, @Param("scid") Long scid);

    /**
     * Locate an existing DIRECT conversation between two specific users inside
     * a tenant. Returns a list rather than Optional to guard against historical
     * duplicates — caller picks the oldest.
     */
    @Query("""
        SELECT c FROM Conversation c
        WHERE c.type = :type
          AND c.scid = :scid
          AND c.id IN (SELECT p1.conversation.id FROM ConversationParticipant p1 WHERE p1.userId = :userA)
          AND c.id IN (SELECT p2.conversation.id FROM ConversationParticipant p2 WHERE p2.userId = :userB)
        ORDER BY c.id ASC
    """)
    List<Conversation> findDirectBetween(@Param("type") ConversationType type,
                                         @Param("scid") Long scid,
                                         @Param("userA") Long userA,
                                         @Param("userB") Long userB);
}
