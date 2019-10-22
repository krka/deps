package se.krka.deps;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class JsonReader {
  static ArtifactContainer fromJson(
          JSONObject object,
          Set<ArtifactContainer> dependencies,
          Set<ArtifactContainer> flattenedDependencies) {
    String groupId = object.getString("groupId");
    String artifactId = object.getString("artifactId");
    String version = object.getString("version");

    Set<String> definedClasses = readSet(object.getJSONArray("classes"));
    Map<String, Set<String>> mappings = readMappings(object.getJSONObject("usages"));

    Set<String> unused = readSet(object.getJSONArray("unused"));
    Set<String> undeclared = readSet(object.getJSONArray("undeclared"));

    Set<ArtifactContainer> unusedDependencies = filter(dependencies, unused);
    Set<ArtifactContainer> undeclaredDependencies = filter(dependencies, undeclared);

    return new ArtifactContainer(
            groupId, artifactId, version,
            dependencies, flattenedDependencies,
            unusedDependencies,
            definedClasses,
            mappings,
            undeclaredDependencies);
  }

  private static Set<ArtifactContainer> filter(Set<ArtifactContainer> dependencies, Set<String> names) {
    return dependencies.stream()
            .filter(artifact -> names.contains(artifact.getArtifactName()))
            .collect(Collectors.toSet());
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
