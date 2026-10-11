
package io.github.inaciofragalli.rivet.image.internal;

import io.github.inaciofragalli.rivet.image.ImageArtifactResponse;
import io.github.inaciofragalli.rivet.image.ImageArtifactService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/image-artifacts")
@RequiredArgsConstructor
public class ImageArtifactController {

    private final ImageArtifactService imageArtifactService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageArtifactResponse> upload(
            @RequestParam("file") MultipartFile file
    ) throws IOException {

        if (file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Arquivo vazio"
            );
        }

        try (InputStream input = file.getInputStream()) {
            ImageArtifactResponse response =
                    imageArtifactService.upload(input);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(response);
        }
    }
}
