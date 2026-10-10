package io.github.inaciofragalli.rivet.application.internal;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;

import java.sql.SQLException;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;


class ApplicationExceptionHandlerTest {
    private final ApplicationExceptionHandler handler =
            new ApplicationExceptionHandler();

    @Test
    void shouldReturnConflictWhenNameUniqueConstraintIsViolated() {
        SQLException sqlException =
                new SQLException("Duplicate key", "23505");

        ConstraintViolationException constraintException =
                new ConstraintViolationException(
                        "Unique constraint violated",
                        sqlException,
                        "uk_applications_name"
                );

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Database integrity violation",
                        constraintException
                );

        ProblemDetail result =
                handler.handleDataIntegrityViolation(exception);

        assertEquals(409, result.getStatus());
        assertEquals("Application already exists", result.getTitle());
    }

    @Test
    void shouldRethrowExceptionWhenConstraintIsNotRecognized() {
        SQLException sqlException =
                new SQLException("Duplicate key", "23505");

        ConstraintViolationException constraintException =
                new ConstraintViolationException(
                        "Unique constraint violated",
                        sqlException,
                        "some_other_constraint"
                );

        DataIntegrityViolationException exception =
                new DataIntegrityViolationException(
                        "Database integrity violation",
                        constraintException
                );

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> handler.handleDataIntegrityViolation(exception)
        );

        assertSame(exception, thrown);
    }
}
