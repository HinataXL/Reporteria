package com.erick.soporte.repository;

import com.erick.soporte.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
}