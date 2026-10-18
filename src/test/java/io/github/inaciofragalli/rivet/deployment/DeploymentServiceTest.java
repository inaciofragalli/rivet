package io.github.inaciofragalli.rivet.deployment;

import io.github.inaciofragalli.rivet.application.ApplicationNotFoundException;
import io.github.inaciofragalli.rivet.application.ApplicationResponse;
import io.github.inaciofragalli.rivet.application.ApplicationService;
import io.github.inaciofragalli.rivet.deployment.internal.Deployment;
import io.github.inaciofragalli.rivet.deployment.internal.DeploymentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeploymentServiceTest {

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private ApplicationService applicationService;

    private DeploymentService deploymentService;

    @Mock
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        deploymentService = new DeploymentService(deploymentRepository, applicationService, entityManager);
    }

    @Test
    void createDeploymentStoresApplicationSnapshotAndStartsPending() {
        UUID applicationId = UUID.randomUUID();
        UUID serverId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();
        ApplicationResponse application = new ApplicationResponse(
                applicationId, "orders", "registry.example/orders:1.2", 8080, artifactId);
        when(applicationService.findById(applicationId)).thenReturn(application);
        when(deploymentRepository.existsByApplicationIdAndServerIdAndStatusIn(
                eq(applicationId), eq(serverId), anyList())).thenReturn(false);
        when(deploymentRepository.saveAndFlush(any(Deployment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        DeploymentResponse response = deploymentService.createDeployment(applicationId, serverId);

        ArgumentCaptor<List<DeploymentStatus>> statusesCaptor = ArgumentCaptor.forClass(List.class);
        verify(applicationService).findById(applicationId);
        verify(deploymentRepository).existsByApplicationIdAndServerIdAndStatusIn(
                eq(applicationId), eq(serverId), statusesCaptor.capture());
        assertEquals(List.of(
                DeploymentStatus.PENDING,
                DeploymentStatus.TRANSFERRING,
                DeploymentStatus.STARTING,
                DeploymentStatus.INTERRUPTED,
                DeploymentStatus.VERIFYING
        ), statusesCaptor.getValue());

        ArgumentCaptor<Deployment> deploymentCaptor = ArgumentCaptor.forClass(Deployment.class);
        verify(deploymentRepository).saveAndFlush(deploymentCaptor.capture());
        Deployment saved = deploymentCaptor.getValue();
        verify(entityManager).refresh(saved);
        assertEquals(applicationId, saved.getApplicationId());
        assertEquals(serverId, saved.getServerId());
        assertEquals(artifactId, saved.getImageArtifactId());
        assertEquals("registry.example/orders:1.2", saved.getImageReference());
        assertEquals(8080, saved.getContainerPort());
        assertEquals(DeploymentStatus.PENDING, saved.getStatus());
        assertEquals(DeploymentStatus.PENDING, response.status());
    }

    @Test
    void createDeploymentRejectsWhenBlockingDeploymentExists() {
        UUID applicationId = UUID.randomUUID();
        UUID serverId = UUID.randomUUID();
        when(applicationService.findById(applicationId)).thenReturn(
                new ApplicationResponse(applicationId, "orders", "orders:latest", 8080, null));
        when(deploymentRepository.existsByApplicationIdAndServerIdAndStatusIn(
                eq(applicationId), eq(serverId), anyList())).thenReturn(true);

        assertThrows(DeploymentAlreadyInProgressException.class,
                () -> deploymentService.createDeployment(applicationId, serverId));

        verify(deploymentRepository, never()).save(any(Deployment.class));
    }

    @Test
    void createDeploymentPropagatesApplicationNotFound() {
        UUID applicationId = UUID.randomUUID();
        UUID serverId = UUID.randomUUID();
        ApplicationNotFoundException exception = new ApplicationNotFoundException(applicationId.toString());
        when(applicationService.findById(applicationId)).thenThrow(exception);

        ApplicationNotFoundException thrown = assertThrows(ApplicationNotFoundException.class,
                () -> deploymentService.createDeployment(applicationId, serverId));

        assertSame(exception, thrown);
        verifyNoInteractions(deploymentRepository);
    }

    @Test
    void findByIdReturnsMappedDeployment() {
        UUID deploymentId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID serverId = UUID.randomUUID();
        Deployment deployment = new Deployment(applicationId, serverId, null, "orders:latest", 8080);
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));

        DeploymentResponse response = deploymentService.findById(deploymentId);

        assertEquals(applicationId, response.applicationId());
        assertEquals(serverId, response.serverId());
        assertEquals("orders:latest", response.imageReference());
        assertEquals(8080, response.containerPort());
        assertEquals(DeploymentStatus.PENDING, response.status());
    }

    @Test
    void findByIdThrowsWhenDeploymentDoesNotExist() {
        UUID deploymentId = UUID.randomUUID();
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

        assertThrows(DeploymentNotFoundException.class, () -> deploymentService.findById(deploymentId));
    }

    @Test
    void findByApplicationIdReturnsAllAttemptsInRepositoryOrder() {
        UUID applicationId = UUID.randomUUID();
        Deployment older = new Deployment(applicationId, UUID.randomUUID(), null, "orders:1", 8080);
        Deployment newer = new Deployment(applicationId, UUID.randomUUID(), null, "orders:2", 8081);
        when(deploymentRepository.findByApplicationId(applicationId)).thenReturn(List.of(older, newer));

        List<DeploymentResponse> responses = deploymentService.findByApplicationId(applicationId);

        assertEquals(2, responses.size());
        assertEquals("orders:1", responses.get(0).imageReference());
        assertEquals("orders:2", responses.get(1).imageReference());
        verify(deploymentRepository).findByApplicationId(applicationId);
    }

    @Test
    void findByApplicationIdReturnsEmptyHistory() {
        UUID applicationId = UUID.randomUUID();
        when(deploymentRepository.findByApplicationId(applicationId)).thenReturn(List.of());

        assertTrue(deploymentService.findByApplicationId(applicationId).isEmpty());
    }

    @Test
    void stateTransitionMethodsDelegateToDeployment() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID(), UUID.randomUUID(), null, "orders:latest", 8080);
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));

        deploymentService.startTransfer(deploymentId);
        assertEquals(DeploymentStatus.TRANSFERRING, deployment.getStatus());
        deploymentService.startContainer(deploymentId);
        assertEquals(DeploymentStatus.STARTING, deployment.getStatus());
        deploymentService.startVerification(deploymentId);
        assertEquals(DeploymentStatus.VERIFYING, deployment.getStatus());
        deploymentService.markSuccess(deploymentId);
        assertEquals(DeploymentStatus.SUCCESS, deployment.getStatus());

        verify(deploymentRepository, times(4)).findById(deploymentId);
    }

    @Test
    void markFailedDelegatesFailureDetailsToDeployment() {
        UUID deploymentId = UUID.randomUUID();
        Deployment deployment = new Deployment(UUID.randomUUID(), UUID.randomUUID(), null, "orders:latest", 8080);
        deployment.startTransfer();
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.of(deployment));

        deploymentService.markFailed(deploymentId, "IMAGE_PULL_FAILED", "Registry unavailable");

        assertEquals(DeploymentStatus.FAILED, deployment.getStatus());
        assertEquals("IMAGE_PULL_FAILED", deployment.getErrorCode());
        assertEquals("Registry unavailable", deployment.getErrorMessage());
    }

    @Test
    void everyStateTransitionMethodThrowsWhenDeploymentDoesNotExist() {
        UUID deploymentId = UUID.randomUUID();
        when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

        assertAll(
                () -> assertThrows(DeploymentNotFoundException.class, () -> deploymentService.startTransfer(deploymentId)),
                () -> assertThrows(DeploymentNotFoundException.class, () -> deploymentService.startContainer(deploymentId)),
                () -> assertThrows(DeploymentNotFoundException.class, () -> deploymentService.startVerification(deploymentId)),
                () -> assertThrows(DeploymentNotFoundException.class, () -> deploymentService.markSuccess(deploymentId)),
                () -> assertThrows(DeploymentNotFoundException.class,
                        () -> deploymentService.markFailed(deploymentId, "ERROR", "failed"))
        );
        verify(deploymentRepository, times(5)).findById(deploymentId);
    }
}
