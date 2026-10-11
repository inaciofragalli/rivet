package io.github.inaciofragalli.rivet.application;

import io.github.inaciofragalli.rivet.application.internal.Application;
import io.github.inaciofragalli.rivet.application.internal.ApplicationRepository;
import io.github.inaciofragalli.rivet.image.ImageArtifactResponse;
import io.github.inaciofragalli.rivet.image.ImageArtifactService;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final ApplicationRepository applicationRepository;
    private final ImageArtifactService imageArtifactService;

    @Transactional
    public ApplicationResponse register(
            String name,
            String image,
            Integer containerPort
    ) {
        return saveApplication(name, image, null, containerPort);
    }

    public ApplicationResponse findById(UUID id) {
        Application app = applicationRepository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id.toString()));

        return toResponse(app);
    }

    public List<ApplicationResponse> findAll() {
        List<Application> apps = applicationRepository.findAll();

        return apps.stream().map(this::toResponse).toList();
    }

    private ApplicationResponse toResponse(Application application) {
        return new ApplicationResponse(
                application.getId(),
                application.getName(),
                application.getImage(),
                application.getContainerPort(),
                application.getImageArtifactId()
        );
    }

    @Transactional
    public ApplicationResponse registerFromArtifact(
            String name,
            UUID artifactId,
            Integer containerPort
    ) {
        ImageArtifactResponse artifact = imageArtifactService.findById(artifactId);

        String imageReference = artifact.tag() == null
                ? artifact.image()
                : artifact.image() + ":" + artifact.tag();

        return saveApplication(name, imageReference, artifactId, containerPort);
    }

    private ApplicationResponse saveApplication(
            String name,
            String image,
            UUID artifactId,
            Integer containerPort
    ) {
        if (applicationRepository.existsByName(name)) {
            throw new ApplicationAlreadyExistsException(name);
        }

        Application app = new Application(
                name,
                image,
                artifactId,
                containerPort
        );

        applicationRepository.save(app);

        return toResponse(app);
    }
}
