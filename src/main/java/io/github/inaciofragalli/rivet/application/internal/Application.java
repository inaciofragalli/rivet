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

    public Application(String name, String image, Integer containerPort) {
        this.name = name;
        this.image = image;
        this.containerPort = containerPort;
    }
}
