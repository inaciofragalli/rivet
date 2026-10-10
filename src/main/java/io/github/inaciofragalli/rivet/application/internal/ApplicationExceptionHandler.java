package io.github.inaciofragalli.rivet.application.internal;

import io.github.inaciofragalli.rivet.application.ApplicationAlreadyExistsException;
import io.github.inaciofragalli.rivet.application.ApplicationNotFoundException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(
            DataIntegrityViolationException e
    ) {
        Throwable cause = e;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && "uk_applications_name".equals(violation.getConstraintName())) {

                ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
                problem.setTitle("Application already exists");
                problem.setDetail("An application with this name already exists");

                return problem;
            }

            cause = cause.getCause();
        }

        throw Objects.requireNonNull(e, "Exception must not be null");
    }
}
