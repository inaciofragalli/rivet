package io.github.inaciofragalli.rivet.application.internal;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;


@ActiveProfiles("test")
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
