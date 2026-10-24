package edu.example.visualizer;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public final class PomParser {
    private static final int MAX_PROPERTY_EXPANSIONS = 20;
    private static final int PLACEHOLDER_END_OFFSET = 2;
    private record Managed(String version, String source) { }
    private record Context(Map<String, String> properties, Map<String, Managed> managed) { }

    public List<Coordinates> directDependencies(String xml, Coordinates current) throws Exception {
        return directDependencies(xml, current, null);
    }

    public List<Coordinates> directDependencies(String xml, Coordinates current, MavenClient client)
            throws Exception {
        Context ctx = context(xml, current, client, new HashSet<>());
        Element dependencies = child(parse(xml).getDocumentElement(), "dependencies");
        List<Coordinates> result = new ArrayList<>();
        if (dependencies == null) {
            return result;
        }
        for (Element element : named(dependencies, "dependency")) {
            Coordinates dependency = readDependency(element, ctx, current);
            if (dependency != null) {
                result.add(dependency);
            }
        }
        return result;
    }

    private Coordinates readDependency(Element element, Context ctx, Coordinates current) {
        Map<String, String> props = ctx.properties();
        if (!included(element, props)) {
            return null;
        }
        String group = resolve(text(element, "groupId"), props);
        String artifact = resolve(text(element, "artifactId"), props);
        if (group.isBlank() || artifact.isBlank()) {
            return null;
        }
        String version = dependencyVersion(element, ctx, group, artifact);
        if (version.isBlank() || version.contains("${")) {
            System.err.println("  [пропуск] Не удалось определить версию " + group + ":" + artifact
                    + " в " + current + " (проверены parent/dependencyManagement/BOM)");
            return null;
        }
        try {
            return new Coordinates(group, artifact, version);
        } catch (IllegalArgumentException ex) {
            System.err.println("  [пропуск] Неподдерживаемые координаты: " + ex.getMessage());
            return null;
        }
    }

    private static boolean included(Element element, Map<String, String> props) {
        String scope = resolve(text(element, "scope"), props);
        String optional = resolve(text(element, "optional"), props);
        String type = resolve(text(element, "type"), props);
        boolean validScope = scope.isEmpty() || "compile".equals(scope) || "runtime".equals(scope);
        boolean validType = type.isEmpty() || "jar".equals(type);
        return validScope && validType && !"true".equalsIgnoreCase(optional);
    }

    private static String dependencyVersion(Element element, Context ctx, String group, String artifact) {
        String version = resolve(text(element, "version"), ctx.properties());
        if (!version.isBlank()) {
            return version;
        }
        Managed managed = ctx.managed().get(group + ":" + artifact);
        if (managed == null) {
            return version;
        }
        version = resolve(managed.version(), ctx.properties());
        System.out.println("  [версия из dependencyManagement] " + group + ":" + artifact
                + " -> " + version + " (источник: " + managed.source() + ")");
        return version;
    }

    private Context context(String xml, Coordinates current, MavenClient client, Set<Coordinates> active)
            throws Exception {
        if (!active.add(current)) {
            throw new IllegalStateException("Цикл parent/BOM: " + current);
        }
        try {
            Element project = parse(xml).getDocumentElement();
            Map<String, String> props = new LinkedHashMap<>();
            Map<String, Managed> managed = new LinkedHashMap<>();
            inheritParent(project, client, active, props, managed);
            addProperties(project, current, props);
            importManagement(project, current, client, active, props, managed);
            return new Context(props, managed);
        } finally {
            active.remove(current);
        }
    }

    private void inheritParent(Element project, MavenClient client, Set<Coordinates> active,
                               Map<String, String> props, Map<String, Managed> managed) throws Exception {
        Element parent = child(project, "parent");
        if (parent == null || client == null) {
            return;
        }
        Coordinates coordinate = new Coordinates(
                text(parent, "groupId"), text(parent, "artifactId"), text(parent, "version"));
        Context inherited = context(client.fetchPom(coordinate), coordinate, client, active);
        props.putAll(inherited.properties());
        managed.putAll(inherited.managed());
        props.put("project.parent.groupId", coordinate.groupId());
        props.put("project.parent.version", coordinate.version());
    }

    private static void addProperties(Element project, Coordinates current, Map<String, String> props) {
        props.put("project.groupId", current.groupId());
        props.put("project.artifactId", current.artifactId());
        props.put("project.version", current.version());
        props.put("pom.groupId", current.groupId());
        props.put("pom.version", current.version());
        Element root = child(project, "properties");
        if (root != null) {
            for (Element property : elements(root)) {
                props.put(property.getTagName(), property.getTextContent().trim());
            }
        }
    }

    private void importManagement(Element project, Coordinates current, MavenClient client,
                                  Set<Coordinates> active, Map<String, String> props,
                                  Map<String, Managed> managed) throws Exception {
        Element management = child(project, "dependencyManagement");
        Element dependencies = management == null ? null : child(management, "dependencies");
        if (dependencies == null) {
            return;
        }
        List<Element> items = named(dependencies, "dependency");
        for (Element item : items) {
            importBom(item, client, active, props, managed);
        }
        for (Element item : items) {
            registerManaged(item, current, props, managed);
        }
    }

    private void importBom(Element item, MavenClient client, Set<Coordinates> active,
                           Map<String, String> props, Map<String, Managed> managed) throws Exception {
        if (!isBom(item, props) || client == null) {
            return;
        }
        String group = resolve(text(item, "groupId"), props);
        String artifact = resolve(text(item, "artifactId"), props);
        String version = resolve(text(item, "version"), props);
        if (version.isBlank()) {
            Managed inherited = managed.get(group + ":" + artifact);
            if (inherited != null) {
                version = resolve(inherited.version(), props);
            }
        }
        if (group.isBlank() || artifact.isBlank() || version.isBlank() || version.contains("${")) {
            System.err.println("  [BOM пропущен] Неизвестная версия: " + group + ":" + artifact);
            return;
        }
        Coordinates bom = new Coordinates(group, artifact, version);
        Context imported = context(client.fetchPom(bom), bom, client, active);
        imported.managed().forEach(managed::putIfAbsent);
    }

    private static boolean isBom(Element element, Map<String, String> props) {
        return "import".equals(resolve(text(element, "scope"), props))
                && "pom".equals(resolve(text(element, "type"), props));
    }

    private static void registerManaged(Element element, Coordinates current,
                                        Map<String, String> props, Map<String, Managed> managed) {
        if (isBom(element, props)) {
            return;
        }
        String group = resolve(text(element, "groupId"), props);
        String artifact = resolve(text(element, "artifactId"), props);
        String version = resolve(text(element, "version"), props);
        if (!group.isBlank() && !artifact.isBlank() && !version.isBlank()) {
            managed.put(group + ":" + artifact, new Managed(version, current.toString()));
        }
    }

    private static String resolve(String value, Map<String, String> props) {
        for (int index = 0; index < MAX_PROPERTY_EXPANSIONS; index++) {
            int start = value.indexOf("${");
            if (start < 0) {
                break;
            }
            int end = value.indexOf('}', start + PLACEHOLDER_END_OFFSET);
            if (end < 0) {
                break;
            }
            String name = value.substring(start + PLACEHOLDER_END_OFFSET, end);
            String replacement = props.get(name);
            if (replacement == null || replacement.equals("${" + name + "}")) {
                break;
            }
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

    private static String text(Element element, String name) {
        Element result = child(element, name);
        return result == null ? "" : result.getTextContent().trim();
    }

    private static Element child(Element element, String name) {
        for (Element item : elements(element)) {
            if (item.getTagName().equals(name)) {
                return item;
            }
        }
        return null;
    }

    private static List<Element> named(Element element, String name) {
        List<Element> result = new ArrayList<>();
        for (Element item : elements(element)) {
            if (item.getTagName().equals(name)) {
                result.add(item);
            }
        }
        return result;
    }

    private static List<Element> elements(Element element) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = element.getChildNodes();
        for (int index = 0; index < nodes.getLength(); index++) {
            if (nodes.item(index) instanceof Element child) {
                result.add(child);
            }
        }
        return result;
    }
}
