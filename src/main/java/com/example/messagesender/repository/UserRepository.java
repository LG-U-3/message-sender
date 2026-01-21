package com.example.messagesender.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.messagesender.domain.user.User;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmail(String email);

  Optional<User> findByPhone(String phone);

  boolean existsByEmail(String email);

  boolean existsByPhone(String phone);
}
