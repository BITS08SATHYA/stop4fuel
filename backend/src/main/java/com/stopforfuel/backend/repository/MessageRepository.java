package com.stopforfuel.backend.repository;

import com.stopforfuel.backend.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends ScidRepository<Message> {

    /** Initial page — newest 50 messages, newest first. Caller reverses for render. */
    List<Message> findTop50ByConversationIdOrderByIdDesc(Long conversationId);

    /** Keyset pagination: messages strictly older than {@code beforeId}. */
    @Query("""
        SELECT m FROM Message m
        WHERE m.conversation.id = :conversationId
          AND m.id < :beforeId
        ORDER BY m.id DESC
    """)
    List<Message> findOlderThan(@Param("conversationId") Long conversationId,
                                @Param("beforeId") Long beforeId,
                                Pageable pageable);
}
