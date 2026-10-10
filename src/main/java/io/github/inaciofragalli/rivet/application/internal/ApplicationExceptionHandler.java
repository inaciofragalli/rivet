package io.github.inaciofragalli.rivet.application.internal;

import io.github.inaciofragalli.rivet.application.ApplicationAlreadyExistsException;
import io.github.inaciofragalli.rivet.application.ApplicationNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApplicationExceptionHandler {
    @ExceptionHandler(ApplicationAlreadyExistsException.class)
    public ProblemDetail handleApplicationAlreadyExistsException(ApplicationAlreadyExistsException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);

        problemDetail.setTitle("Application already exists");
        problemDetail.setDetail(e.getMessage());

        return problemDetail;
    }

    @ExceptionHandler(ApplicationNotFoundException.class)
    public ProblemDetail handleApplicationNotFoundException(ApplicationNotFoundException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problemDetail.setTitle("Application not found");
        problemDetail.setDetail(e.getMessage());

        return problemDetail;
    }
}
