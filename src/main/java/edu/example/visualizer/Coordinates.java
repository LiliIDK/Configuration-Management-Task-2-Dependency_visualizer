package edu.example.visualizer;

/** Уникальный идентификатор артефакта Maven: группа, имя, версия. */
public record Coordinates(String groupId, String artifactId, String version) {
    private static final int COORDINATE_PARTS = 3;

    public Coordinates {
        if (!valid(groupId) || !valid(artifactId) || !valid(version)) {
            throw new IllegalArgumentException("Неверные координаты Maven: "
                    + groupId + ":" + artifactId + ":" + version);
        }
    }

    private static boolean valid(String s) {
        return s != null && !s.isBlank() && s.matches("[a-zA-Z0-9_.-]+");
    }

    public static Coordinates parse(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length != COORDINATE_PARTS) {
            throw new IllegalArgumentException("Ожидается groupId:artifactId:version: " + value);
        }
        return new Coordinates(parts[0], parts[1], parts[2]);
    }

    public String pomPath() {
        return groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/"
                + artifactId + "-" + version + ".pom";
    }

    @Override public String toString() { return groupId + ":" + artifactId + ":" + version; }
}
