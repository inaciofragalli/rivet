package io.github.inaciofragalli.rivet.deployment.internal;

import io.github.inaciofragalli.rivet.deployment.DeploymentStatus;
import io.github.inaciofragalli.rivet.deployment.InvalidDeploymentTransitionException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "deployments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deployment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "server_id", nullable = false)
    private UUID  serverId;

    @Column(name = "image_artifact_id")
    private UUID  imageArtifactId;

    @Column(name = "image_reference", columnDefinition = "TEXT", nullable = false)
    private String imageReference;

    @Column(name = "container_port", nullable = false)
    private Integer containerPort;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeploymentStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false, insertable = false)
    private Instant requestedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    @Column(name = "container_id", length = 64)
    private String containerId;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Version
    @Column(nullable = false)
    private Long version;

    public Deployment(UUID applicationId, UUID serverId, UUID imageArtifactId, String imageReference, Integer containerPort) {
        this.applicationId = applicationId;
        this.serverId = serverId;
        this.imageArtifactId = imageArtifactId;
        this.imageReference = imageReference;
        this.containerPort = containerPort;
        this.status = DeploymentStatus.PENDING;
    }

    public void startTransfer() {
        if (status != DeploymentStatus.PENDING) {
            throw new InvalidDeploymentTransitionException();
        }

        status = DeploymentStatus.TRANSFERRING;
        startedAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void startContainer() {
        if (status != DeploymentStatus.TRANSFERRING) {
            throw new InvalidDeploymentTransitionException();
        }

        status = DeploymentStatus.STARTING;
        updatedAt = Instant.now();
    }

    public void startVerification() {
        if (status != DeploymentStatus.STARTING) {
            throw new InvalidDeploymentTransitionException();
        }

        status = DeploymentStatus.VERIFYING;
        updatedAt = Instant.now();
    }

    public void markSuccess() {
        if (status != DeploymentStatus.VERIFYING) {
            throw new InvalidDeploymentTransitionException();
        }

        status = DeploymentStatus.SUCCESS;
        updatedAt = Instant.now();
        finishedAt = Instant.now();
    }

    public void markFailed(String errorCode, String errorMessage) {
        if (status == DeploymentStatus.SUCCESS || status == DeploymentStatus.INTERRUPTED || status == DeploymentStatus.FAILED) {
            throw new InvalidDeploymentTransitionException();
        }

        this.errorCode = errorCode;
        this.errorMessage = errorMessage;

        status = DeploymentStatus.FAILED;
        updatedAt = Instant.now();
        finishedAt = Instant.now();
    }

    public void markInterrupted() {
        if (status == DeploymentStatus.SUCCESS || status == DeploymentStatus.FAILED) {
            throw new InvalidDeploymentTransitionException();
        }

        status = DeploymentStatus.INTERRUPTED;
        updatedAt = Instant.now();
    }

    public void recordContainerId(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            throw new IllegalArgumentException("Container ID cannot be null or blank");
        }

        if (containerId.length() != 64 ||
                !containerId.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid Docker container ID");
        }

        if (this.containerId != null) {
            throw new IllegalStateException("Container ID is already recorded");
        }

        if (this.status != DeploymentStatus.STARTING) {
            throw new InvalidDeploymentTransitionException();
        }

        this.containerId = containerId;
        this.updatedAt = Instant.now();
    }
}
