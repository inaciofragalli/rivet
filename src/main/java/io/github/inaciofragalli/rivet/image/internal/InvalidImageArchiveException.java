package io.github.inaciofragalli.rivet.image.internal;

public class InvalidImageArchiveException extends RuntimeException {
    public InvalidImageArchiveException(Throwable cause) {
        super("Arquivo Docker inválido ou não suportado", cause);
    }
}