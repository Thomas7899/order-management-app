// src/main/java/com/thomas/order_management/repository/UserRepository.java
package com.thomas.order_management.repository;

import com.thomas.order_management.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmail(String email);

    List<User> findAllByOrderByUsernameAsc();
}
