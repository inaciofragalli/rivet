package io.github.inaciofragalli.rivet.user.internal;

import io.github.inaciofragalli.rivet.TestcontainersConfiguration;
import io.github.inaciofragalli.rivet.user.UserAuthData;
import io.github.inaciofragalli.rivet.user.UserLookup;
import io.github.inaciofragalli.rivet.user.UserProvisioning;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class UserProvisioningIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProvisioning userProvisioning;

    @Autowired
    private UserLookup userLookup;

    @Test
    void shouldCreateTheInitialAdminAndExposeOnlyAuthData() {
        // Rollback ao final do teste; o admin criado na inicialização dos testes permanece.
        userRepository.deleteAll();
        userRepository.flush();

        assertFalse(userProvisioning.hasUsers());

        userProvisioning.createInitialAdmin("first@test.local", "stored-hash");

        assertTrue(userProvisioning.hasUsers());
        assertEquals(1, userRepository.count());

        UserAuthData result = userLookup.findByEmail("first@test.local").orElseThrow();
        assertEquals("first@test.local", result.email());
        assertEquals("stored-hash", result.passwordHash());
    }

    @Test
    void shouldNotCreateASecondAdminWhenAnyUserAlreadyExists() {
        assertTrue(userProvisioning.hasUsers());

        long before = userRepository.count();
        userProvisioning.createInitialAdmin("second@test.local", "stored-hash");

        assertEquals(before, userRepository.count());
        assertTrue(userLookup.findByEmail("second@test.local").isEmpty());
    }

    @Test
    void shouldNormalizeEmailWhenCreatingInitialAdminAndLookingUp() {
        userRepository.deleteAll();
        userRepository.flush();

        userProvisioning.createInitialAdmin("  Admin@Test.Local  ", "stored-hash");

        User persistedUser = userRepository.findAll().getFirst();
        assertEquals("admin@test.local", persistedUser.getEmail());

        UserAuthData byUppercaseWithWhitespace = userLookup.findByEmail("  ADMIN@TEST.LOCAL  ").orElseThrow();
        assertEquals("admin@test.local", byUppercaseWithWhitespace.email());

        UserAuthData byNormalized = userLookup.findByEmail("admin@test.local").orElseThrow();
        assertEquals("admin@test.local", byNormalized.email());
    }
}

