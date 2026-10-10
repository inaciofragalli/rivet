package io.github.inaciofragalli.rivet.application;

import io.github.inaciofragalli.rivet.application.internal.Application;
import io.github.inaciofragalli.rivet.application.internal.ApplicationRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final ApplicationRepository applicationRepository;

    @Transactional
    public ApplicationResponse register(String name, String image, Integer containerPort) {
        if (applicationRepository.existsByName(name)) {
            throw new ApplicationAlreadyExistsException(name);
        }

        Application app = new Application(name, image, containerPort);
        applicationRepository.save(app);

        return toResponse(app);
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
                application.getContainerPort()
        );
    }
}
