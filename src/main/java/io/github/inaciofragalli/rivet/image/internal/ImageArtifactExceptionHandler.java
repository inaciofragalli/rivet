
package io.github.inaciofragalli.rivet.image.internal;

import io.github.inaciofragalli.rivet.image.ImageArtifactNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ImageArtifactExceptionHandler {

    @ExceptionHandler(InvalidImageArchiveException.class)
    public ProblemDetail handleInvalidArchive(
            InvalidImageArchiveException e
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.BAD_REQUEST
        );

        problem.setTitle("Invalid Docker image archive");
        problem.setDetail(e.getMessage());

        return problem;
    }


    @ExceptionHandler({
            MaxUploadSizeExceededException.class,
            ImageArchiveTooLargeException.class
    })
    public ProblemDetail handleOversizedUpload(Exception e) {
        ProblemDetail problem = ProblemDetail.forStatus(
                HttpStatus.CONTENT_TOO_LARGE
        );

        problem.setTitle("Upload too large");
        problem.setDetail("O arquivo excede o tamanho máximo permitido");

        return problem;
    }


    @ExceptionHandler(ImageArtifactNotFoundException.class)
    public ProblemDetail handleArtifactNotFound(
            ImageArtifactNotFoundException e
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Image artifact not found");
        problem.setDetail(e.getMessage());

        return problem;
    }
}
