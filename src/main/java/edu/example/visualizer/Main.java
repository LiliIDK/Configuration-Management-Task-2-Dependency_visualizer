package edu.example.visualizer;

import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) {
        try {
            if (args.length > 1) throw new IllegalArgumentException("Использование: java -jar ... [config.yaml]");
            Config config = ConfigLoader.load(Path.of(args.length == 0 ? "config.yaml" : args[0]));
            System.out.println("Корневая библиотека: " + config.root());
            System.out.println("Maven-репозиторий: " + config.repositoryUrl());
            System.out.println("Фильтр: " + config.filter());
            System.out.println("Лог HTTP-запросов: " + config.logRequests());
            System.out.println("Лог XML POM: " + config.logPom());
            GraphBuilder builder = new GraphBuilder(new MavenClient(config.repositoryUrl(), config.logRequests(), config.logPom()), new PomParser(), config);
            DependencyGraph graph = builder.build();
            System.out.println("Вершин графа: " + graph.size());
            for (var e : graph.adjacency().entrySet()) {
                for (Coordinates dependency : e.getValue())
                    System.out.println("  " + e.getKey() + " -> " + dependency);
            }
            Path image = new MermaidVisualizer().saveAndRender(graph, config.outputFile());
            System.out.println("PNG готов: " + image);
        } catch (Exception e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
        }
    }
}
