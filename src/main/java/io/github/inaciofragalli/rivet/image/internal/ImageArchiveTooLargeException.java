package io.github.inaciofragalli.rivet.image.internal;

import java.io.IOException;

public class ImageArchiveTooLargeException extends IOException {
    public ImageArchiveTooLargeException() {
        super("Imagem excede o limite de 512 MiB");
    }

    public ImageArchiveTooLargeException(String message) { super(message); }
}
