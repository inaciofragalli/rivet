package io.github.inaciofragalli.rivet.deployment.internal;

import io.github.inaciofragalli.rivet.TestcontainersConfiguration;
import io.github.inaciofragalli.rivet.deployment.DeploymentAlreadyInProgressException;
import io.github.inaciofragalli.rivet.deployment.DeploymentResponse;
import io.github.inaciofragalli.rivet.deployment.DeploymentService;
import io.github.inaciofragalli.rivet.deployment.DeploymentStatus;
import io.github.inaciofragalli.rivet.deployment.InvalidDeploymentTransitionException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DeploymentPersistenceIntegrationTest {

    @Autowired
    private DeploymentRepository repository;

    @Autowired
    private DeploymentService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private UUID applicationId;
    private UUID serverId;
    private UUID artifactId;

    @BeforeEach
    void createReferences() {
        applicationId = UUID.randomUUID();
        serverId = UUID.randomUUID();
        artifactId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO image_artifacts
                    (id, image_name, image_tag, architecture, os, archive_size_bytes, sha256)
                VALUES (?, 'example/app', 'v1', 'amd64', 'linux', 1024, ?)
                """, artifactId, "a".repeat(64));
        jdbc.update("""
                INSERT INTO applications (id, name, image, container_port, image_artifact_id)
                VALUES (?, ?, 'example/app:v1', 8080, ?)
                """, applicationId, "deploy-" + applicationId.toString().substring(0, 12), artifactId);
        jdbc.update("""
                INSERT INTO servers (id, name, host, ssh_port, ssh_username)
                VALUES (?, ?, 'localhost', 22, 'rivet')
                """, serverId, "deploy-" + serverId.toString().substring(0, 12));
    }

    @AfterEach
    void cleanUpReferences() {
        // Only remove this test's fixtures, in foreign-key order.
        jdbc.update("DELETE FROM deployments WHERE application_id = ? OR server_id = ?", applicationId, serverId);
        jdbc.update("DELETE FROM applications WHERE id = ?", applicationId);
        jdbc.update("DELETE FROM servers WHERE id = ?", serverId);
        jdbc.update("DELETE FROM image_artifacts WHERE id = ?", artifactId);
    }

    @Test
    void shouldApplyDeploymentMigrationSuccessfully() {
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM flyway_schema_history
                WHERE version = '7' AND success = true AND script = 'V7__create_deployments.sql'
                """, Integer.class));
    }

    @Test
    void shouldPersistSnapshotAndRecoverDatabaseGeneratedTimestamps() {
        Instant before = databaseNow();
        Deployment saved = repository.saveAndFlush(newDeployment());
        Deployment recovered = repository.findById(saved.getId()).orElseThrow();
        Instant after = databaseNow();

        assertAll(
                () -> assertEquals(applicationId, recovered.getApplicationId()),
                () -> assertEquals(serverId, recovered.getServerId()),
                () -> assertEquals(artifactId, recovered.getImageArtifactId()),
                () -> assertEquals("example/app:v1", recovered.getImageReference()),
                () -> assertEquals(8080, recovered.getContainerPort()),
                () -> assertEquals(DeploymentStatus.PENDING, recovered.getStatus()),
                () -> assertNotNull(recovered.getVersion()),
                () -> assertTimestampBetween(recovered.getRequestedAt(), before, after),
                () -> assertTimestampBetween(recovered.getUpdatedAt(), before, after)
        );
    }

    @Test
    void shouldAllowDeploymentsWithoutAnImageArtifact() {
        Deployment saved = repository.saveAndFlush(new Deployment(
                applicationId, serverId, null, "registry/app:v2", 80));

        assertNull(repository.findById(saved.getId()).orElseThrow().getImageArtifactId());
    }

    @Test
    void shouldApplyDatabaseDefaultsWhenOptionalColumnsAreOmitted() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO deployments (id, application_id, server_id, image_reference, container_port)
                VALUES (?, ?, ?, 'example/app:v1', 8080)
                """, id, applicationId, serverId);

        Deployment recovered = repository.findById(id).orElseThrow();
        assertAll(
                () -> assertEquals(DeploymentStatus.PENDING, recovered.getStatus()),
                () -> assertEquals(0L, recovered.getVersion()),
                () -> assertNotNull(recovered.getRequestedAt()),
                () -> assertNotNull(recovered.getUpdatedAt()),
                () -> assertNull(recovered.getImageArtifactId())
        );
    }

    @Test
    void shouldReturnGeneratedTimestampsWhenCreatingThroughService() {
        DeploymentResponse response = service.createDeployment(applicationId, serverId);

        assertAll(
                () -> assertNotNull(response.requestedAt()),
                () -> assertNotNull(response.updatedAt())
        );
    }

    @Test
    void shouldKeepSnapshotWhenApplicationConfigurationChanges() {
        DeploymentResponse response = service.createDeployment(applicationId, serverId);
        jdbc.update("""
                UPDATE applications SET image = 'example/app:v2', container_port = 9090,
                    image_artifact_id = NULL WHERE id = ?
                """, applicationId);

        DeploymentResponse recovered = service.findById(response.id());
        assertAll(
                () -> assertEquals("example/app:v1", recovered.imageReference()),
                () -> assertEquals(artifactId, recovered.imageArtifactId()),
                () -> assertEquals(8080, recovered.containerPort())
        );
    }

    @ParameterizedTest
    @EnumSource(DeploymentStatus.class)
    void shouldStoreEachStatusAsAString(DeploymentStatus status) {
        UUID id = insertDeployment(status, Instant.parse("2026-01-01T00:00:00Z"));

        assertEquals(status.name(), jdbc.queryForObject(
                "SELECT status FROM deployments WHERE id = ?", String.class, id));
        assertEquals(status, repository.findById(id).orElseThrow().getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"application_id", "server_id", "image_artifact_id"})
    void shouldEnforceForeignKeys(String column) {
        UUID id = repository.saveAndFlush(newDeployment()).getId();

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE deployments SET " + column + " = ? WHERE id = ?", UUID.randomUUID(), id));
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "application_id", "server_id", "image_reference", "container_port",
            "status", "requested_at", "updated_at", "version"})
    void shouldEnforceRequiredColumns(String column) {
        UUID id = repository.saveAndFlush(newDeployment()).getId();

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE deployments SET " + column + " = NULL WHERE id = ?", id));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 65536})
    void shouldRejectPortsOutsideAllowedRange(int port) {
        UUID id = repository.saveAndFlush(newDeployment()).getId();

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE deployments SET container_port = ? WHERE id = ?", port, id));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 65535})
    void shouldAcceptPortRangeBoundaries(int port) {
        Deployment saved = repository.saveAndFlush(new Deployment(
                applicationId, serverId, null, "example/app:v1", port));

        assertEquals(port, repository.findById(saved.getId()).orElseThrow().getContainerPort());
    }

    @Test
    void shouldRejectUnknownStatusAtDatabaseLevel() {
        UUID id = repository.saveAndFlush(newDeployment()).getId();

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE deployments SET status = 'UNKNOWN' WHERE id = ?", id));
    }

    @ParameterizedTest
    @ValueSource(strings = {"container_id", "error_code"})
    void shouldEnforceMetadataLengthLimits(String column) {
        UUID id = repository.saveAndFlush(newDeployment()).getId();

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE deployments SET " + column + " = ? WHERE id = ?", "a".repeat(65), id));
    }

    @ParameterizedTest
    @EnumSource(value = DeploymentStatus.class,
            names = {"PENDING", "TRANSFERRING", "STARTING", "VERIFYING", "INTERRUPTED"})
    void shouldEnforcePartialUniqueIndexForEveryBlockingStatus(DeploymentStatus status) {
        insertDeployment(status, Instant.parse("2026-01-01T00:00:00Z"));

        assertThrows(DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(newDeployment()));
    }

    @ParameterizedTest
    @EnumSource(value = DeploymentStatus.class, names = {"SUCCESS", "FAILED"})
    void shouldAllowAnotherAttemptAfterTerminalState(DeploymentStatus status) {
        insertDeployment(status, Instant.parse("2026-01-01T00:00:00Z"));

        DeploymentResponse retry = service.createDeployment(applicationId, serverId);

        assertEquals(DeploymentStatus.PENDING, retry.status());
        assertEquals(2, repository.findByApplicationId(applicationId).size());
    }

    @Test
    void shouldRejectServiceCreationWhileInterrupted() {
        insertDeployment(DeploymentStatus.INTERRUPTED, Instant.parse("2026-01-01T00:00:00Z"));

        assertThrows(DeploymentAlreadyInProgressException.class,
                () -> service.createDeployment(applicationId, serverId));
        assertEquals(1, repository.findByApplicationId(applicationId).size());
    }

    @Test
    void shouldAllowActiveDeploymentsForDifferentApplicationAndServerPairs() {
        repository.saveAndFlush(newDeployment());
        UUID otherApplicationId = UUID.randomUUID();
        UUID otherServerId = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO applications (id, name, image, container_port)
                    VALUES (?, ?, 'example/other:v1', 80)
                    """, otherApplicationId, "deploy-" + otherApplicationId.toString().substring(0, 12));
            jdbc.update("""
                    INSERT INTO servers (id, name, host, ssh_port, ssh_username)
                    VALUES (?, ?, 'other.local', 22, 'rivet')
                    """, otherServerId, "deploy-" + otherServerId.toString().substring(0, 12));

            Deployment otherServer = repository.saveAndFlush(new Deployment(
                    applicationId, otherServerId, null, "example/app:v1", 8080));
            Deployment otherApplication = repository.saveAndFlush(new Deployment(
                    otherApplicationId, serverId, null, "example/other:v1", 80));

            assertEquals(DeploymentStatus.PENDING, repository.findById(otherServer.getId()).orElseThrow().getStatus());
            assertEquals(DeploymentStatus.PENDING, repository.findById(otherApplication.getId()).orElseThrow().getStatus());
        } finally {
            jdbc.update("DELETE FROM deployments WHERE application_id = ? OR server_id = ?",
                    otherApplicationId, otherServerId);
            jdbc.update("DELETE FROM applications WHERE id = ?", otherApplicationId);
            jdbc.update("DELETE FROM servers WHERE id = ?", otherServerId);
        }
    }

    @Test
    void shouldReadMultipleAttemptsAndExcludeOtherApplications() {
        UUID older = insertDeployment(DeploymentStatus.FAILED, Instant.parse("2026-01-01T00:00:00Z"));
        UUID newer = insertDeployment(DeploymentStatus.SUCCESS, Instant.parse("2026-01-02T00:00:00Z"));

        List<DeploymentResponse> history = service.findByApplicationId(applicationId);

        assertEquals(2, history.size());
        assertEquals(java.util.Set.of(older, newer), history.stream()
                .map(DeploymentResponse::id).collect(java.util.stream.Collectors.toSet()));
        assertTrue(service.findByApplicationId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void shouldCommitServiceTransitionsWithoutAnOuterTestTransaction() {
        UUID id = service.createDeployment(applicationId, serverId).id();
        Instant requestedAt = service.findById(id).requestedAt();

        service.startTransfer(id);
        DeploymentResponse transferring = service.findById(id);
        assertEquals(DeploymentStatus.TRANSFERRING, transferring.status());
        assertNotNull(transferring.startedAt());

        service.startContainer(id);
        assertEquals(DeploymentStatus.STARTING, service.findById(id).status());

        EntityManager manager = entityManagerFactory.createEntityManager();
        try {
            manager.getTransaction().begin();
            manager.find(Deployment.class, id).recordContainerId("a".repeat(64));
            manager.getTransaction().commit();
        } finally {
            close(manager);
        }

        service.startVerification(id);
        assertEquals(DeploymentStatus.VERIFYING, service.findById(id).status());
        service.markSuccess(id);

        DeploymentResponse finished = service.findById(id);
        assertAll(
                () -> assertEquals(DeploymentStatus.SUCCESS, finished.status()),
                () -> assertEquals("a".repeat(64), finished.containerId()),
                () -> assertEquals(requestedAt, finished.requestedAt()),
                () -> assertEquals(transferring.startedAt(), finished.startedAt()),
                () -> assertNotNull(finished.finishedAt()),
                () -> assertFalse(finished.updatedAt().isBefore(transferring.updatedAt()))
        );
    }

    @Test
    void shouldPersistFailureAndRollBackAnInvalidServiceTransition() {
        UUID id = service.createDeployment(applicationId, serverId).id();
        service.startTransfer(id);
        service.markFailed(id, "TRANSFER_ERROR", "Transfer failed");
        Deployment before = repository.findById(id).orElseThrow();

        assertThrows(InvalidDeploymentTransitionException.class, () -> service.startContainer(id));

        Deployment after = repository.findById(id).orElseThrow();
        assertAll(
                () -> assertEquals(DeploymentStatus.FAILED, after.getStatus()),
                () -> assertEquals("TRANSFER_ERROR", after.getErrorCode()),
                () -> assertEquals("Transfer failed", after.getErrorMessage()),
                () -> assertNotNull(after.getFinishedAt()),
                () -> assertEquals(before.getVersion(), after.getVersion()),
                () -> assertEquals(before.getUpdatedAt(), after.getUpdatedAt())
        );
    }

    @Test
    void shouldRejectAStaleUpdateFromAnIndependentPersistenceContext() {
        UUID id = service.createDeployment(applicationId, serverId).id();
        service.startTransfer(id);
        service.startContainer(id);
        service.startVerification(id);

        EntityManager first = entityManagerFactory.createEntityManager();
        EntityManager second = entityManagerFactory.createEntityManager();
        long originalVersion;
        try {
            first.getTransaction().begin();
            second.getTransaction().begin();
            Deployment winner = first.find(Deployment.class, id);
            Deployment stale = second.find(Deployment.class, id);
            assertNotSame(winner, stale);
            originalVersion = winner.getVersion();
            assertEquals(originalVersion, stale.getVersion());

            winner.markSuccess();
            first.getTransaction().commit();

            stale.markFailed("STALE_FAILURE", "Must not overwrite success");
            assertThrows(OptimisticLockException.class, second::flush);
        } finally {
            close(second);
            close(first);
        }

        Deployment persisted = repository.findById(id).orElseThrow();
        assertAll(
                () -> assertEquals(DeploymentStatus.SUCCESS, persisted.getStatus()),
                () -> assertEquals(originalVersion + 1, persisted.getVersion()),
                () -> assertNull(persisted.getErrorCode()),
                () -> assertNull(persisted.getErrorMessage())
        );
    }

    private Deployment newDeployment() {
        return new Deployment(applicationId, serverId, artifactId, "example/app:v1", 8080);
    }

    private UUID insertDeployment(DeploymentStatus status, Instant requestedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO deployments
                    (id, application_id, server_id, image_artifact_id,
                     image_reference, container_port, status, requested_at)
                VALUES (?, ?, ?, ?, 'example/app:v1', 8080, ?, ?)
                """, id, applicationId, serverId, artifactId, status.name(), Timestamp.from(requestedAt));
        return id;
    }

    private Instant databaseNow() {
        return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class).toInstant();
    }

    private static void assertTimestampBetween(Instant value, Instant before, Instant after) {
        assertNotNull(value);
        assertFalse(value.isBefore(before), "Timestamp precedes insertion");
        assertFalse(value.isAfter(after), "Timestamp follows retrieval");
    }

    private static void close(EntityManager manager) {
        if (manager.getTransaction().isActive()) {
            manager.getTransaction().rollback();
        }
        manager.close();
    }
}
