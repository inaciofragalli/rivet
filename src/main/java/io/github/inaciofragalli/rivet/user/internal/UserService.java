package io.github.inaciofragalli.rivet.user.internal;

import io.github.inaciofragalli.rivet.user.UserAuthData;
import io.github.inaciofragalli.rivet.user.UserLookup;
import io.github.inaciofragalli.rivet.user.UserProvisioning;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService implements UserLookup, UserProvisioning {
    private final UserRepository userRepository;

    @Override
    public Optional<UserAuthData> findByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(user -> new UserAuthData(
                        user.getId(),
                        user.getEmail(),
                        user.getPasswordHash()
                ));
    }

    @Override
    @Transactional
    public void createInitialAdmin(String email, String passwordHash) {
        if (userRepository.count() == 0) {
            userRepository.save(new User(email, passwordHash));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasUsers() {
        return userRepository.count() > 0;
    }
}
