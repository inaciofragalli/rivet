package io.github.inaciofragalli.rivet.security.internal;

import io.github.inaciofragalli.rivet.user.UserProvisioning;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class InitialAdminProvisionerTest {
    private static final String EMAIL = "admin@test.local";
    private static final String PASSWORD = "TestPassword123!";
    private static final String HASH = "encoded-test-password";

    private UserProvisioning provisioning;
    private PasswordEncoder encoder;
    private InitialAdminProvisioner runner;
    private ApplicationArguments args;

    @BeforeEach
    void setUp() {
        provisioning = mock(UserProvisioning.class);
        encoder = mock(PasswordEncoder.class);
        args = mock(ApplicationArguments.class);

        runner = new InitialAdminProvisioner(provisioning, encoder);
        ReflectionTestUtils.setField(runner, "email", EMAIL);
        ReflectionTestUtils.setField(runner, "password", PASSWORD);
    }

    @Test
    void shouldCreateFirstAdminUsingPasswordHash() throws Exception {
        when(provisioning.hasUsers()).thenReturn(false);
        when(encoder.encode(PASSWORD)).thenReturn(HASH);

        runner.run(args);

        verify(encoder).encode(PASSWORD);
        verify(provisioning).createInitialAdmin(EMAIL, HASH);
    }

    @Test
    void shouldSkipProvisioningWhenUsersAlreadyExist() throws Exception {
        when(provisioning.hasUsers()).thenReturn(true);

        runner.run(args);

        verifyNoInteractions(encoder);
        verify(provisioning, never()).createInitialAdmin(anyString(), anyString());
    }

    @Test
    void shouldFailStartupWhenEmailIsMissingAndThereAreNoUsers() {
        when(provisioning.hasUsers()).thenReturn(false);
        ReflectionTestUtils.setField(runner, "email", " ");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> runner.run(args)
        );

        assertTrue(exception.getMessage().contains("Initial admin credentials"));
        verifyNoInteractions(encoder);
    }

    @Test
    void shouldFailStartupWhenPasswordIsMissingAndThereAreNoUsers() {
        when(provisioning.hasUsers()).thenReturn(false);
        ReflectionTestUtils.setField(runner, "password", "");

        assertThrows(IllegalStateException.class, () -> runner.run(args));
        verifyNoInteractions(encoder);
    }
}