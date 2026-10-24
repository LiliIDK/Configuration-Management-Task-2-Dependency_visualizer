package edu.example.visualizer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Получаем метаданные Maven (только XML-файлы .pom) стандартным Java HttpClient.
 *
 * Это НЕ вызов Maven и не скачивание JAR-файлов. Для POM используется HTTP GET.
 * Когда включено логирование, выводим реальный URL и полный XML-ответ сервера.
 */
public final class MavenClient {
    private static final int HTTP_OK = 200;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final URI base;
    private final Map<Coordinates, String> cache = new HashMap<>();
    private final boolean logRequests;
    private final boolean logPom;

    public MavenClient(URI repositoryUrl) {
        this(repositoryUrl, false, false);
    }

    public MavenClient(URI repositoryUrl, boolean logRequests, boolean logPom) {
        String value = repositoryUrl.toString();
        base = URI.create(value.endsWith("/") ? value : value + "/");
        this.logRequests = logRequests;
        this.logPom = logPom;
    }

    public String fetchPom(Coordinates coordinate) throws IOException, InterruptedException {
                String cached = cache.get(coordinate);
        URI uri = base.resolve(coordinate.pomPath());
        if (cached != null) {
            if (logRequests) System.out.println("[HTTP CACHE] " + coordinate + " (без нового запроса) " + uri);
            if (logPom) printPom(coordinate, uri, cached, true);
            return cached;
        }

                HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/xml,text/xml,*/*")
                .GET()
                .build();

        if (logRequests) {
            System.out.println("[HTTP REQUEST] " + request.method() + " " + request.uri());
            System.out.println("[HTTP REQUEST] Accept: application/xml,text/xml,*/*");
            System.out.println("[HTTP REQUEST] Тело запроса: отсутствует (GET)");
        }

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (logRequests) {
            System.out.println("[HTTP RESPONSE] " + response.statusCode() + " " + response.uri());
            System.out.println("[HTTP RESPONSE] Content-Type: "
                    + response.headers().firstValue("content-type").orElse("не указан"));
        }
        if (response.statusCode() != HTTP_OK)
            throw new IOException("HTTP " + response.statusCode() + " при получении " + uri);

            String xml = response.body();
        if (logPom) printPom(coordinate, response.uri(), xml, false);
        cache.put(coordinate, xml);
        return xml;
    }

    private static void printPom(Coordinates coordinate, URI uri, String xml, boolean fromCache) {
        System.out.println("========== POM XML: " + coordinate
                + (fromCache ? " [из кеша]" : " [получен по HTTP]") + " ==========");
        System.out.println("Источник: " + uri);
        System.out.println(xml);
        System.out.println("========== КОНЕЦ POM: " + coordinate + " ==========");
    }
}
