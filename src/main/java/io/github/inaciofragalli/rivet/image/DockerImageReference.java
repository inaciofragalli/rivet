
package io.github.inaciofragalli.rivet.image;

import java.util.regex.Pattern;

public record DockerImageReference(String name, String tag) {

    private static final Pattern COMPONENT = Pattern.compile(
            "[a-z0-9]+(?:(?:[._]|__|-+)[a-z0-9]+)*"
    );

    private static final Pattern TAG = Pattern.compile(
            "[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}"
    );

    private static final Pattern REGISTRY = Pattern.compile(
            "(?:\\[[0-9a-fA-F:.]+\\]|"
                    + "[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*"
                    + "(?:\\.[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*)*)"
                    + "(?::[0-9]{1,5})?"
    );

    public static DockerImageReference parse(String reference) {
        if (reference == null || reference.isBlank()
                || reference.length() > 384) {
            throw new IllegalArgumentException(
                    "Referência Docker inválida"
            );
        }

        int lastSlash = reference.lastIndexOf('/');
        int lastColon = reference.lastIndexOf(':');

        String name = lastColon > lastSlash
                ? reference.substring(0, lastColon)
                : reference;

        String tag = lastColon > lastSlash
                ? reference.substring(lastColon + 1)
                : null;

        if (name.isEmpty() || name.length() > 255
                || (tag != null && !TAG.matcher(tag).matches())) {
            throw new IllegalArgumentException(
                    "Nome ou tag Docker inválidos"
            );
        }

        String[] components = name.split("/", -1);

        int start = 0;

        if (components.length > 1 && (
                components[0].contains(".")
                        || components[0].contains(":")
                        || components[0].equals("localhost"))) {

            if (!REGISTRY.matcher(components[0]).matches()) {
                throw new IllegalArgumentException(
                        "Registry Docker inválido"
                );
            }

            start = 1;
        }

        for (int i = start; i < components.length; i++) {
            if (!COMPONENT.matcher(components[i]).matches()) {
                throw new IllegalArgumentException(
                        "Nome do repositório Docker inválido"
                );
            }
        }

        return new DockerImageReference(name, tag);
    }

    public static boolean isValid(String reference) {
        try {
            parse(reference);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
