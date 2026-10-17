package io.github.inaciofragalli.rivet.server.internal;

import io.github.inaciofragalli.rivet.server.ServerAlreadyExistsException;
import io.github.inaciofragalli.rivet.server.ServerConnectionException;
import io.github.inaciofragalli.rivet.server.ServerNotFoundException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

@RestControllerAdvice(basePackageClasses = ServerController.class)
public class ServerExceptionHandler {
    @ExceptionHandler(ServerAlreadyExistsException.class)
    public ProblemDetail handleServerAlreadyExistsException(ServerAlreadyExistsException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.CONFLICT);

        problemDetail.setTitle("Server already exists");
        problemDetail.setDetail(e.getMessage());

        return  problemDetail;
    }

    @ExceptionHandler(ServerNotFoundException.class)
    public ProblemDetail handleServerNotFoundException(ServerNotFoundException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problemDetail.setTitle("Server not found");
        problemDetail.setDetail(e.getMessage());

        return  problemDetail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        Throwable cause = e;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && "uk_servers_name".equals(violation.getConstraintName())) {

                ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
                problem.setTitle("Server already exists");
                problem.setDetail("A server with this name already exists");

                return problem;
            }

            cause = cause.getCause();
        }

        throw Objects.requireNonNull(e, "Exception must not be null");
    }

    @ExceptionHandler(ServerConnectionException.class)
    public ProblemDetail handleServerConnectionException(ServerConnectionException e) {
        ProblemDetail problemDetail = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);

        problemDetail.setTitle("SSH connection failed");
        problemDetail.setDetail("Could not establish a secure SSH connection");

        return  problemDetail;
    }
}
