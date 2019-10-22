package se.krka.deps;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ArtifactContainer {

  static final ArtifactContainer IN_PROGRESS_MARKER = new ArtifactContainer();

  private final String coordinate;
  private final String artifactName;

  // Direct declared dependencies
  private final Set<ArtifactContainer> dependencies;

  // Set of declared dependencies that are not used
  private final Set<ArtifactContainer> unusedDependencies;

  // Set of classes that this artifact defines
  private final Set<String> definedClasses;

  // Map of class -> artifacts that define that class
  private final Map<String, Set<ArtifactContainer>> dependsOnClasses;

  // Set of classes which were not found anywhere
  private final Set<String> unknownDependencies;


  public ArtifactContainer(
          String coordinate,
          String artifactName,
          Set<ArtifactContainer> dependencies,
          Set<ArtifactContainer> unusedDependencies,
          Set<String> definedClasses,
          Map<String, Set<ArtifactContainer>> dependsOnClasses,
          Set<String> unknownDependencies) {
    this.coordinate = coordinate;
    this.artifactName = artifactName;
    this.dependencies = dependencies;
    this.unusedDependencies = unusedDependencies;
    this.definedClasses = definedClasses;
    this.dependsOnClasses = dependsOnClasses;
    this.unknownDependencies = unknownDependencies;
  }

  private ArtifactContainer() {
    coordinate = null;
    artifactName = null;
    dependencies = null;
    unusedDependencies = null;
    definedClasses = null;
    dependsOnClasses = null;
    unknownDependencies = null;
  }

  Set<ArtifactContainer> findContainers(String className) {
    if (definedClasses.contains(className)) {
      return Set.of(this);
    }
    HashSet<ArtifactContainer> set = new HashSet<>();
    for (ArtifactContainer dependency : dependencies) {
      set.addAll(dependency.findContainers(className));
    }
    return Set.copyOf(set);
  }

  @Override
  public String toString() {
    return coordinate;
  }

  private Node toPackageTree() {
    Node root = new Node();
    dependsOnClasses.forEach((className, foundIn) -> {
      root.add(className, foundIn);
    });
    unknownDependencies.forEach(className -> {
      root.add(className, Set.of());
    });

    root.simplify();
    return root;
  }

  public void printDependencies(String indent) {
    Node node = toPackageTree();
    Map<String, Set<ArtifactContainer>> map = node.prettyPrint();
    Map<Set<ArtifactContainer>, List<Map.Entry<String, Set<ArtifactContainer>>>> byFoundIn = map.entrySet().stream()
            .collect(Collectors.groupingBy(Map.Entry::getValue));

    unusedDependencies.stream().map(ArtifactContainer::getCoordinate).forEach(s ->
            System.out.println(indent + "Unused: " + s));

    if (byFoundIn.isEmpty()) {
      System.out.println(indent + "<none>");
      return;
    }

    byFoundIn.forEach((foundIn, entry) -> {
      if (!foundIn.isEmpty()) {
        System.out.println(indent + foundIn + " for classes " + entry.stream().map(Map.Entry::getKey).collect(Collectors.toSet()));
      }
    });
    byFoundIn.forEach((foundIn, entry) -> {
      if (foundIn.isEmpty()) {
        System.out.println(indent + "(Provided by runtime) for classes " + entry.stream().map(Map.Entry::getKey).collect(Collectors.toSet()));
      }
    });
  }

  public void showTransitive() {
    Set<String> undeclared = dependsOnClasses.values().stream()
            .filter(this::isMissing)
            .flatMap(Collection::stream)
            .map(ArtifactContainer::getArtifactName)
            .collect(Collectors.toSet());
    if (!undeclared.isEmpty()) {
      System.out.println(this.coordinate + " has undeclared dependencies on " + undeclared);
    }
  }

  private boolean isMissing(Set<ArtifactContainer> containers) {
    return containers.stream()
            .filter(dependencies::contains)
            .findAny()
            .isEmpty();
  }

  public Set<ArtifactContainer> getDependencies() {
    return dependencies;
  }

  public String getCoordinate() {
    return coordinate;
  }

  public String getArtifactName() {
    return artifactName;
  }

  private static class Node {
    private final Map<String, Node> nodes;
    private final Map<String, Set<ArtifactContainer>> classes;

    Node() {
      this.nodes = new HashMap<>();
      this.classes = new HashMap<>();
    }

    void add(String className, Set<ArtifactContainer> foundIn) {
      Node node = this;
      String[] parts = className.split("/");
      String lastPart = parts[parts.length - 1];
      for (int i = 0; i < parts.length - 1; i++) {
        node = node.add(parts[i]);
      }
      node.addClass(lastPart, foundIn);
    }

    private void addClass(String className, Set<ArtifactContainer> foundIn) {
      classes.put(className, foundIn);
    }

    private Node add(String part) {
      return nodes.computeIfAbsent(part, part1 -> new Node());
    }

    private Set<Set<ArtifactContainer>> getDependencies() {
      Set<Set<ArtifactContainer>> collected = nodes.values().stream()
              .flatMap(node -> node.getDependencies().stream())
              .collect(Collectors.toSet());
      collected.addAll(classes.values());
      return collected;
    }

    void simplify() {
      nodes.forEach((s, node) -> node.simplify());
      Set<Set<ArtifactContainer>> dependencies = getDependencies();
      if (dependencies.isEmpty()) {
        return;
      }
      if (dependencies.size() == 1) {
        if (!classes.isEmpty()) {
          classes.clear();
          classes.put("*", dependencies.iterator().next());
          nodes.clear();
        }
      }
    }

    Map<String, Set<ArtifactContainer>> prettyPrint() {
      return prettyPrint("", new HashMap<>());
    }

    private Map<String, Set<ArtifactContainer>> prettyPrint(String path, Map<String, Set<ArtifactContainer>> map) {
      String prepend = path.isEmpty() ? "" : path + ".";
      nodes.forEach((s, node) -> {
        node.prettyPrint(prepend + s, map);
      });
      if (!classes.isEmpty()) {
        classes.forEach((className, foundIn) -> map.put(prepend + className, foundIn));
      }
      return map;
    }
  }
}
