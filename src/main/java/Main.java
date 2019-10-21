import java.io.IOException;

public class Main {
  public static void main(String[] args) throws IOException {
    long t1 = System.currentTimeMillis();
    //String filename = "../sparkey-java/pom.xml";
    String filename = "../sc2stats/pom.xml";
    //String filename = "../folsom/folsom/pom.xml";

    //Resolver resolver = Resolver.createFromPomfile(filename);
    //Resolver resolver = Resolver.createFromProject(filename);
    Resolver resolver = Resolver.createFromCoordinate("com.spotify:missinglink-maven-plugin:0.1.1");

    // TODO: also show direct usages of transitive dependencies
    resolver.printDependencyTree();
    resolver.showTransitiveProblems();

    long t2 = System.currentTimeMillis();
    long diff = t2 - t1;
    System.out.println("Time: " + diff + " ms");
  }

}
