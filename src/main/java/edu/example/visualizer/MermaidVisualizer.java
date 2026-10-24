package edu.example.visualizer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MermaidVisualizer {
    public String toMermaid(DependencyGraph graph) {
        StringBuilder result = new StringBuilder("flowchart TD\n");
        Map<Coordinates, String> ids = new LinkedHashMap<>();
        for (Coordinates c : graph.adjacency().keySet()) ids.put(c, "N" + ids.size());
        for (var entry : ids.entrySet()) {
            String label = entry.getKey().toString().replace("&", "&amp;").replace("\"", "#quot;")
                    .replace("<", "&lt;").replace(">", "&gt;");
            result.append("    ").append(entry.getValue()).append("[\"").append(label).append("\"]\n");
        }
        graph.adjacency().forEach((from, dependencies) -> {
            for (Coordinates to : dependencies) {
                result.append("    ").append(ids.get(from)).append(" --> ").append(ids.get(to)).append('\n');
            }
        });
        return result.toString();
    }

    public Path saveAndRender(DependencyGraph graph, Path png) throws IOException, InterruptedException {
        Path absolutePng = png.toAbsolutePath().normalize();
        Path parent = absolutePng.getParent();
        if (parent != null) Files.createDirectories(parent);
        String name = absolutePng.getFileName().toString();
        Path mmd = absolutePng.resolveSibling(name.substring(0, name.length() - 4) + ".mmd");
        Files.writeString(mmd, toMermaid(graph), StandardCharsets.UTF_8);
        System.out.println("Mermaid-текст: " + mmd);
        Process process;

        try {
            String mermaidPath = "C:\\Users\\Vio\\AppData\\Roaming\\npm\\mmdc.cmd";
            ProcessBuilder processBuilder = new ProcessBuilder(
                    mermaidPath,
                    "-i", mmd.toString(),
                    "-o", absolutePng.toString()
            );
            Map<String, String> environment = processBuilder.environment();
            String nodePath = "C:\\Program Files\\nodejs";
            String currentPath = environment.getOrDefault(
                    "Path",
                    environment.getOrDefault("PATH", "")
            );

            environment.put("Path", nodePath + ";" + currentPath);
            process = processBuilder.inheritIO().start();

        } catch (IOException e) {
            System.err.println(
                    "Ошибка запуска Mermaid CLI: " + e.getMessage()
            );
            throw e;
        }
        int code = process.waitFor();
        if (code != 0) throw new IOException("Mermaid CLI завершился с кодом " + code + "; файл .mmd сохранён");
        return absolutePng;
    }
}
