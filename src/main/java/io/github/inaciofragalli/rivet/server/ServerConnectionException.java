package io.github.inaciofragalli.rivet.server;

public class ServerConnectionException extends RuntimeException {
    public ServerConnectionException(Throwable cause) {
        super("Failed to establish SSH connection", cause);
    }
}
