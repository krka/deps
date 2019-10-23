package se.krka.deps;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

class ArtifactContainerBuilder {

  private final Coordinate coordinate;

  // Direct declared dependencies
  private final Set<ArtifactContainer> dependencies;

  // Set of classes that are defined in this artifact
  private final Set<String> definedClasses = new HashSet<>();

  // Set of classes that are referenced from this artifact
  private final Set<String> usedClasses = new HashSet<>();

  private final MyClassVisitor myClassVisitor;

  ArtifactContainerBuilder(
          Coordinate coordinate,
          Set<ArtifactContainer> dependencies) {
    this.coordinate = coordinate;
    this.dependencies = dependencies;
    this.myClassVisitor = new MyClassVisitor(this);
  }

  void addDefinition(String className) {
    definedClasses.add(className);
  }

  void addOwner(String owner) {
    if (owner.startsWith("[")) {
      addDescriptor(owner);
    } else {
      addClass(owner);
    }
  }

  void addDescriptor(String descriptor) {
    addDescriptor(Type.getType(descriptor));
  }

  private void addDescriptor(Type type) {
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

  void addClass(String className) {
    if (className.startsWith("[")) {
      throw new IllegalArgumentException("Unexpected class: " + className);
    }
    usedClasses.add(className);
  }

  private Coordinate getCoordinate() {
    return coordinate;
  }

  ArtifactContainer build(File file) {
    loadClasses(file);

    usedClasses.removeAll(definedClasses);

    Set<ArtifactContainer> flattenedDependencies = new HashSet<>(dependencies);
    for (ArtifactContainer dependency : dependencies) {
      flattenedDependencies.addAll(dependency.getFlattenedDependencies());
    }

    // Map of class -> artifacts that define that class
    final Map<String, Set<ArtifactContainer>> dependsOnClasses = new HashMap<>();

    for (String className : usedClasses) {
      dependsOnClasses.put(className, findContainers(className, flattenedDependencies));
    }

    Set<String> allUsed = dependsOnClasses.values().stream()
                    .flatMap(Collection::stream)
                    .map(ArtifactContainer::getArtifactName)
                    .collect(Collectors.toSet());

    // Set of declared dependencies that are not used
    final Set<ArtifactContainer> unusedDependencies = new HashSet<>(dependencies);
    unusedDependencies.removeIf(artifactContainer -> allUsed.contains(artifactContainer.getArtifactName()));

    Set<ArtifactContainer> undeclared = dependsOnClasses.values().stream()
                    .filter(this::isMissing)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());

    Map<String, Set<ArtifactContainer>> dependencyMap = Node.getDependencyMap(dependsOnClasses);
    Map<String, Set<String>> mappings = dependencyMap.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> mapToName(e.getValue())));
    mappings = new TreeMap<>(mappings);


    return new ArtifactContainer(
            coordinate,
            dependencies,
            flattenedDependencies,
            unusedDependencies,
            definedClasses,
            mappings,
            undeclared);
  }

  private Set<String> mapToName(Set<ArtifactContainer> value) {
    return value.stream().map(ArtifactContainer::getArtifactName).collect(Collectors.toSet());
  }

  private Set<ArtifactContainer> findContainers(String className, Set<ArtifactContainer> flattenedDependencies) {
    HashSet<ArtifactContainer> set = new HashSet<>();
    for (ArtifactContainer dependency : flattenedDependencies) {
      if (dependency.definesClass(className)) {
        set.add(dependency);
      }
    }
    return set;
  }

  private boolean isMissing(Set<ArtifactContainer> containers) {
    return containers.stream()
            .filter(dependencies::contains)
            .findAny()
            .isEmpty();
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

  private void loadClassFile(File file) throws IOException {
    try (InputStream inputStream = new FileInputStream(file)) {
      loadClass(inputStream);
    }
  }

  private void loadClass(InputStream inputStream) throws IOException {
    ClassReader classReader = new ClassReader(inputStream);
    classReader.accept(myClassVisitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
  }
}
