package io.github.inaciofragalli.rivet.image.internal;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

final class DecompressedSizeLimitInputStream extends FilterInputStream {

    private final long maxBytes;
    private long consumedBytes;

    DecompressedSizeLimitInputStream(InputStream input, long maxBytes) {
        super(input);

        if (maxBytes <= 0) {
            throw new IllegalArgumentException("Limite inválido");
        }

        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int value = in.read();

        if (value != -1) {
            count(1);
        }

        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length)
            throws IOException {

        int bytesRead = in.read(buffer, offset, length);

        if (bytesRead > 0) {
            count(bytesRead);
        }

        return bytesRead;
    }

    @Override
    public long skip(long bytesToSkip) throws IOException {
        byte[] buffer = new byte[8192];
        long skipped = 0;

        while (skipped < bytesToSkip) {
            int bytesRead = read(
                    buffer,
                    0,
                    (int) Math.min(buffer.length, bytesToSkip - skipped)
            );

            if (bytesRead == -1) {
                break;
            }

            skipped += bytesRead;
        }

        return skipped;
    }

    private void count(long bytes) throws IOException {
        if (bytes > maxBytes - consumedBytes) {
            throw new ImageArchiveTooLargeException(
                    "Conteúdo descompactado excede o limite permitido"
            );
        }

        consumedBytes += bytes;
    }
}
