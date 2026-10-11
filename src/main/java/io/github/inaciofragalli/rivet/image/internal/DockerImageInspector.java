package io.github.inaciofragalli.rivet.image.internal;

import io.github.inaciofragalli.rivet.image.ImageMetadata;
import io.github.inaciofragalli.rivet.image.DockerImageReference;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class DockerImageInspector {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private static final long MAX_METADATA_SIZE = 1024 * 1024;
    private static final long DEFAULT_MAX_DECOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024;
    private final long maxDecompressedBytes;

    public DockerImageInspector() {
        this(DEFAULT_MAX_DECOMPRESSED_BYTES);
    }

    DockerImageInspector(long maxDecompressedBytes) {
        this.maxDecompressedBytes = maxDecompressedBytes;
    }

    public ImageMetadata inspect(Path archivePath) throws IOException {
        String configPath = null;
        String imageReference = null;

        // PRIMEIRA PASSAGEM: encontrar o manifest.json
        try (TarArchiveInputStream tar = openArchive(archivePath)) {
            TarArchiveEntry entry;

            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isFile()) {

                    if (entry.getName().equals("manifest.json")) {
                        byte[] content = readMetadata(tar, entry);

                        JsonNode manifest = parseJson(content, "manifest.json");

                        if (manifest == null || !manifest.isArray() || manifest.size() != 1) {
                            throw new IOException("O TAR deve conter exatamente uma imagem");
                        }

                        JsonNode image = manifest.get(0);

                        JsonNode repoTags = image.path("RepoTags");
                        JsonNode configNode = image.path("Config");

                        if (!repoTags.isArray()
                                || repoTags.isEmpty()
                                || !repoTags.get(0).isString()
                                || repoTags.get(0).asString().isBlank()) {
                            throw new IOException("RepoTags inválido ou ausente");
                        }

                        if (!configNode.isString() || configNode.asString().isBlank()) {
                            throw new IOException("Config inválido ou ausente");
                        }

                        imageReference = repoTags.get(0).asString();
                        configPath = configNode.asString();
                    }
                }

                tar.transferTo(OutputStream.nullOutputStream());
            }
        }

        // ENTRE AS DUAS PASSAGENS
        if (configPath == null) {
            throw new IOException("manifest.json não encontrado");
        }

        // SEGUNDA PASSAGEM: encontrar o JSON de configuração
        try (TarArchiveInputStream tar = openArchive(archivePath)) {
            TarArchiveEntry entry;

            while ((entry = tar.getNextEntry()) != null) {
                if (entry.isFile() && entry.getName().equals(configPath)) {
                    byte[] content = readMetadata(tar, entry);

                    JsonNode config = parseJson(content, configPath);

                    if (config == null || !config.isObject()) {
                        throw new IOException("Configuração da imagem inválida");
                    }

                    JsonNode architectureNode = config.path("architecture");
                    JsonNode osNode = config.path("os");

                    if (!architectureNode.isString()
                            || architectureNode.asString().isBlank()
                            || !osNode.isString()
                            || osNode.asString().isBlank()) {
                        throw new IOException("Arquitetura ou sistema operacional inválido");
                    }

                    String architecture = architectureNode.asString();
                    String os = osNode.asString();

                    JsonNode exposedPorts = config.path("config").path("ExposedPorts");

                    List<Integer> ports = new ArrayList<>();

                    if (!exposedPorts.isMissingNode() && !exposedPorts.isNull()) {
                        if (!exposedPorts.isObject()) {
                            throw new IOException("ExposedPorts deve ser um objeto JSON");
                        }

                        for (String portSpec : exposedPorts.propertyNames()) {
                            String[] parts = portSpec.split("/", -1);

                            if (parts.length != 2) {
                                throw new IOException("Porta inválida: " + portSpec);
                            }

                            if (!parts[1].equals("tcp")) {
                                continue;
                            }

                            try {
                                int port = Integer.parseInt(parts[0]);

                                if (port < 1 || port > 65535) {
                                    throw new IOException("Porta fora do intervalo: " + port);
                                }

                                ports.add(port);
                            } catch (NumberFormatException e) {
                                throw new IOException("Porta inválida: " + portSpec, e);
                            }
                        }
                    }

                    DockerImageReference reference;

                    try {
                        reference = DockerImageReference.parse(imageReference);
                    } catch (IllegalArgumentException e) {
                        throw new IOException("Referência Docker inválida", e);
                    }

                    return new ImageMetadata(
                            reference.name(),
                            reference.tag(),
                            architecture,
                            os,
                            ports
                    );
                }
            }

            throw new IOException("Arquivo de configuração da imagem não encontrado");
        }
    }

    private TarArchiveInputStream openArchive(Path path) throws IOException {
        BufferedInputStream input = new BufferedInputStream(
                Files.newInputStream(path)
        );

        try {
            input.mark(2);

            int first = input.read();
            int second = input.read();

            input.reset();

            InputStream stream = input;

            if (first == 0x1f && second == 0x8b) {
                stream = GzipCompressorInputStream.builder()
                        .setInputStream(input)
                        .get();
            }

            return new TarArchiveInputStream(
                    new DecompressedSizeLimitInputStream(
                            stream,
                            maxDecompressedBytes
                    )
            );

        } catch (IOException | RuntimeException e) {
            input.close();
            throw e;
        }
    }

    private byte[] readMetadata(TarArchiveInputStream tar, TarArchiveEntry entry) throws IOException {
        long size = entry.getSize();

        if (size < 0 || size > MAX_METADATA_SIZE) {
            throw new IOException("Tamanho de metadados inválido");
        }

        byte[] content = tar.readNBytes((int) size);

        if (content.length != size) {
            throw new IOException("Arquivo TAR incompleto");
        }

        return content;
    }

    private JsonNode parseJson(byte[] content, String filename) throws IOException {
        try {
            return mapper.readTree(content);
        } catch (JacksonException e) {
            throw new IOException("JSON inválido: " + filename, e);
        }
    }
}
