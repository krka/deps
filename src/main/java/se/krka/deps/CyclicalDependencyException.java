package se.krka.deps;

import java.util.ArrayList;

public class CyclicalDependencyException extends RuntimeException {
  private final ArrayList<Object> coordinates = new ArrayList<>();

  CyclicalDependencyException(String coordinate) {
    super("Found cyclical dependency: ");
    addCoordinate(coordinate);
  }

  void addCoordinate(String coordinate) {
    coordinates.add(coordinate);
  }

  @Override
  public String getMessage() {
    return super.getMessage() + coordinates;
  }
}
