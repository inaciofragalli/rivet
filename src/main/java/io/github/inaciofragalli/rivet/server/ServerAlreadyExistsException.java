package io.github.inaciofragalli.rivet.server;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ServerAlreadyExistsException extends RuntimeException {
    public ServerAlreadyExistsException(String message) {
        super(message);
    }
}
