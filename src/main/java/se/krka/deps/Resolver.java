package se.krka.deps;

import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenArtifactInfo;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.BuiltProject;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.EmbeddedMaven;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Resolver {
  // Map of artifact name -> artifact
  private final Map<String, ArtifactContainer> artifacts = new HashMap<>();

  private final List<ArtifactContainer> roots = new ArrayList<>();

  private final ArtifactCache artifactCache = ArtifactCache.getDefault();

  private static Resolver create(List<MavenResolvedArtifact> artifacts) {
    Resolver resolver = new Resolver();
    System.out.println("Resolving dependencies:");
    for (MavenResolvedArtifact artifact : artifacts) {
      resolver.roots.add(resolver.resolve(artifact.getCoordinate()));
    }
    return resolver;
  }

  public static Resolver createFromPomfile(String filename) {
    List<MavenResolvedArtifact> artifacts = Maven.resolver().loadPomFromFile(filename)
            .importDependencies(ScopeType.COMPILE, ScopeType.PROVIDED)
            .resolve().withTransitivity().asList(MavenResolvedArtifact.class);

    return create(artifacts);
  }

  public static Resolver createFromCoordinate(String coordinate) {
    List<MavenResolvedArtifact> artifacts = Maven.resolver()
            .resolve(coordinate)
            .withTransitivity()
            .asList(MavenResolvedArtifact.class);
    return create(artifacts);
  }

  public static Resolver createFromProject(String filename) {
    BuiltProject builtProject = EmbeddedMaven.forProject(filename)
            .setGoals("clean", "package")
            .build();

    Resolver resolver = new Resolver();
    addModules(resolver, builtProject);
    return resolver;
  }

  private static void addModules(Resolver resolver, BuiltProject module) {
    addRoot(resolver, module);
    module.getModules().forEach(submodule -> addModules(resolver, submodule));
  }

  private static void addRoot(Resolver resolver, BuiltProject builtProject) {
    List<MavenResolvedArtifact> dependencies = builtProject.getModel()
            .getDependencies().stream()
            .filter(dependency -> Set.of("compile", "provided").contains(dependency.getScope()))
            .map(dependency -> dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion())
            .map(coordinate -> Maven.resolver().resolve(coordinate)
                    .withoutTransitivity().asSingleResolvedArtifact())
            .collect(Collectors.toList());

    String groupId = builtProject.getModel().getGroupId();
    String artifactId = builtProject.getModel().getArtifactId();
    String version = builtProject.getModel().getVersion();

    File file = new File(builtProject.getTargetDirectory(), "classes");
    // TODO: use getArchive() instead

    resolver.roots.add(resolver.resolve(groupId, artifactId, version, dependencies, file));
  }

  private ArtifactContainer resolve(MavenArtifactInfo artifact) {
    return resolve(artifact.getCoordinate());
  }

  ArtifactContainer resolve(String groupId, String artifactId, String version) {
    return resolve(groupId + ":" + artifactId + ":" + version);
  }

  private ArtifactContainer resolve(MavenCoordinate coordinate) {
    return resolve(getCoordinate(coordinate));
  }

  private ArtifactContainer resolve(String coordinate) {
    MavenResolvedArtifact resolvedArtifact = resolveMavenArtifact(coordinate);
    return resolve(resolvedArtifact);
  }

  MavenResolvedArtifact resolveMavenArtifact(String coordinate) {
    return Maven.resolver()
              .resolve(coordinate)
              .withoutTransitivity()
              .asSingleResolvedArtifact();
  }

  private ArtifactContainer resolve(MavenResolvedArtifact artifact) {
    MavenCoordinate coordinate = artifact.getCoordinate();
    MavenArtifactInfo[] dependencies = artifact.getDependencies();
    File file = artifact.asFile();
    return resolve(coordinate, dependencies, file);
  }

  private ArtifactContainer resolve(MavenCoordinate coordinate, MavenArtifactInfo[] dependencies, File file) {
    String groupId = coordinate.getGroupId();
    String artifactId = coordinate.getArtifactId();
    String version = coordinate.getVersion();
    return resolve(groupId, artifactId, version, dependencies, file);
  }

  private ArtifactContainer resolve(String groupId, String artifactId, String version, MavenArtifactInfo[] dependencies, File file) {
    return resolve(groupId, artifactId, version, Arrays.asList(dependencies), file);
  }

  private ArtifactContainer resolve(
          String groupId, String artifactId, String version,
          List<? extends MavenArtifactInfo> dependencies, File file) {
    String coordinate = groupId + ":" + artifactId + ":" + version;
    if (artifacts.containsKey(coordinate)) {
      ArtifactContainer container = artifacts.get(coordinate);
      if (container != null) {
        return container;
      }
      throw new CyclicalDependencyException(coordinate);
    }
    artifacts.put(coordinate, null);

    Set<ArtifactContainer> artifactDependencies = new HashSet<>();
    try {
      for (MavenArtifactInfo dependency : dependencies) {
        artifactDependencies.add(resolve(dependency));
      }
    } catch (CyclicalDependencyException e) {
      e.addCoordinate(coordinate);
      throw e;
    }

    ArtifactContainer container = artifactCache.resolve(
            groupId, artifactId, version,
            artifactDependencies,
            () -> new ArtifactContainerBuilder(groupId, artifactId, version, artifactDependencies).build(file));
    artifacts.put(coordinate, container);
    return container;
  }

  private static String getCoordinate(MavenCoordinate coordinate) {
    return coordinate.getGroupId() + ":" + coordinate.getArtifactId() + ":" + coordinate.getVersion();
  }

  public List<ArtifactContainer> getRoots() {
    return roots;
  }

  public Map<String, ArtifactContainer> getArtifacts() {
    return artifacts;
  }

  public void printDependencyTree() {
    System.out.println("Dependency tree:");
    printDependencies("  ", new HashMap<>(), roots, new AtomicInteger());
  }

  private void printDependencies(String indent,
                                 Map<ArtifactContainer, Integer> visited,
                                 Collection<ArtifactContainer> artifacts,
                                 AtomicInteger currentLine) {
    String nextIndent = indent + "  ";
    for (ArtifactContainer value : artifacts) {
      Set<ArtifactContainer> dependencies = value.getDependencies();
      Integer lineNumber = visited.get(value);
      if (lineNumber != null) {
        String s = indent + value.getCoordinate() + " (see #" + lineNumber + ")";
        System.out.println("     " + s);
      } else {
        String s = indent + value.getCoordinate();
        printWithLine(currentLine, s);
        visited.put(value, currentLine.get());

        if (!dependencies.isEmpty()) {
          //System.out.println("     " + nextIndent + "actual:");
          //value.printDependencies(nextIndent + "  ");
          //System.out.println("     " + nextIndent + "declared:");
          printDependencies(nextIndent + "  ", visited, dependencies, currentLine);
        }
      }
    }
  }

  private void printWithLine(AtomicInteger currentLine, String s) {
    int num = currentLine.incrementAndGet();
    System.out.printf("%3d: %s\n", num, s);
  }

  public void printUnusedWarnings() {
    Set<ArtifactContainer> unused = artifacts.values().stream()
            .filter(container -> !container.getUnusedDependencies().isEmpty())
            .collect(Collectors.toSet());

    if (!unused.isEmpty()) {
      System.out.println("Unused dependencies:");
      unused.forEach(ArtifactContainer::printUnusedDependencies);
    }
  }

  public void printUndeclaredWarnings() {
    Set<ArtifactContainer> undeclared = artifacts.values().stream()
            .filter(container -> !container.getUndeclared().isEmpty())
            .collect(Collectors.toSet());
    if (!undeclared.isEmpty()) {
      System.out.println("Undeclared dependencies:");
      undeclared.forEach(ArtifactContainer::printUndeclaredDependencies);
    }
  }
}
