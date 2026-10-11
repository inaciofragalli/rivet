package io.github.inaciofragalli.rivet.image.internal;

import io.github.inaciofragalli.rivet.image.ImageMetadata;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "image_artifacts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ImageArtifact {

    @Id
    private UUID id;

    @Column(name = "image_name", nullable = false, length = 512)
    private String imageName;

    @Column(name = "image_tag", length = 128)
    private String imageTag;

    @Column(nullable = false, length = 64)
    private String architecture;

    @Column(nullable = false, length = 64)
    private String os;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exposed_ports", nullable = false, columnDefinition = "jsonb")
    private List<Integer> exposedPorts;

    @Column(name = "archive_size_bytes", nullable = false)
    private long archiveSizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ImageArtifact(
            UUID id,
            ImageMetadata metadata,
            long archiveSizeBytes,
            String sha256
    ) {
        this.id = id;
        this.imageName = metadata.image();
        this.imageTag = metadata.tag();
        this.architecture = metadata.architecture();
        this.os = metadata.os();
        this.exposedPorts = new ArrayList<>(metadata.exposedPorts());
        this.archiveSizeBytes = archiveSizeBytes;
        this.sha256 = sha256;
    }
}
