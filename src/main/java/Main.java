import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.BuiltProject;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.EmbeddedMaven;

import java.io.File;
import java.util.List;

public class Main {
  public static void main(String[] args) {

    String filename = "../folsom/folsom/pom.xml";

    BuiltProject builtProject = EmbeddedMaven.forProject(filename)
            .setGoals("clean", "package")
            .build();

    File targetDirectory = builtProject.getTargetDirectory();
    System.out.println("target: " + targetDirectory);
    List<Archive> archives = builtProject.getArchives();

  }
}
