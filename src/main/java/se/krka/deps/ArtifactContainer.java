package se.krka.deps;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ArtifactContainer {

  private final String groupId;
  private final String artifactId;
  private final String version;

  // Direct declared dependencies
  private final Set<ArtifactContainer> dependencies;

  // All transitive dependencies
  private final Set<ArtifactContainer> flattenedDependencies;

  // Set of declared dependencies that are not used
  private final Set<ArtifactContainer> unusedDependencies;

  // Set of classes that this artifact defines
  private final Set<String> definedClasses;

  // Map of packages/class prefix -> artifacts that define that class
  private final Map<String, Set<String>> mappings;

  // Set of dependencies that are used, but not explicitly declared
  private final Set<ArtifactContainer> undeclared;


  public ArtifactContainer(
          String groupId,
          String artifactId,
          String version,
          Set<ArtifactContainer> dependencies,
          Set<ArtifactContainer> flattenedDependencies,
          Set<ArtifactContainer> unusedDependencies,
          Set<String> definedClasses,
          Map<String, Set<String>> mappings,
          Set<ArtifactContainer> undeclared) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
    this.dependencies = dependencies;
    this.flattenedDependencies = flattenedDependencies;
    this.unusedDependencies = unusedDependencies;
    this.definedClasses = definedClasses;
    this.mappings = mappings;
    this.undeclared = undeclared;
  }

  public boolean definesClass(String className) {
    return definedClasses.contains(className);
  }

  public Set<ArtifactContainer> getFlattenedDependencies() {
    return flattenedDependencies;
  }

  @Override
  public String toString() {
    return getCoordinate();
  }

  public void printDependencies(String indent) {
    mappings.forEach((prefix, artifacts) -> {
      if (artifacts.isEmpty()) {
        System.out.println(indent + prefix + " expected in runtime");
      } else {
        System.out.println(indent + prefix + " found in " + artifacts);
      }
    });

    unusedDependencies.stream().map(ArtifactContainer::getCoordinate).forEach(s ->
            System.out.println(indent + "Unused: " + s));
  }

  public void printUndeclaredDependencies() {
    if (!undeclared.isEmpty()) {
      System.out.println(getCoordinate() + " has undeclared dependencies on " + undeclared);
    }
  }

  public void printUnusedDependencies() {
    if (!unusedDependencies.isEmpty()) {
      Set<String> unused = unusedDependencies.stream().map(ArtifactContainer::getArtifactName).collect(Collectors.toSet());
      System.out.println(getCoordinate() + " has unused dependencies on " + unused);
    }
  }

  public Set<ArtifactContainer> getDependencies() {
    return dependencies;
  }

  public String getGroupId() {
    return groupId;
  }

  public String getArtifactId() {
    return artifactId;
  }

  public String getVersion() {
    return version;
  }

  public String getCoordinate() {
    return groupId +":" + artifactId + ":" + version;
  }

  public String getArtifactName() {
    return groupId + ":" + artifactId;
  }

  public Map<String, Set<String>> getMappings() {
    return mappings;
  }

  public Set<String> getDefinedClasses() {
    return definedClasses;
  }

  public Set<ArtifactContainer> getUnusedDependencies() {
    return unusedDependencies;
  }

  public Set<ArtifactContainer> getUndeclared() {
    return undeclared;
  }
}
