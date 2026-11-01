package io.github.vecluu.jdoc.module;

import io.github.vecluu.jdoc.error.ResolutionException;
import io.github.vecluu.jdoc.model.Gav;
import io.github.vecluu.jdoc.model.Scope;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Reads the few parts of a POM jdoc needs with the JDK DOM parser. The POM namespace is optional; DOCTYPEs,
 * external entities and XInclude are refused, so reading never touches the network.
 *
 * <p>Values are interpolated with the POM's own {@code <properties>} and {@code project.*} / {@code parent.*}
 * coordinates; anything else stays as written.
 *
 * <p>The XML parser factory is created on the first parse, so wiring a reader costs nothing on calls that answer from
 * a {@link SnapshotPomAnalyzer} snapshot. An instance is confined to one thread.
 */
@Slf4j
public final class PomReader {

    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final int MAX_INTERPOLATION_ROUNDS = 5;

    private Optional<DocumentBuilderFactory> factory = Optional.empty();

    /**
     * Reads a POM file.
     *
     * @throws ResolutionException if the file cannot be read or is not a well-formed POM
     */
    public PomModel read(Path pomFile) {
        byte[] content;
        try {
            content = Files.readAllBytes(pomFile);
        } catch (IOException e) {
            throw new ResolutionException("cannot read " + pomFile + ": " + e.getMessage(), e);
        }
        return read(pomFile, content);
    }

    /**
     * Reads a POM from its already loaded bytes.
     *
     * @param pomFile the file the bytes came from (for relative paths and messages)
     * @throws ResolutionException if the content is not a well-formed POM
     */
    public PomModel read(Path pomFile, byte[] content) {
        Element project = parse(pomFile, content);
        Map<String, String> properties = new HashMap<>();
        child(project, "properties").ifPresent(element -> {
            for (Element property : children(element)) {
                properties.put(property.getLocalName(), property.getTextContent().strip());
            }
        });

        Optional<Element> parentElement = child(project, "parent");
        Optional<String> parentGroup = parentElement.flatMap(p -> text(p, "groupId"));
        Optional<String> parentArtifact = parentElement.flatMap(p -> text(p, "artifactId"));
        Optional<String> parentVersion = parentElement.flatMap(p -> text(p, "version"));
        parentGroup.ifPresent(v -> putCoordinate(properties, "parent.groupId", v));
        parentVersion.ifPresent(v -> putCoordinate(properties, "parent.version", v));
        parentArtifact.ifPresent(v -> putCoordinate(properties, "parent.artifactId", v));

        Optional<String> groupId = text(project, "groupId").or(() -> parentGroup);
        Optional<String> artifactId = text(project, "artifactId");
        Optional<String> version = text(project, "version").or(() -> parentVersion);
        groupId.ifPresent(v -> putCoordinate(properties, "groupId", v));
        artifactId.ifPresent(v -> putCoordinate(properties, "artifactId", v));
        version.ifPresent(v -> putCoordinate(properties, "version", v));
        Interpolator interpolator = new Interpolator(properties);

        Optional<ParentReference> parent = parentElement.flatMap(p -> gav(
                        parentGroup.map(interpolator::apply), parentArtifact.map(interpolator::apply),
                        parentVersion.map(interpolator::apply))
                .map(gav -> new ParentReference(gav, relativePath(p))));
        Optional<Gav> coordinates = gav(groupId.map(interpolator::apply), artifactId.map(interpolator::apply),
                version.map(interpolator::apply));

        List<String> modules = new ArrayList<>(modules(project));
        child(project, "profiles").ifPresent(profiles -> {
            for (Element profile : children(profiles, "profile")) {
                modules.addAll(modules(profile));
            }
        });
        List<DeclaredDependency> dependencies = new ArrayList<>();
        child(project, "dependencies").ifPresent(element -> {
            for (Element dependency : children(element, "dependency")) {
                dependency(dependency, interpolator).ifPresent(dependencies::add);
            }
        });
        return new PomModel(pomFile, coordinates, parent, modules, dependencies);
    }

