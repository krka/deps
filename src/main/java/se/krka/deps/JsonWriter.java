package se.krka.deps;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Map;
import java.util.Set;

class JsonWriter {
  static JSONObject toJsonObject(ArtifactContainer container) {
    JSONObject object = new JSONObject();
    object.put("groupId", container.getGroupId());
    object.put("artifactId", container.getArtifactId());
    object.put("version", container.getVersion());
    object.put("dependencies", getDeclaredDependencies(container));
    object.put("usages", getUsages(container));
    object.put("classes", getClasses(container));
    object.put("unused", getUnused(container));
    object.put("undeclared", getUndeclared(container));
    return object;
  }

  private static JSONArray getUnused(ArtifactContainer container) {
    JSONArray array = new JSONArray();
    container.getUndeclared().stream()
            .map(ArtifactContainer::getArtifactName)
            .forEach(array::put);
    return array;
  }

  private static JSONArray getUndeclared(ArtifactContainer container) {
    JSONArray array = new JSONArray();
    container.getUnusedDependencies().stream()
            .map(ArtifactContainer::getArtifactName)
            .forEach(array::put);
    return array;
  }

  private static JSONArray getDeclaredDependencies(ArtifactContainer container) {
    JSONArray array = new JSONArray();
    Set<ArtifactContainer> directDependencies = container.getDependencies();
    for (ArtifactContainer dependency : directDependencies) {
      array.put(getDependency(dependency, false));
    }
    for (ArtifactContainer dependency : container.getFlattenedDependencies()) {
      if (!directDependencies.contains(dependency)) {
        array.put(getDependency(dependency, true));
      }
    }
    return array;
  }

  private static JSONObject getUsages(ArtifactContainer container) {
    JSONObject object = new JSONObject();
    Map<String, Set<String>> mappings = container.getMappings();
    mappings.forEach((s, strings) -> object.put(s, new JSONArray(strings)));
    return object;
  }

  private static JSONObject getDependency(ArtifactContainer dependency, boolean transitive) {
    JSONObject object = new JSONObject();
    object.put("groupId", dependency.getGroupId());
    object.put("artifactId", dependency.getArtifactId());
    object.put("version", dependency.getVersion());
    object.put("transitive", transitive);
    return object;
  }

  private static JSONArray getClasses(ArtifactContainer container) {
    JSONArray array = new JSONArray();
    container.getDefinedClasses().forEach(array::put);
    return array;
  }
}
