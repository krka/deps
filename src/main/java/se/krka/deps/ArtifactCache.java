package se.krka.deps;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static java.lang.System.getProperty;

class ArtifactCache {
  private final File dir;

  public ArtifactCache(File dir) {
    this.dir = dir;
    if (!dir.exists()) {
      if (!dir.mkdirs()) {
        throw new RuntimeException("Could not create directory: " + dir);
      }
    }
    if (!dir.isDirectory()) {
      throw new RuntimeException("Expected a directory: " + dir);
    }
  }

  public static ArtifactCache getDefault() {
    String homeDir = getProperty("user.home");
    File root = new File(new File(new File(homeDir, ".m2"), "repository"), "dependency-data");
    return new ArtifactCache(root);
  }

  public ArtifactContainer resolve(
          String groupId, String artifactId, String version,
          Set<ArtifactContainer> dependencies,
          Callable<ArtifactContainer> provider) {
    String coordinate = groupId + "_" + artifactId + "_" + version;
    File dir = new File(this.dir, coordinate);
    try {
      if (dir.exists()) {
        JSONObject data = getObject(dir, "data.json");
        HashSet<ArtifactContainer> flattenedDependencies = new HashSet<>(dependencies);
        for (ArtifactContainer dependency : dependencies) {
          flattenedDependencies.addAll(dependency.getFlattenedDependencies());
        }
        return JsonReader.fromJson(data, dependencies, flattenedDependencies);
      } else {
        ArtifactContainer artifactContainer = provider.call();
        if (!dir.mkdirs()) {
          throw new IOException("Could not create directory: " + dir);
        }
        JSONObject jsonObject = JsonWriter.toJsonObject(artifactContainer);
        writeObject(dir, "data.json", jsonObject);
        return artifactContainer;
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void writeObject(File dir, String filename, JSONObject object) throws IOException {
    File file = new File(dir, filename + ".gz");
    try (Writer writer = new OutputStreamWriter(new GZIPOutputStream(new FileOutputStream(file)), StandardCharsets.UTF_8)) {
      object.write(writer, 2, 0);
    }
  }

  private JSONObject getObject(File dir, String filename) throws IOException {
    File file = new File(dir, filename + ".gz");
    try (Reader reader = new InputStreamReader(new GZIPInputStream(new FileInputStream(file)), StandardCharsets.UTF_8)) {
      return new JSONObject(new JSONTokener(reader));
    }
  }
}
