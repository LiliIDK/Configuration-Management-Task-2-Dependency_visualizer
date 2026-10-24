package edu.example.visualizer;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Проверяем разрешение версии из parent и импортированного BOM без интернета. */
public class ParentResolutionTest {
    public static void main(String[] args) throws Exception {
        String parent = """
            <project><groupId>example</groupId><artifactId>parent</artifactId><version>1</version>
            <properties><lib.version>7.3</lib.version></properties>
            <dependencyManagement><dependencies><dependency>
            <groupId>example</groupId><artifactId>lib</artifactId><version>${lib.version}</version>
            </dependency></dependencies></dependencyManagement></project>
            """;
        String child = """
            <project><parent><groupId>example</groupId><artifactId>parent</artifactId><version>1</version></parent>
            <artifactId>app</artifactId><dependencies><dependency><groupId>example</groupId>
            <artifactId>lib</artifactId></dependency></dependencies></project>
            """;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/example/parent/1/parent-1.pom", e -> {
            byte[] bytes = parent.getBytes(StandardCharsets.UTF_8);
            e.sendResponseHeaders(200, bytes.length);
            try (var out = e.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            MavenClient client = new MavenClient(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"));
            List<Coordinates> dependencies = new PomParser().directDependencies(child, new Coordinates("example", "app", "1"), client);
            if (!dependencies.equals(List.of(new Coordinates("example", "lib", "7.3"))))
                throw new AssertionError(dependencies);
            System.out.println("ParentResolutionTest OK");
        } finally { server.stop(0); }
    }
}