    private Element parse(Path pomFile, byte[] content) {
        Document document;
        try {
            DocumentBuilder builder = factory().newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            document = builder.parse(new ByteArrayInputStream(content), pomFile.toUri().toString());
        } catch (IOException e) {
            throw new ResolutionException("cannot read " + pomFile + ": " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new ResolutionException("malformed POM " + pomFile + ": " + e.getMessage(), e);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("XML parser misconfigured", e);
        }
        Element root = document.getDocumentElement();
        if (root == null || !"project".equals(root.getLocalName())) {
            throw new ResolutionException("not a POM (root element is not <project>): " + pomFile);
        }
        return root;
    }

    private static Optional<String> relativePath(Element parent) {
        return child(parent, "relativePath")
                .map(element -> Optional.of(element.getTextContent().strip()))
                .orElse(Optional.of(ParentReference.DEFAULT_RELATIVE_PATH))
                .filter(path -> !path.isEmpty());
    }

    private static List<String> modules(Element container) {
        List<String> modules = new ArrayList<>();
        child(container, "modules").ifPresent(element -> children(element, "module").stream()
                .map(module -> module.getTextContent().strip()).filter(name -> !name.isEmpty())
                .forEach(modules::add));
        child(container, "subprojects").ifPresent(element -> children(element, "subproject").stream()
                .map(module -> module.getTextContent().strip()).filter(name -> !name.isEmpty())
                .forEach(modules::add));
        return modules;
    }

    private static Optional<DeclaredDependency> dependency(Element element, Interpolator interpolator) {
        Optional<String> groupId = text(element, "groupId").map(interpolator::apply);
        Optional<String> artifactId = text(element, "artifactId").map(interpolator::apply);
        if (groupId.isEmpty() || artifactId.isEmpty()) {
            return Optional.empty();
        }
        Scope scope = text(element, "scope").map(interpolator::apply).flatMap(Scope::parse).orElse(Scope.COMPILE);
        return Optional.of(new DeclaredDependency(groupId.get(), artifactId.get(), scope,
                text(element, "type").map(interpolator::apply).orElse(Gav.JAR),
                text(element, "classifier").map(interpolator::apply)));
    }

    private static Optional<Gav> gav(Optional<String> groupId, Optional<String> artifactId,
                                     Optional<String> version) {
        if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Gav.of(groupId.get(), artifactId.get(), version.get()));
        } catch (IllegalArgumentException e) {
            log.debug("ignoring invalid coordinates {}:{}:{}", groupId.get(), artifactId.get(), version.get());
            return Optional.empty();
        }
    }

    private static void putCoordinate(Map<String, String> properties, String name, String value) {
        properties.put("project." + name, value);
        properties.put("pom." + name, value);
        if (name.startsWith("parent.")) {
            properties.put(name, value);
        }
    }

    private static Optional<String> text(Element parent, String name) {
        return child(parent, name).map(element -> element.getTextContent().strip()).filter(s -> !s.isEmpty());
    }

    private static Optional<Element> child(Element parent, String name) {
        return children(parent, name).stream().findFirst();
    }

    private static List<Element> children(Element parent, String name) {
        return children(parent).stream().filter(element -> name.equals(element.getLocalName())).toList();
    }

    private static List<Element> children(Element parent) {
        List<Element> elements = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private DocumentBuilderFactory factory() {
        if (factory.isEmpty()) {
            factory = Optional.of(secureFactory());
        }
        return factory.get();
    }

    private static DocumentBuilderFactory secureFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("XML parser does not support secure processing", e);
        }
        return factory;
    }

    /** Replaces known {@code ${name}} expressions; unknown ones stay as written. */
    private record Interpolator(Map<String, String> properties) {

        String apply(String value) {
            String current = value;
            for (int round = 0; round < MAX_INTERPOLATION_ROUNDS && current.contains("${"); round++) {
                Matcher matcher = PROPERTY.matcher(current);
                StringBuilder result = new StringBuilder();
                while (matcher.find()) {
                    String replacement = properties.getOrDefault(matcher.group(1), matcher.group());
                    matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
                }
                matcher.appendTail(result);
                String next = result.toString();
                if (next.equals(current)) {
                    break;
                }
                current = next;
            }
            return current;
        }
    }
}
