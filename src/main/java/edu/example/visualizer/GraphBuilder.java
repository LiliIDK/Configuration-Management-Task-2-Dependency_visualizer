package edu.example.visualizer;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/** Построение графа ПО МЕРЕ обхода Maven-артефактов в ширину (BFS). */
public final class GraphBuilder {
    private final MavenClient client;
    private final PomParser parser;
    private final Config config;
    private final DependencyGraph graph = new DependencyGraph();
    private final Queue<Coordinates> queue = new ArrayDeque<>();
    private final Set<Coordinates> discovered = new HashSet<>();

    public GraphBuilder(MavenClient client, PomParser parser, Config config) {
        this.client = client;
        this.parser = parser;
        this.config = config;
    }

    public DependencyGraph build() throws Exception {
        Coordinates root = config.root();
        if (excluded(root)) throw new IllegalArgumentException("Корневой пакет попадает под фильтр: " + root);
        graph.addVertex(root);
        discovered.add(root);
        queue.add(root);
        bfsRecursive();
        return graph;
    }

    /**
     * BFS обычно пишут через while (!queue.isEmpty()). По условию задания
     * вместо while используем РЕКУРСИЮ: один вызов = обработка ОДНОЙ вершины.
     *
     * Очередь обеспечивает BFS-порядок: сначала все соседи корня, затем
     * соседи этих соседей и т.д. Если здесь рекурсивно вызвать обработку
     * каждого соседа, получился бы DFS, а не BFS!
     */
    private void bfsRecursive() throws Exception {
        if (queue.isEmpty()) return;

        Coordinates current = queue.remove();
        System.out.println("Исследуем: " + current);
        String pom = client.fetchPom(current);
        for (Coordinates dependency : parser.directDependencies(pom, current, client)) {
            if (excluded(dependency)) continue;
            graph.addEdge(current, dependency);
            if (discovered.add(dependency)) {
                if (discovered.size() > config.maxPackages())
                    throw new IllegalStateException("Превышен лимит max_packages=" + config.maxPackages());
                queue.add(dependency);
            }
        }
        bfsRecursive();
    }

    private boolean excluded(Coordinates c) {
        return !config.filter().isEmpty() && c.artifactId().contains(config.filter());
    }
}
