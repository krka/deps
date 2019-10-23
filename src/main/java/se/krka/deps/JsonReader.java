package se.krka.deps;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

class JsonReader {
  static IncompleteArtifact fromJson(JSONObject object) {

    Coordinate coordinate = readCoordinate(object.getJSONObject("coordinate"));

    Set<String> definedClasses = readSet(object.getJSONArray("classes"));
    Map<String, Set<String>> mappings = readMappings(object.getJSONObject("usages"));

    Set<String> unused = readSet(object.getJSONArray("unused"));
    Set<String> undeclared = readSet(object.getJSONArray("undeclared"));

    Set<Coordinate> dependencies = readDependencies(object.getJSONArray("dependencies"));

    return new IncompleteArtifact(
            coordinate,
            dependencies,
            definedClasses,
            mappings,
            unused,
            undeclared);
  }

  private static Set<Coordinate> readDependencies(JSONArray dependencies) {
    HashSet<Coordinate> result = new HashSet<>();
    int length = dependencies.length();
    for (int i = 0; i < length; i++) {
      JSONObject dependency = dependencies.getJSONObject(i);
      if (!dependency.getBoolean("transitive")) {
        result.add(readDependency(dependency));
      }
    }
    return result;
  }

  private static Coordinate readDependency(JSONObject jsonObject) {
    return readCoordinate(jsonObject.getJSONObject("coordinate"));
  }

  private static Coordinate readCoordinate(JSONObject coordinate) {
    return Coordinate.fromJson(coordinate);
  }

  private static Map<String, Set<String>> readMappings(JSONObject usages) {
    HashMap<String, Set<String>> mappings = new HashMap<>();
    Iterator<String> iterator = usages.keys();
    while (iterator.hasNext()) {
      String key = iterator.next();
      mappings.put(key, readSet(usages.getJSONArray(key)));
    }
    return mappings;
  }

  private static Set<String> readSet(JSONArray array) {
    HashSet<String> set = new HashSet<>();
    int length = array.length();
    for (int i = 0; i < length; i++) {
      set.add(array.getString(i));
    }
    return set;
  }
}
