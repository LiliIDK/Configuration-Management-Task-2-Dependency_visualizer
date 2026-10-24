package edu.example.visualizer;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class ConfigLoader {
    private static final int DEFAULT_MAX_PACKAGES = 200;
    private static final int MIN_PACKAGES = 1;
    private static final int MAX_PACKAGES = 10_000;
    private static final Set<String> KEYS = Set.of(
            "package_name", "repository_url", "output_file", "filter",
            "max_packages", "log_requests", "log_pom");
    private static final Set<String> REQUIRED = Set.of(
            "package_name", "repository_url", "output_file", "filter");

    private ConfigLoader() {
    }

    public static Config load(Path file) throws IOException {
        Map<String, String> values = readValues(file);
        checkRequired(values);
        Coordinates root = Coordinates.parse(values.get("package_name"));
        URI repository = repositoryUrl(values.get("repository_url"));
        Path output = outputPath(values.get("output_file"));
        int max = maxPackages(values.getOrDefault("max_packages", String.valueOf(DEFAULT_MAX_PACKAGES)));
        boolean requests = parseBoolean(values.getOrDefault("log_requests", "false"), "log_requests");
        boolean pom = parseBoolean(values.getOrDefault("log_pom", String.valueOf(requests)), "log_pom");
        return new Config(root, repository, output, values.get("filter"), max, requests, pom);
    }

    private static Map<String, String> readValues(Path file) throws IOException {
        Map<String, String> values = new HashMap<>();
        int lineNumber = 0;
        for (String line : Files.readAllLines(file)) {
            lineNumber++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            parseLine(values, trimmed, lineNumber);
        }
        return values;
    }

    private static void parseLine(Map<String, String> values, String line, int lineNumber) {
        int separator = line.indexOf(':');
        if (separator < 1) {
            throw new IllegalArgumentException("YAML, строка " + lineNumber + ": ожидается ключ: значение");
        }
        String key = line.substring(0, separator).trim();
        if (!KEYS.contains(key)) {
            throw new IllegalArgumentException("Неизвестный параметр: " + key);
        }
        if (values.containsKey(key)) {
            throw new IllegalArgumentException("Повтор параметра: " + key);
        }
        values.put(key, unquote(line.substring(separator + 1).trim(), key));
    }

    private static String unquote(String value, String key) {
        if (value.startsWith("\"") || value.startsWith("'")) {
            char quote = value.charAt(0);
            if (value.length() < 2 || value.charAt(value.length() - 1) != quote) {
                throw new IllegalArgumentException("Незакрытые кавычки для " + key);
            }
            return value.substring(1, value.length() - 1);
        }
        int comment = value.indexOf(" #");
        return comment < 0 ? value : value.substring(0, comment).trim();
    }

    private static void checkRequired(Map<String, String> values) {
        for (String required : REQUIRED) {
            if (!values.containsKey(required)) {
                throw new IllegalArgumentException("В YAML отсутствует параметр: " + required);
            }
        }
    }

    private static URI repositoryUrl(String input) {
        URI url;
        try {
            url = URI.create(input);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Некорректный repository_url", e);
        }
        if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null
                || url.getUserInfo() != null || url.getQuery() != null || url.getFragment() != null) {
            throw new IllegalArgumentException("repository_url должен быть HTTPS URL без query/fragment");
        }
        return url;
    }

    private static Path outputPath(String output) {
        if (output.isBlank() || !output.toLowerCase().endsWith(".png")) {
            throw new IllegalArgumentException("output_file должен оканчиваться на .png");
        }
        return Path.of(output);
    }

    private static int maxPackages(String input) {
        int max;
        try {
            max = Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("max_packages должен быть целым числом", e);
        }
        if (max < MIN_PACKAGES || max > MAX_PACKAGES) {
            throw new IllegalArgumentException("max_packages: ожидается число от 1 до 10000");
        }
        return max;
    }

    private static boolean parseBoolean(String value, String key) {
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException(key + " принимает только true или false");
        }
        return Boolean.parseBoolean(value);
    }
}
