package edu.example.visualizer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Список смежности: ключ — библиотека, значение — библиотеки, от которых она зависит.
 * LinkedHashMap/LinkedHashSet сохраняют порядок обнаружения вершин и рёбер.
 */
public final class DependencyGraph {
    private final Map<Coordinates, Set<Coordinates>> edges = new LinkedHashMap<>();

    public void addVertex(Coordinates vertex) { edges.computeIfAbsent(vertex, k -> new LinkedHashSet<>()); }

    public void addEdge(Coordinates from, Coordinates to) {
        addVertex(from);
        addVertex(to);
        edges.get(from).add(to);
    }

    public int size() { return edges.size(); }

    public Map<Coordinates, Set<Coordinates>> adjacency() {
        Map<Coordinates, Set<Coordinates>> copy = new LinkedHashMap<>();
        edges.forEach((k, v) -> copy.put(k, Collections.unmodifiableSet(v)));
        return Collections.unmodifiableMap(copy);
    }
}
