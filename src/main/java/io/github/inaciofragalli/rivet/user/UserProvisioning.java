package io.github.inaciofragalli.rivet.user;

public interface UserProvisioning {
    void createInitialAdmin(String email, String passwordHash);

    boolean hasUsers();
}
