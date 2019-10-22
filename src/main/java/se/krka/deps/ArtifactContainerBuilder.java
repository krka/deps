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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

class ArtifactContainerBuilder {

  private final String coordinate;
  private final String artifactName;

  // Direct declared dependencies
  private final Set<ArtifactContainer> dependencies;

  // Set of declared dependencies that are not used
  private final Set<ArtifactContainer> unusedDependencies = new HashSet<>();

  // Set of classes that this artifact defines
  private final Set<String> definedClasses = new HashSet<>();

  // Map of class -> artifacts that define that class
  private final Map<String, Set<ArtifactContainer>> dependsOnClasses = new HashMap<>();

  // Set of classes which were not found anywhere
  private final Set<String> unknownDependencies = new HashSet<>();

  private final MyClassVisitor myClassVisitor;

  public ArtifactContainerBuilder(String coordinate, String artifactName) {
    this.coordinate = coordinate;
    this.artifactName = artifactName;
    this.dependencies = new HashSet<>();
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
      return Set.of();
    }
    HashSet<ArtifactContainer> set = new HashSet<>();
    for (ArtifactContainer dependency : dependencies) {
      set.addAll(dependency.findContainers(className));
    }
    return set;
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

  @Override
  public String toString() {
    return coordinate;
  }

  ArtifactContainer build(File file) {
    loadClasses(file);

    dependsOnClasses.keySet().removeIf(definedClasses::contains);
    unknownDependencies.removeIf(definedClasses::contains);

    Set<String> allUsed = dependsOnClasses.values().stream()
            .flatMap(Collection::stream)
            .map(ArtifactContainer::getArtifactName)
            .collect(Collectors.toSet());

    unusedDependencies.addAll(dependencies);
    unusedDependencies.removeIf(artifactContainer -> allUsed.contains(artifactContainer.getArtifactName()));

    return new ArtifactContainer(
            coordinate, artifactName,
            dependencies,
            unusedDependencies,
            definedClasses,
            dependsOnClasses,
            unknownDependencies);
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
}
