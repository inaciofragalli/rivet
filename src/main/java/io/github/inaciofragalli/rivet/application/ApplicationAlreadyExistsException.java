package io.github.inaciofragalli.rivet.application;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ApplicationAlreadyExistsException extends RuntimeException {
    public ApplicationAlreadyExistsException(String name) {
        super("Application with name " + name + " already exists");
    }
}
