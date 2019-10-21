import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class ArtifactContainer {

  private final String coordinate;
  private final String artifactName;

  private final List<ArtifactContainer> dependencies;
  private final Set<ArtifactContainer> unusedDependencies = new HashSet<>();

  private final Set<String> definedClasses = new HashSet<>();
  private final Map<String, Set<ArtifactContainer>> dependsOnClasses = new HashMap<>();
  private final Set<String> unknownDependencies = new HashSet<>();

  private final MyClassVisitor myClassVisitor;

  private boolean resolved;

  public ArtifactContainer(String coordinate, String artifactName) {
    this.coordinate = coordinate;
    this.artifactName = artifactName;
    this.dependencies = new ArrayList<>();
    this.myClassVisitor = new MyClassVisitor(this);
  }

  public void addClass(String className) {
    if (className.startsWith("[")) {
      throw new IllegalArgumentException("Unexpected class: " + className);
    }
    if (dependsOnClasses.containsKey(className) || unknownDependencies.contains(className)) {
      return;
    }

    Set<ArtifactContainer> containers = findContainers(className);
    if (containers.isEmpty()) {
      unknownDependencies.add(className);
    } else {
      dependsOnClasses.put(className, containers);
    }
  }

  private Set<ArtifactContainer> findContainers(String className) {
    if (definedClasses.contains(className)) {
      return Set.of(this);
    }
    HashSet<ArtifactContainer> set = new HashSet<>();
    for (ArtifactContainer dependency : dependencies) {
      set.addAll(dependency.findContainers(className));
    }
    return Set.copyOf(set);
  }

  public void addDefinition(String className) {
    definedClasses.add(className);
  }

  void addDescriptor(String descriptor) {
    addDescriptor(Type.getType(descriptor));
  }

  public void addOwner(String owner) {
    if (owner.startsWith("[")) {
      addDescriptor(owner);
    } else {
      addClass(owner);
    }
  }

  void addDescriptor(Type type) {
    switch (type.getSort()) {
      case Type.ARRAY:
        addDescriptor(type.getElementType());
        break;
      case Type.OBJECT:
        addClass(type.getInternalName());
        break;
      case Type.METHOD:
        addDescriptor(type.getReturnType());
        for (Type argumentType : type.getArgumentTypes()) {
          addDescriptor(argumentType);
        }
        break;
      default:
        // Do nothing
    }
  }

  @Override
  public String toString() {
    return coordinate;
  }

  public Node toPackageTree() {
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
    Map<String, Set<ArtifactContainer>> map = node.prettyPrint("");
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

  public boolean isResolved() {
    return resolved;
  }

  public void populate(File file) {
    loadClasses(file);

    dependsOnClasses.keySet().removeIf(definedClasses::contains);
    unknownDependencies.removeIf(definedClasses::contains);

    Set<String> allUsed = dependsOnClasses.values().stream()
            .flatMap(Collection::stream)
            .map(ArtifactContainer::getArtifactName)
            .collect(Collectors.toSet());

    unusedDependencies.addAll(dependencies);
    unusedDependencies.removeIf(artifactContainer -> allUsed.contains(artifactContainer.getArtifactName()));

    resolved = true;
  }

  private void loadClasses(File file) {
    try {
      if (file.isFile() && file.getName().endsWith(".jar")) {
        loadJarFile(file);
      } else if (file.isFile() && file.getName().endsWith(".class")) {
        loadClassFile(file);
      } else if (file.isDirectory()) {
        loadClassDirectory(file);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void loadClassDirectory(File directory) {
    File[] files = directory.listFiles();
    if (files != null) {
      for (File file : files) {
        loadClasses(file);
      }
    }
  }

  private void loadJarFile(File file) throws IOException {
    try (JarFile jarFile = new JarFile(file)) {
      Enumeration<JarEntry> entries = jarFile.entries();
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.getName().endsWith(".class")) {
          try (InputStream inputStream = jarFile.getInputStream(entry)) {
            loadClass(inputStream);
          }
        }
      }
    }
  }

  private void loadClass(InputStream inputStream) throws IOException {
    ClassReader classReader = new ClassReader(inputStream);
    classReader.accept(myClassVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
  }

  private void loadClassFile(File file) throws IOException {
    try (InputStream inputStream = new FileInputStream(file)) {
      loadClass(inputStream);
    }
  }

  public void addDependency(ArtifactContainer coordinate) {
    dependencies.add(coordinate);
  }

  public List<ArtifactContainer> getDependencies() {
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

    public Node() {
      this.nodes = new HashMap<>();
      this.classes = new HashMap<>();
    }

    public void add(String className, Set<ArtifactContainer> foundIn) {
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

    public void simplify() {
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

    public Map<String, Set<ArtifactContainer>> prettyPrint(String path) {
      return prettyPrint(path, new HashMap<>());
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
