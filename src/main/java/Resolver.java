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
import java.util.stream.Collectors;

public class Resolver {
  private final Map<String, ArtifactContainer> artifacts = new HashMap<>();
  private final Map<String, List<String>> dependencies = new HashMap<>();

  private List<ArtifactContainer> roots = new ArrayList<>();

  public static Resolver create(List<MavenResolvedArtifact> artifacts) {
    Resolver resolver = new Resolver();
    System.out.println("Resolving dependencies:");
    for (MavenResolvedArtifact artifact : artifacts) {
      resolver.roots.add(resolver.resolve(artifact.getCoordinate()));
    }
    return resolver;
  }

  public static Resolver createFromPomfile(String filename) {
    List<MavenResolvedArtifact> artifacts = Maven.resolver().loadPomFromFile(filename)
            .importDependencies(ScopeType.COMPILE)
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

    File file = new File(builtProject.getTargetDirectory(), "classes");
    // TODO: use getArchive() instead

    List<MavenResolvedArtifact> dependencies = builtProject.getModel()
            .getDependencies().stream()
            .filter(dependency -> dependency.getScope().equals("compile"))
            .map(dependency -> dependency.getGroupId() + ":" + dependency.getArtifactId() + ":" + dependency.getVersion())
            .map(coordinate -> Maven.resolver().resolve(coordinate)
                    .withoutTransitivity().asSingleResolvedArtifact())
            .collect(Collectors.toList());

    String groupId = builtProject.getModel().getGroupId();
    String artifactId = builtProject.getModel().getArtifactId();
    String version = builtProject.getModel().getVersion();

    String artifactName = groupId + ":" + artifactId;
    String coordinate = groupId + ":" + artifactId + ":" + version;

    Resolver resolver = new Resolver();
    resolver.roots.add(resolver.resolve(artifactName, coordinate, dependencies, file));

    return resolver;
  }

  private ArtifactContainer resolve(MavenArtifactInfo artifact) {
    return resolve(artifact.getCoordinate());
  }

  private ArtifactContainer resolve(MavenCoordinate coordinate1) {
    String coordinate = getCoordinate(coordinate1);

    ArtifactContainer container = artifacts.get(coordinate);
    if (container != null) {
      if (container.isResolved()) {
        return container;
      }
      throw new CyclicalDependencyException(coordinate);
    }

    MavenResolvedArtifact resolvedArtifact = Maven.resolver()
            .resolve(coordinate)
            .withoutTransitivity()
            .asSingleResolvedArtifact();
    return resolve(resolvedArtifact);
  }

  private ArtifactContainer resolve(MavenResolvedArtifact artifact) {
    MavenCoordinate coordinate = artifact.getCoordinate();
    MavenArtifactInfo[] dependencies = artifact.getDependencies();
    File file = artifact.asFile();
    return resolve(coordinate, dependencies, file);
  }

  private ArtifactContainer resolve(MavenCoordinate coordinate1, MavenArtifactInfo[] dependencies, File file) {
    String coordinate = getCoordinate(coordinate1);
    return resolve(getArtifactName(coordinate1), coordinate, dependencies, file);
  }

  private ArtifactContainer resolve(String artifactName, String coordinate, List<? extends MavenArtifactInfo> dependencies, File file) {
    List<String> dependencyCoordinates = dependencies.stream()
            .map(MavenArtifactInfo::getCoordinate)
            .map(Resolver::getCoordinate)
            .collect(Collectors.toList());

    ArtifactContainer container = artifacts.get(coordinate);
    if (container != null) {
      if (container.isResolved()) {
        return container;
      }
      throw new CyclicalDependencyException(coordinate);
    }
    container = new ArtifactContainer(coordinate, artifactName);
    artifacts.put(coordinate, container);

    try {
      this.dependencies.put(coordinate, dependencyCoordinates);
      for (MavenArtifactInfo dependency : dependencies) {
        ArtifactContainer resolvedDependency = resolve(dependency);
        container.addDependency(resolvedDependency);
      }
    } catch (CyclicalDependencyException e) {
      e.addCoordinate(coordinate);
      throw e;
    }

    container.populate(file);
    return container;
  }

  private ArtifactContainer resolve(String artifactName, String coordinate, MavenArtifactInfo[] dependencies, File file) {
    return resolve(artifactName, coordinate, Arrays.asList(dependencies), file);
  }

  private static String getCoordinate(MavenCoordinate coordinate) {
    return coordinate.getGroupId() + ":" + coordinate.getArtifactId() + ":" + coordinate.getVersion();
  }

  private static String getArtifactName(MavenCoordinate coordinate) {
    return coordinate.getGroupId() + ":" + coordinate.getArtifactId();
  }

  public void printDependencyTree() {
    System.out.println("Dependency tree:");
    printDependencies("  ", new HashSet<>(), roots);
  }

  private void printDependencies(String indent, Set<ArtifactContainer> visited, Collection<ArtifactContainer> artifacts) {
    String nextIndent = indent + "  ";
    for (ArtifactContainer value : artifacts) {
      List<ArtifactContainer> dependencies = value.getDependencies();
      boolean hasDependencies = !dependencies.isEmpty();
      boolean doVisit = hasDependencies && visited.add(value);
      boolean truncated = hasDependencies && !doVisit;
      System.out.println(indent + value.getCoordinate() + (truncated ? " (truncated)" : ""));
      if (doVisit) {
        System.out.println(nextIndent + "actual:");
        value.printDependencies(nextIndent + "  ");
        System.out.println(nextIndent + "declared:");
        printDependencies(nextIndent + "  ", visited, dependencies);
      }
    }
  }
}
