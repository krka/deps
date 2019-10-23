package se.krka.deps;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class NodeTest {
  @Test
  public void testSimple() {
    Map<String, Set<String>> input = Map.of("java.lang.String", Set.of("first"));
    Map<String, Set<String>> output = Node.getDependencyMap(input);
    assertEquals(Map.of("**", Set.of("first")), output);
  }

  @Test
  public void testMerged() {
    Map<String, Set<String>> input = Map.of(
            "java.lang.String", Set.of("first"),
            "java.lang.Object", Set.of("first")
            );
    Map<String, Set<String>> output = Node.getDependencyMap(input);
    assertEquals(Map.of("**", Set.of("first")), output);
  }

  @Test
  public void testMergedPackage() {
    Map<String, Set<String>> input = Map.of(
            "java.lang.String", Set.of("first"),
            "java.lang.Object", Set.of("first"),
            "java.util.Date", Set.of("second")
            );
    Map<String, Set<String>> output = Node.getDependencyMap(input);
    assertEquals(Map.of(
            "java.lang.**", Set.of("first"),
            "java.util.**", Set.of("second")
            ), output);
  }

  @Test
  public void testSplitClasses() {
    Map<String, Set<String>> input = Map.of(
            "java.lang.String", Set.of("first"),
            "java.lang.Object", Set.of("first"),
            "java.lang.util.Something", Set.of("second")
            );
    Map<String, Set<String>> output = Node.getDependencyMap(input);
    assertEquals(Map.of(
            "java.lang.*", Set.of("first"),
            "java.lang.util.**", Set.of("second")
            ), output);
  }

  @Test
  public void testCantMerge() {
    Map<String, Set<String>> input = Map.of(
            "java.lang.String", Set.of("first"),
            "java.lang.Object", Set.of("second")
            );
    Map<String, Set<String>> output = Node.getDependencyMap(input);
    assertEquals(input, output);
  }

  @Test
  public void testNoPackageMerge() {
    Map<String, Set<String>> input = Map.of(
            "String", Set.of("first"),
            "Object", Set.of("first")
            );
    Map<String, Set<String>> output = Node.getDependencyMap(input);
    assertEquals(Map.of("**", Set.of("first")), output);
  }

  @Test
  public void testNoPackageDontMerge() {
    Map<String, Set<String>> input = Map.of(
            "String", Set.of("first"),
            "Object", Set.of("first"),
            "java.Something", Set.of("second")
            );
    Map<String, Set<String>> output = Node.getDependencyMap(input);
    Map<String, Set<String>> expected = Map.of(
            "*", Set.of("first"),
            "java.**", Set.of("second"));
    assertEquals(expected, output);
  }
}