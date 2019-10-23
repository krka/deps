package se.krka.deps;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class IncompleteArtifact {
  private final Coordinate coordinate;
  private final Set<Coordinate> dependencies;
  private final Set<String> definedClasses;
  private final Map<String, Set<String>> mappings;
  private final Set<String> unused;
  private final Set<String> undeclared;

  IncompleteArtifact(
          Coordinate coordinate,
          Set<Coordinate> dependencies,
          Set<String> definedClasses,
          Map<String, Set<String>> mappings,
          Set<String> unused,
          Set<String> undeclared) {

    this.coordinate = coordinate;
    this.dependencies = dependencies;
    this.definedClasses = definedClasses;
    this.mappings = mappings;
    this.unused = unused;
    this.undeclared = undeclared;
  }

  Set<Coordinate> getDependencies() {
    return dependencies;
  }

  ArtifactContainer complete(Set<ArtifactContainer> dependencies) {
    HashSet<ArtifactContainer> flattenedDependencies = new HashSet<>(dependencies);
    for (ArtifactContainer dependency : dependencies) {
      flattenedDependencies.addAll(dependency.getFlattenedDependencies());
    }

    Set<ArtifactContainer> unusedDependencies = filter(dependencies, unused);
    Set<ArtifactContainer> undeclaredDependencies = filter(dependencies, undeclared);
    return new ArtifactContainer(coordinate, dependencies, flattenedDependencies,
            unusedDependencies, definedClasses, mappings, undeclaredDependencies);
  }

  private static Set<ArtifactContainer> filter(Set<ArtifactContainer> dependencies, Set<String> names) {
    return dependencies.stream()
            .filter(artifact -> names.contains(artifact.getArtifactName()))
            .collect(Collectors.toSet());
  }

}
