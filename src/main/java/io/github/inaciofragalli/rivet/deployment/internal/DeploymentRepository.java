package io.github.inaciofragalli.rivet.deployment.internal;

import io.github.inaciofragalli.rivet.deployment.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {
    boolean existsByApplicationIdAndServerIdAndStatusIn(UUID applicationId, UUID serverId, List<DeploymentStatus> statuses);

    boolean findAllByApplicationIdOrderByRequestedAtDesc(UUID applicationId);

    List<Deployment> findByApplicationId(UUID applicationId);
}