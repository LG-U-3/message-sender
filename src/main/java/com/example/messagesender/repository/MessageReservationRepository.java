package com.example.messagesender.repository;

import com.example.messagesender.domain.message.MessageReservation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReservationRepository extends JpaRepository<MessageReservation, Long> {

}
