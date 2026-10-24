package edu.example.visualizer;

import java.io.StringReader;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

public final class PomParser {
    private record Managed(String version, String source) {}
    private record Context(Map<String, String> properties, Map<String, Managed> managed) {}

    public List<Coordinates> directDependencies(String xml, Coordinates current) throws Exception {
        return directDependencies(xml, current, null);
    }

    public List<Coordinates> directDependencies(String xml, Coordinates current, MavenClient client) throws Exception {
        Context context = context(xml, current, client, new HashSet<>());
        Element project = parse(xml).getDocumentElement();
        List<Coordinates> result = new ArrayList<>();
        Element list = child(project, "dependencies");
        if (list == null) return result;

        for (Element dependency : named(list, "dependency")) {
            Map<String, String> props = context.properties();
            String scope = resolve(text(dependency, "scope"), props);
            String optional = resolve(text(dependency, "optional"), props);
            String type = resolve(text(dependency, "type"), props);
            if (!(scope.isEmpty() || scope.equals("compile") || scope.equals("runtime"))) continue;
            if (optional.equalsIgnoreCase("true")) continue;
            if (!(type.isEmpty() || type.equals("jar"))) continue;

            String group = resolve(text(dependency, "groupId"), props);
            String artifact = resolve(text(dependency, "artifactId"), props);
            if (group.isBlank() || artifact.isBlank()) continue;
            String version = resolve(text(dependency, "version"), props);
            if (version.isBlank()) {
                Managed inherited = context.managed().get(group + ":" + artifact);
                if (inherited != null) {
                    version = resolve(inherited.version(), props);
                    System.out.println("  [версия из dependencyManagement] " + group + ":" + artifact
                            + " -> " + version + " (источник: " + inherited.source() + ")");
                }
            }
            if (version.isBlank() || version.contains("${")) {
                System.err.println("  [пропуск] Не удалось определить версию " + group + ":" + artifact
                        + " в " + current + " (проверены parent/dependencyManagement/BOM)");
                continue;
            }
            try {
                result.add(new Coordinates(group, artifact, version));
            } catch (IllegalArgumentException ex) {
                System.err.println("  [пропуск] Неподдерживаемые координаты: " + ex.getMessage());
            }
        }
        return result;
    }
    private Context context(String xml, Coordinates current, MavenClient client, Set<Coordinates> active) throws Exception {
        if (!active.add(current)) throw new IllegalStateException("Цикл parent/BOM: " + current);
        try {
            Element project = parse(xml).getDocumentElement();
            Map<String, String> props = new LinkedHashMap<>();
            Map<String, Managed> versions = new LinkedHashMap<>();

            Element parent = child(project, "parent");
            if (parent != null && client != null) {
                Coordinates p = new Coordinates(text(parent, "groupId"), text(parent, "artifactId"), text(parent, "version"));
                Context inherited = context(client.fetchPom(p), p, client, active);
                props.putAll(inherited.properties());
                versions.putAll(inherited.managed());
                props.put("project.parent.groupId", p.groupId());
                props.put("project.parent.version", p.version());
            }

            props.put("project.groupId", current.groupId());
            props.put("project.artifactId", current.artifactId());
            props.put("project.version", current.version());
            props.put("pom.groupId", current.groupId());
            props.put("pom.version", current.version());
            Element propertyRoot = child(project, "properties");
            if (propertyRoot != null) for (Element e : elements(propertyRoot))
                props.put(e.getTagName(), e.getTextContent().trim());

            Element management = child(project, "dependencyManagement");
            Element managedDependencies = management == null ? null : child(management, "dependencies");
            if (managedDependencies != null) {
                for (Element d : named(managedDependencies, "dependency")) {
                    String scope = resolve(text(d, "scope"), props);
                    String type = resolve(text(d, "type"), props);
                    if (!scope.equals("import") || !type.equals("pom")) continue;
                    if (client == null) continue;
                    String group = resolve(text(d, "groupId"), props);
                    String artifact = resolve(text(d, "artifactId"), props);
                    String version = resolve(text(d, "version"), props);
                    if (version.isBlank()) {
                        Managed managed = versions.get(group + ":" + artifact);
                        if (managed != null) version = resolve(managed.version(), props);
                    }
                    if (group.isBlank() || artifact.isBlank() || version.isBlank() || version.contains("${")) {
                        System.err.println("  [BOM пропущен] Неизвестная версия: " + group + ":" + artifact);
                        continue;
                    }
                    Coordinates bom = new Coordinates(group, artifact, version);
                    Context imported = context(client.fetchPom(bom), bom, client, active);
                    imported.managed().forEach(versions::putIfAbsent);
                }
                for (Element d : named(managedDependencies, "dependency")) {
                    String scope = resolve(text(d, "scope"), props);
                    String type = resolve(text(d, "type"), props);
                    if (scope.equals("import") && type.equals("pom")) continue;
                    String group = resolve(text(d, "groupId"), props);
                    String artifact = resolve(text(d, "artifactId"), props);
                    String version = resolve(text(d, "version"), props);
                    if (!group.isBlank() && !artifact.isBlank() && !version.isBlank())
                        versions.put(group + ":" + artifact, new Managed(version, current.toString()));
                }
            }
            return new Context(props, versions);
        } finally {
            active.remove(current);
        }
    }

    private static String resolve(String value, Map<String, String> props) {
        for (int i = 0; i < 20; i++) {
            int start = value.indexOf("${");
            if (start < 0) break;
            int end = value.indexOf('}', start + 2);
            if (end < 0) break;
            String name = value.substring(start + 2, end);
            String replacement = props.get(name);
            if (replacement == null || replacement.equals("${" + name + "}")) break;
            value = value.substring(0, start) + replacement + value.substring(end + 1);
        }
        return value;
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String text(Element e, String name) {
        Element found = child(e, name);
        return found == null ? "" : found.getTextContent().trim();
    }
    private static Element child(Element e, String name) {
        for (Element item : elements(e)) if (item.getTagName().equals(name)) return item;
        return null;
    }
    private static List<Element> named(Element e, String name) {
        List<Element> out = new ArrayList<>();
        for (Element item : elements(e)) if (item.getTagName().equals(name)) out.add(item);
        return out;
    }
    private static List<Element> elements(Element e) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = e.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) if (nodes.item(i) instanceof Element item) out.add(item);
        return out;
    }
}
