package io.github.inaciofragalli.rivet.security.internal;

import io.github.inaciofragalli.rivet.user.UserProvisioning;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InitialAdminProvisioner implements ApplicationRunner {
    private final UserProvisioning userProvisioning;
    private final PasswordEncoder passwordEncoder;

    @Value("${rivet.bootstrap.email:}")
    private String email;

    @Value("${rivet.bootstrap.password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (userProvisioning.hasUsers()) {
            return;
        }

        if (email.isBlank() || password.isBlank()) {
            throw new IllegalStateException("Initial admin credentials are required");
        }

        String passwordHash = passwordEncoder.encode(password);

        userProvisioning.createInitialAdmin(email, passwordHash);
    }
}
