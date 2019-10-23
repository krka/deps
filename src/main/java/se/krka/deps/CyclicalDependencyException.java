package se.krka.deps;

import java.util.ArrayList;

public class CyclicalDependencyException extends RuntimeException {
  private final ArrayList<Coordinate> coordinates = new ArrayList<>();

  CyclicalDependencyException(Coordinate coordinate) {
    super("Found cyclical dependency: ");
    addCoordinate(coordinate);
  }

  void addCoordinate(Coordinate coordinate) {
    coordinates.add(coordinate);
  }

  @Override
  public String getMessage() {
    return super.getMessage() + coordinates;
  }
}
