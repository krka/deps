package se.krka.deps;

public class Main {
  public static void main(String[] args) {
    long t1 = System.currentTimeMillis();

    //Resolver resolver = Resolver.createFromPomfile("../sparkey-java/pom.xml");

    //Resolver resolver = Resolver.createFromProject("../sparkey-java/pom.xml");
    //Resolver resolver = Resolver.createFromProject("../sc2stats/pom.xml");
    //Resolver resolver = Resolver.createFromProject("../folsom/folsom/pom.xml");

    //Resolver resolver = Resolver.createFromCoordinate("com.spotify:missinglink-maven-plugin:0.1.1");
    Resolver resolver = Resolver.createFromCoordinate("com.spotify:futures-extra:4.2.1");

    resolver.printDependencyTree();
    resolver.showTransitiveProblems();

    long t2 = System.currentTimeMillis();
    long diff = t2 - t1;
    System.out.println("Time: " + diff + " ms");

    // TODO:
    // better tree view
    // caching
    // define API
    // proper return types
    // handle submodules
  }

}
