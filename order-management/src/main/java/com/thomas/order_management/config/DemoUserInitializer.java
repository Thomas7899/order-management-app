package com.thomas.order_management.config;

import com.thomas.order_management.service.UserService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Stellt sicher, dass die Demo-Benutzer existieren; unabhängig vom Zurücksetzen der Bewegungsdaten.
 */
@Component
@Order(0)
@ConditionalOnProperty(name = "app.demo.users-enabled", havingValue = "true", matchIfMissing = true)
public class DemoUserInitializer implements ApplicationRunner {

    private final UserService userService;

    public DemoUserInitializer(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        userService.ensureDemoUsers();
    }
}
