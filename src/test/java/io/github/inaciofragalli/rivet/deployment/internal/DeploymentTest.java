package io.github.inaciofragalli.rivet.deployment.internal;

import io.github.inaciofragalli.rivet.deployment.DeploymentStatus;
import io.github.inaciofragalli.rivet.deployment.InvalidDeploymentTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DeploymentTest {
    private static final String CONTAINER_ID = "0123456789abcdef".repeat(4);

    @Test
    void shouldStartPendingAndPreserveDeploymentSnapshot() {
        UUID applicationId = UUID.randomUUID();
        UUID serverId = UUID.randomUUID();
        UUID imageArtifactId = UUID.randomUUID();
        String imageReference = "registry.example/app:1.2.3";
        Integer containerPort = 8080;

        Deployment deployment = new Deployment(applicationId, serverId, imageArtifactId, imageReference, containerPort);

        assertNull(deployment.getId());
        assertEquals(applicationId, deployment.getApplicationId());
        assertEquals(serverId, deployment.getServerId());
        assertEquals(imageArtifactId, deployment.getImageArtifactId());
        assertEquals(imageReference, deployment.getImageReference());
        assertEquals(containerPort, deployment.getContainerPort());
        assertEquals(DeploymentStatus.PENDING, deployment.getStatus());
        assertNull(deployment.getRequestedAt());
        assertNull(deployment.getStartedAt());
        assertNull(deployment.getFinishedAt());
        assertNull(deployment.getUpdatedAt());
        assertNull(deployment.getContainerId());
        assertNull(deployment.getErrorCode());
        assertNull(deployment.getErrorMessage());
    }

    @Test
    void shouldFollowSuccessfulLifecycleAndSetTimestamps() {
        Deployment deployment = newDeployment();
        Snapshot original = snapshot(deployment);

        Instant transferStartedBefore = Instant.now();
        deployment.startTransfer();
        Instant transferStartedAfter = Instant.now();

        assertEquals(DeploymentStatus.TRANSFERRING, deployment.getStatus());
        assertBetween(deployment.getStartedAt(), transferStartedBefore, transferStartedAfter);
        assertBetween(deployment.getUpdatedAt(), transferStartedBefore, transferStartedAfter);
        assertNull(deployment.getFinishedAt());

        deployment.startContainer();
        assertEquals(DeploymentStatus.STARTING, deployment.getStatus());
        assertNotNull(deployment.getUpdatedAt());
        assertNull(deployment.getFinishedAt());

        deployment.startVerification();
        assertEquals(DeploymentStatus.VERIFYING, deployment.getStatus());
        assertNotNull(deployment.getUpdatedAt());
        assertNull(deployment.getFinishedAt());

        Instant successBefore = Instant.now();
        deployment.markSuccess();
        Instant successAfter = Instant.now();

        assertEquals(DeploymentStatus.SUCCESS, deployment.getStatus());
        assertBetween(deployment.getFinishedAt(), successBefore, successAfter);
        assertBetween(deployment.getUpdatedAt(), successBefore, successAfter);
        assertEquals(original.applicationId(), deployment.getApplicationId());
        assertEquals(original.serverId(), deployment.getServerId());
        assertEquals(original.imageArtifactId(), deployment.getImageArtifactId());
        assertEquals(original.imageReference(), deployment.getImageReference());
        assertEquals(original.containerPort(), deployment.getContainerPort());
    }

    @ParameterizedTest(name = "{0} must reject state {1}")
    @MethodSource("invalidTransitions")
    void shouldRejectInvalidLifecycleTransitionsWithoutChangingDeployment(
            String operationName,
            DeploymentStatus state,
            Consumer<Deployment> operation
    ) {
        Deployment deployment = deploymentIn(state);
        Snapshot before = snapshot(deployment);

        assertThrows(InvalidDeploymentTransitionException.class, () -> operation.accept(deployment), operationName);

        assertEquals(before, snapshot(deployment));
    }

    static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                invalid("startTransfer", DeploymentStatus.TRANSFERRING, Deployment::startTransfer),
                invalid("startTransfer", DeploymentStatus.STARTING, Deployment::startTransfer),
                invalid("startTransfer", DeploymentStatus.VERIFYING, Deployment::startTransfer),
                invalid("startTransfer", DeploymentStatus.SUCCESS, Deployment::startTransfer),
                invalid("startTransfer", DeploymentStatus.FAILED, Deployment::startTransfer),
                invalid("startTransfer", DeploymentStatus.INTERRUPTED, Deployment::startTransfer),
                invalid("startContainer", DeploymentStatus.PENDING, Deployment::startContainer),
                invalid("startContainer", DeploymentStatus.STARTING, Deployment::startContainer),
                invalid("startContainer", DeploymentStatus.VERIFYING, Deployment::startContainer),
                invalid("startContainer", DeploymentStatus.SUCCESS, Deployment::startContainer),
                invalid("startContainer", DeploymentStatus.FAILED, Deployment::startContainer),
                invalid("startContainer", DeploymentStatus.INTERRUPTED, Deployment::startContainer),
                invalid("startVerification", DeploymentStatus.PENDING, Deployment::startVerification),
                invalid("startVerification", DeploymentStatus.TRANSFERRING, Deployment::startVerification),
                invalid("startVerification", DeploymentStatus.VERIFYING, Deployment::startVerification),
                invalid("startVerification", DeploymentStatus.SUCCESS, Deployment::startVerification),
                invalid("startVerification", DeploymentStatus.FAILED, Deployment::startVerification),
                invalid("startVerification", DeploymentStatus.INTERRUPTED, Deployment::startVerification),
                invalid("markSuccess", DeploymentStatus.PENDING, Deployment::markSuccess),
                invalid("markSuccess", DeploymentStatus.TRANSFERRING, Deployment::markSuccess),
                invalid("markSuccess", DeploymentStatus.STARTING, Deployment::markSuccess),
                invalid("markSuccess", DeploymentStatus.SUCCESS, Deployment::markSuccess),
                invalid("markSuccess", DeploymentStatus.FAILED, Deployment::markSuccess),
                invalid("markSuccess", DeploymentStatus.INTERRUPTED, Deployment::markSuccess)
        );
    }

    @ParameterizedTest(name = "markFailed must work from {0}")
    @MethodSource("activeStatuses")
    void shouldFailFromEveryActiveStatus(DeploymentStatus state) {
        Deployment deployment = deploymentIn(state);
        Instant before = Instant.now();
        deployment.markFailed("IMAGE_PULL_FAILED", "Could not retrieve image");
        Instant after = Instant.now();

        assertEquals(DeploymentStatus.FAILED, deployment.getStatus());
        assertEquals("IMAGE_PULL_FAILED", deployment.getErrorCode());
        assertEquals("Could not retrieve image", deployment.getErrorMessage());
        assertBetween(deployment.getFinishedAt(), before, after);
        assertBetween(deployment.getUpdatedAt(), before, after);
    }

    static Stream<DeploymentStatus> activeStatuses() {
        return Stream.of(DeploymentStatus.PENDING, DeploymentStatus.TRANSFERRING,
                DeploymentStatus.STARTING, DeploymentStatus.VERIFYING);
    }

    @Test
    void shouldRejectFailureFromFinalizedStatusesWithoutMutation() {
        for (DeploymentStatus state : Stream.of(DeploymentStatus.SUCCESS, DeploymentStatus.FAILED,
                DeploymentStatus.INTERRUPTED).toList()) {
            Deployment deployment = deploymentIn(state);
            Snapshot before = snapshot(deployment);

            assertThrows(InvalidDeploymentTransitionException.class,
                    () -> deployment.markFailed("NEW_ERROR", "Replacement error"), state.toString());

            assertEquals(before, snapshot(deployment));
        }
    }

    @Test
    void shouldRejectInterruptingSuccessOrFailureWithoutMutation() {
        for (DeploymentStatus state : Stream.of(DeploymentStatus.SUCCESS, DeploymentStatus.FAILED).toList()) {
            Deployment deployment = deploymentIn(state);
            Snapshot before = snapshot(deployment);

            assertThrows(InvalidDeploymentTransitionException.class, deployment::markInterrupted, state.toString());

            assertEquals(before, snapshot(deployment));
        }
    }

    @Test
    void shouldInterruptActiveDeploymentWithoutSettingFinishedAt() {
        Deployment deployment = deploymentIn(DeploymentStatus.TRANSFERRING);
        Instant before = Instant.now();
        deployment.markInterrupted();
        Instant after = Instant.now();

        assertEquals(DeploymentStatus.INTERRUPTED, deployment.getStatus());
        assertBetween(deployment.getUpdatedAt(), before, after);
        assertNull(deployment.getFinishedAt());
    }

    @Test
    void shouldNotAllowLifecycleOperationsAfterInterruption() {
        Deployment deployment = deploymentIn(DeploymentStatus.INTERRUPTED);
        Snapshot before = snapshot(deployment);

        assertThrows(InvalidDeploymentTransitionException.class, deployment::startTransfer);
        assertThrows(InvalidDeploymentTransitionException.class, deployment::startContainer);
        assertThrows(InvalidDeploymentTransitionException.class, deployment::startVerification);
        assertThrows(InvalidDeploymentTransitionException.class, deployment::markSuccess);
        assertThrows(InvalidDeploymentTransitionException.class,
                () -> deployment.markFailed("ERROR", "failure"));

        assertEquals(before, snapshot(deployment));
    }

    @Test
    void shouldRecordValidContainerIdOnlyWhileStarting() {
        Deployment deployment = deploymentIn(DeploymentStatus.STARTING);
        Instant before = Instant.now();
        deployment.recordContainerId(CONTAINER_ID);
        Instant after = Instant.now();

        assertEquals(CONTAINER_ID, deployment.getContainerId());
        assertBetween(deployment.getUpdatedAt(), before, after);
    }

    @ParameterizedTest(name = "container ID {0} is rejected without mutation")
    @MethodSource("invalidContainerIds")
    void shouldRejectInvalidContainerIdsWithoutMutation(String containerId) {
        Deployment deployment = deploymentIn(DeploymentStatus.STARTING);
        Snapshot before = snapshot(deployment);

        assertThrows(IllegalArgumentException.class, () -> deployment.recordContainerId(containerId));

        assertEquals(before, snapshot(deployment));
    }

    static Stream<Arguments> invalidContainerIds() {
        return Stream.of(
                Arguments.of((String) null),
                Arguments.of(""),
                Arguments.of("   "),
                Arguments.of("a".repeat(63)),
                Arguments.of("a".repeat(65)),
                Arguments.of("A".repeat(64)),
                Arguments.of("g".repeat(64)),
                Arguments.of("0".repeat(63) + " ")
        );
    }

    @Test
    void shouldRejectDuplicateContainerIdWithoutMutation() {
        Deployment deployment = deploymentIn(DeploymentStatus.STARTING);
        deployment.recordContainerId(CONTAINER_ID);
        Snapshot before = snapshot(deployment);

        assertThrows(IllegalStateException.class, () -> deployment.recordContainerId("f".repeat(64)));

        assertEquals(before, snapshot(deployment));
    }

    @ParameterizedTest(name = "container ID cannot be recorded from {0}")
    @MethodSource("containerIdForbiddenStatuses")
    void shouldRejectContainerIdOutsideStartingWithoutMutation(DeploymentStatus state) {
        Deployment deployment = deploymentIn(state);
        Snapshot before = snapshot(deployment);

        assertThrows(InvalidDeploymentTransitionException.class, () -> deployment.recordContainerId(CONTAINER_ID));

        assertEquals(before, snapshot(deployment));
    }

    static Stream<DeploymentStatus> containerIdForbiddenStatuses() {
        return Stream.of(DeploymentStatus.PENDING, DeploymentStatus.TRANSFERRING, DeploymentStatus.VERIFYING,
                DeploymentStatus.SUCCESS, DeploymentStatus.FAILED, DeploymentStatus.INTERRUPTED);
    }

    private static Arguments invalid(String operation, DeploymentStatus state, Consumer<Deployment> action) {
        return Arguments.of(operation, state, action);
    }

    private static Deployment newDeployment() {
        return new Deployment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "registry.example/app:1.2.3", 8080);
    }

    private static Deployment deploymentIn(DeploymentStatus status) {
        Deployment deployment = newDeployment();
        switch (status) {
            case PENDING -> { }
            case TRANSFERRING -> deployment.startTransfer();
            case STARTING -> {
                deployment.startTransfer();
                deployment.startContainer();
            }
            case VERIFYING -> {
                deployment.startTransfer();
                deployment.startContainer();
                deployment.startVerification();
            }
            case SUCCESS -> {
                deployment.startTransfer();
                deployment.startContainer();
                deployment.startVerification();
                deployment.markSuccess();
            }
            case FAILED -> deployment.markFailed("ORIGINAL_ERROR", "Original failure");
            case INTERRUPTED -> deployment.markInterrupted();
        }
        return deployment;
    }

    private static Snapshot snapshot(Deployment deployment) {
        return new Snapshot(deployment.getApplicationId(), deployment.getServerId(), deployment.getImageArtifactId(),
                deployment.getImageReference(), deployment.getContainerPort(), deployment.getStatus(),
                deployment.getStartedAt(), deployment.getFinishedAt(), deployment.getUpdatedAt(),
                deployment.getContainerId(), deployment.getErrorCode(), deployment.getErrorMessage());
    }

    private static void assertBetween(Instant actual, Instant before, Instant after) {
        assertNotNull(actual);
        assertFalse(actual.isBefore(before), () -> actual + " is before " + before);
        assertFalse(actual.isAfter(after), () -> actual + " is after " + after);
    }

    private record Snapshot(
            UUID applicationId,
            UUID serverId,
            UUID imageArtifactId,
            String imageReference,
            Integer containerPort,
            DeploymentStatus status,
            Instant startedAt,
            Instant finishedAt,
            Instant updatedAt,
            String containerId,
            String errorCode,
            String errorMessage
    ) { }
}
