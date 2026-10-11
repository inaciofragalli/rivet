package io.github.inaciofragalli.rivet.application.internal;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@Entity
@Table(name = "applications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String image;

    @Column(name = "container_port", nullable = false)
    private Integer containerPort;

    @Column(name = "image_artifact_id")
    private UUID imageArtifactId;

    public Application(String name, String image, Integer containerPort) {
        this(name, image, null, containerPort);
    }

    public Application(
            String name,
            String image,
            UUID imageArtifactId,
            Integer containerPort
    ) {
        this.name = name;
        this.image = image;
        this.imageArtifactId = imageArtifactId;
        this.containerPort = containerPort;
    }
}
