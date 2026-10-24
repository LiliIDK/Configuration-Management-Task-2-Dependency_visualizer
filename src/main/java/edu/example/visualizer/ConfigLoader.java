package edu.example.visualizer;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

//загрузчик YAML
public final class ConfigLoader {
    private ConfigLoader() {}

    public static Config load(Path file) throws IOException {
        Map<String, String> values = new HashMap<>();
        Set<String> keys = Set.of("package_name", "repository_url", "output_file", "filter", "max_packages", "log_requests", "log_pom");
        int lineNumber = 0;
        for (String line : Files.readAllLines(file)) {
            lineNumber++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
            int colon = trimmed.indexOf(':');
            if (colon < 1) throw new IllegalArgumentException("YAML, строка " + lineNumber + ": ожидается ключ: значение");
            String key = trimmed.substring(0, colon).trim();
            String value = trimmed.substring(colon + 1).trim();
            if (!keys.contains(key)) throw new IllegalArgumentException("Неизвестный параметр: " + key);
            if (values.containsKey(key)) throw new IllegalArgumentException("Повтор параметра: " + key);
            // Поддерживаются одинарные и двойные кавычки
            if (value.startsWith("\"") || value.startsWith("'")) {
                char q = value.charAt(0);
                if (value.length() < 2 || value.charAt(value.length() - 1) != q)
                    throw new IllegalArgumentException("Незакрытые кавычки для " + key);
                value = value.substring(1, value.length() - 1);
            } else {
                int comment = value.indexOf(" #");
                if (comment >= 0) value = value.substring(0, comment).trim();
            }
            values.put(key, value);
        }
        for (String required : Set.of("package_name", "repository_url", "output_file", "filter")) {
            if (!values.containsKey(required)) throw new IllegalArgumentException("В YAML отсутствует параметр: " + required);
        }
        Coordinates root = Coordinates.parse(values.get("package_name"));
        URI url;
        try { url = URI.create(values.get("repository_url")); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Некорректный repository_url", e); }
        if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null || url.getUserInfo() != null
                || url.getQuery() != null || url.getFragment() != null)
            throw new IllegalArgumentException("repository_url должен быть HTTPS URL репозитория без query/fragment");
        String output = values.get("output_file");
        if (output.isBlank() || !output.toLowerCase().endsWith(".png"))
            throw new IllegalArgumentException("output_file должен оканчиваться на .png");
        int max;
        try { max = Integer.parseInt(values.getOrDefault("max_packages", "200")); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("max_packages должен быть целым числом", e); }
        if (max < 1 || max > 10000) throw new IllegalArgumentException("max_packages: ожидается число от 1 до 10000");
        boolean logRequests = parseBoolean(values.getOrDefault("log_requests", "false"), "log_requests");
        boolean logPom = parseBoolean(values.getOrDefault("log_pom", Boolean.toString(logRequests)), "log_pom");
        return new Config(root, url, Path.of(output), values.get("filter"), max, logRequests, logPom);
    }
    private static boolean parseBoolean(String value, String key) {
        if (!value.equals("true") && !value.equals("false"))
            throw new IllegalArgumentException(key + " принимает только true или false");
        return Boolean.parseBoolean(value);
    }
}
