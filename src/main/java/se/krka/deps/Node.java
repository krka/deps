package se.krka.deps;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class Node<T> {
  private final Map<String, Node<T>> nodes = new HashMap<>();
  private final Map<String, Set<T>> classes = new HashMap<>();
  private Set<Set<T>> dependencies;

  private Node() {
  }

  static <T> Map<String, Set<T>> getDependencyMap(Map<String, Set<T>> dependencies) {
    Node<T> root = new Node<>();
    dependencies.forEach(root::add);
    root.simplify();
    return root.toMap();
  }

  private void add(String className, Set<T> foundIn) {
    Node<T> node = this;
    String[] parts = className.split("\\.");
    String lastPart = parts[parts.length - 1];
    for (int i = 0; i < parts.length - 1; i++) {
      node = node.add(parts[i]);
    }
    node.classes.put(lastPart, foundIn);
  }

  Map<String, Set<T>> toMap() {
    return toMap("", new HashMap<>());
  }

  private Map<String, Set<T>> toMap(String path, Map<String, Set<T>> map) {
    String prepend = path.isEmpty() ? "" : path + ".";
    nodes.forEach((s, node) -> node.toMap(prepend + s, map));
    if (!classes.isEmpty()) {
      classes.forEach((className, foundIn) -> map.put(prepend + className, foundIn));
    }
    return map;
  }


  private Set<Set<T>> getDependencies() {
    if (dependencies == null) {
      dependencies = nodes.values().stream()
              .flatMap(node -> node.getDependencies().stream())
              .collect(Collectors.toSet());
      dependencies.addAll(classes.values());
    }
    return dependencies;
  }

  private void simplify() {
    nodes.values().forEach(Node::simplify);

    Set<Set<T>> dependencies = getDependencies();
    if (dependencies.size() == 1) {
      classes.clear();
      classes.put("**", dependencies.iterator().next());
      nodes.clear();
    } else {
      Set<Set<T>> classDependencies = new HashSet<>(classes.values());
      if (classDependencies.size() == 1) {
        classes.clear();
        classes.put("*", classDependencies.iterator().next());
      }

    }
  }

  private Node<T> add(String part) {
    return nodes.computeIfAbsent(part, p -> new Node<>());
  }
}

