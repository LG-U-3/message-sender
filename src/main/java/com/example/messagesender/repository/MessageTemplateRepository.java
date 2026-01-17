package com.example.messagesender.repository;

import com.example.messagesender.domain.message.MessageTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageTemplateRepository extends JpaRepository<MessageTemplate, Long> {

}
