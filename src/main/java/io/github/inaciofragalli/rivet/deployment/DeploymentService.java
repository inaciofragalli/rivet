package io.github.inaciofragalli.rivet.deployment;

import io.github.inaciofragalli.rivet.application.ApplicationResponse;
import io.github.inaciofragalli.rivet.application.ApplicationService;
import io.github.inaciofragalli.rivet.deployment.internal.Deployment;
import io.github.inaciofragalli.rivet.deployment.internal.DeploymentRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeploymentService {
    private final DeploymentRepository deploymentRepository;
    private final ApplicationService applicationService;
    private final EntityManager entityManager;

    private static final List<DeploymentStatus> BLOCKING_STATUSES = List.of(
            DeploymentStatus.PENDING,
            DeploymentStatus.TRANSFERRING,
            DeploymentStatus.STARTING,
            DeploymentStatus.INTERRUPTED,
            DeploymentStatus.VERIFYING
    );

    @Transactional
    public DeploymentResponse createDeployment(UUID applicationId, UUID serverId) {
        ApplicationResponse application = applicationService.findById(applicationId);

        if (deploymentRepository.existsByApplicationIdAndServerIdAndStatusIn(applicationId, serverId, BLOCKING_STATUSES)) {
            throw new DeploymentAlreadyInProgressException();
        }

        Deployment deployment = new Deployment(
                applicationId,
                serverId,
                application.imageArtifactId(),
                application.image(),
                application.containerPort()
        );

        Deployment saved = deploymentRepository.saveAndFlush(deployment);

        entityManager.refresh(saved);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public DeploymentResponse findById(UUID id) {
        Deployment deployment = deploymentRepository.findById(id)
                .orElseThrow(() -> new DeploymentNotFoundException(id));

        return toResponse(deployment);
    }

    @Transactional(readOnly = true)
    public List<DeploymentResponse> findByApplicationId(UUID applicationId) {
        List<Deployment> deployments = deploymentRepository.findByApplicationId(applicationId);

        return deployments.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void startTransfer(UUID id) {
        Deployment  deployment = deploymentRepository.findById(id)
                .orElseThrow(() -> new DeploymentNotFoundException(id));

        deployment.startTransfer();
    }

    @Transactional
    public void startContainer(UUID id) {
        Deployment  deployment = deploymentRepository.findById(id)
                .orElseThrow(() -> new DeploymentNotFoundException(id));

        deployment.startContainer();
    }

    @Transactional
    public void startVerification(UUID id) {
        Deployment  deployment = deploymentRepository.findById(id)
                .orElseThrow(() -> new DeploymentNotFoundException(id));

        deployment.startVerification();
    }

    @Transactional
    public void markSuccess(UUID id) {
        Deployment  deployment = deploymentRepository.findById(id)
                .orElseThrow(() -> new DeploymentNotFoundException(id));

        deployment.markSuccess();
    }

    @Transactional
    public void markFailed(UUID id, String errorCode,  String errorMessage) {
        Deployment  deployment = deploymentRepository.findById(id)
                .orElseThrow(() -> new DeploymentNotFoundException(id));

        deployment.markFailed(errorCode, errorMessage);
    }

    private DeploymentResponse toResponse(Deployment deployment) {
        return new DeploymentResponse(
                deployment.getId(),
                deployment.getApplicationId(),
                deployment.getServerId(),
                deployment.getImageArtifactId(),
                deployment.getImageReference(),
                deployment.getContainerPort(),
                deployment.getStatus(),
                deployment.getRequestedAt(),
                deployment.getStartedAt(),
                deployment.getFinishedAt(),
                deployment.getUpdatedAt(),
                deployment.getContainerId(),
                deployment.getErrorCode(),
                deployment.getErrorMessage()
        );
    }
}