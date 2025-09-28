package com.thomas.order_management.controller;

import com.thomas.order_management.model.User;
import com.thomas.order_management.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Alle User abrufen
    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    // User erstellen
    @PostMapping
    public User createUser(@RequestBody User user) {
        return userRepository.save(user);
    }
}
