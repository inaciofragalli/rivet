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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationExceptionHandlerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationRepository applicationRepository;

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


    @Test
    void shouldHandleConcurrentRegistrationsWithSameName() throws Exception {
        String name = "race-" + UUID.randomUUID().toString().substring(0, 8);

        String requestBody = """
            {
              "name": "%s",
              "image": "taskhub:v1",
              "containerPort": 8080
            }
            """.formatted(name);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Integer> register = () -> {
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Start timeout");
            }

            return mockMvc.perform(post("/api/applications")
                            .with(user("test-user"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(register);
            Future<Integer> second = executor.submit(register);

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<Integer> statuses = Stream.of(
                            first.get(30, TimeUnit.SECONDS),
                            second.get(30, TimeUnit.SECONDS)
                    )
                    .sorted()
                    .toList();

            assertEquals(List.of(201, 409), statuses);
        }

        long count = applicationRepository.findAll().stream()
                .filter(app -> name.equals(app.getName()))
                .count();

        assertEquals(1, count);
    }

}
