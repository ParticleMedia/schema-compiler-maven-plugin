package com.pmi.SchemaCompiler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.javatuples.Pair;

/** Goal which generates ad config files. */
@Mojo(name = "generate-config", defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class ConfigGeneratorMojo extends AbstractMojo {
  private static String pattern_fb = "(153277661752118|4921738341205298)_\\d{15,16}";
  private static String pattern_admob1 = "/21839579524/am-(ios|ipad|android|androidLite)-.+";
  private static String pattern_admob2 = "ca-app-pub-4001304092171320/\\d{10}";
  // gam 360
  private static String pattern_admob3 = "/22129205380/gam360-(ios|ipad|android|androidLite)-.+";
  private static String pattern_dfp1 = "/21839579524/am-(ios|ipad|android|androidLite)-.+";
  private static String pattern_dfp2 = "/22129205380/gam360-(ios|ipad|android|androidLite)-.+";
  private static String pattern_smaato = "\\d{9}";
  private static String pattern_aps = "/21839579524/aps/amazon-(ios|ipad|android|androidLite)-.+";
  private static String pattern_apploving = "[a-zA-Z0-9]+";
  private static String pattern_unity = "[-a-zA-Z0-9]+";

  private class AdUnitComparator implements Comparator<Map<String, Object>> {
    private boolean isGreaterThanAndroidVersion810 = false;

    AdUnitComparator(boolean isGreaterThanAndroidVersion810) {
      this.isGreaterThanAndroidVersion810 = isGreaterThanAndroidVersion810;
    }

    public int compare(Map<String, Object> o1, Map<String, Object> o2) {
      if (!o1.containsKey("price") && !o2.containsKey("price")) {
        return 0;
      } else if (!o1.containsKey("price")) {
        return -1;
      } else if (!o2.containsKey("price")) {
        return 1;
      } else {
        // Before android version 8.1.0, we put aps at the bottom,
        // after android version 8.1.0, we put aps at the top.
        if ("ad_aps_native".equals(o1.get("ctype"))) {
          if (isGreaterThanAndroidVersion810) {
            return -1;
          } else {
            return 1;
          }
        }
        if ("ad_aps_native".equals(o2.get("ctype"))) {
          if (isGreaterThanAndroidVersion810) {
            return 1;
          } else {
            return -1;
          }
        }

        double price1 = Double.valueOf(o1.get("price").toString());
        double price2 = Double.valueOf(o2.get("price").toString());
        if ("ad_fb_native".equals(o1.get("ctype")) && price1 == 0) {
          return -1;
        }

        if ("ad_fb_native".equals(o2.get("ctype")) && price2 == 0) {
          return 1;
        }
        return Double.compare(price2, price1);
      }
    }
  }

  @Parameter(defaultValue = "${project}")
  private MavenProject project;

  // The directory which contains all ad units separated by os.
  @Parameter(property = "adUnitsDir", required = true)
  private String adUnitsDir;

  // The directory which contains all ads separated by OS.
  @Parameter(property = "adsDir", required = true)
  private String adsDir;

  // The directory which contains all the output JSON.
  @Parameter(property = "dataDir", required = true)
  private String dataDir;

  private ObjectMapper objectMapper =
      new ObjectMapper().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

  private Map<String, Map<String, Object>> adUnits;

  public void execute() throws MojoExecutionException {
    try {
      this.adUnits = readAdUnits();
      Path adsDirPath = Paths.get(this.adsDir);
      List<Path> allOS = Files.list(adsDirPath).collect(Collectors.toList());
      for (Path osPath : allOS) {
        genAds(osPath.getFileName().toString());
      }
    } catch (Exception e) {
      getLog().error(e);
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private Map<String, Map<String, Object>> genAds(String os) throws IOException {
    Path defaultDir = Paths.get(this.adsDir, os, "default");
    Map<String, Map<String, Object>> adDefault = readAdDefault(defaultDir);

    Path adsByOsDir = Paths.get(this.adsDir, os);
    Files.list(adsByOsDir)
        .filter(path -> !"default".equals(path.getFileName().toString()) && Files.isDirectory(path))
        .forEach(segmentPath -> processSegment(segmentPath, adDefault));

    return null;
  }

  // Return the ad under the segment path, e.g.:
  // "ios_huge_3.6.6_all.json" -> ad map
  // "android_related_7.3.0_all.json" -> ad map
  // and so on
  private void processSegment(Path segment, Map<String, Map<String, Object>> adDefault) {
    try {
      Files.walk(segment)
          .filter(path -> isJsonFile(path))
          .collect(
              Collectors.toMap(
                  path -> pathToKey(path), path -> readExpandAndOverride(path, adDefault)))
          .entrySet()
          .stream()
          .forEach(entry -> writeJson(segment, entry.getKey(), entry.getValue()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // Return the default ad in the dir path, e.g.:
  // "related" -> ad map
  // "interstitial" -> ad map
  // and so on
  private Map<String, Map<String, Object>> readAdDefault(Path defaultDir) throws IOException {
    return Files.walk(defaultDir)
        .filter(path -> isJsonFile(path))
        .collect(Collectors.toMap(path -> pathToKey(path), path -> readAndExpand(path)));
  }

  // Read the JSON file specified by the Path and expand the JSON file it contains ads key word.
  // Return a map representing the JSON object.
  private Map<String, Object> readAndExpand(Path path) {
    Map<String, Object> ad = readJson(path);

    if (ad.containsKey("ads")) {
      List<String> adUnitNames = (List<String>) ad.get("ads");
      List<Map<String, Object>> adUnits =
          adUnitNames.stream()
              .map(adUnitName -> getAdUnit(adUnitName))
              .collect(Collectors.toList());
      ad.put("ads", adUnits);
    }

    return ad;
  }

  // Read the JSON file specified by the Path and expand the JSON file it contains ads key word.
  // Return a map representing the JSON object.
  private Map<String, Object> readExpandAndOverride(
      Path path, Map<String, Map<String, Object>> adDefault) {
    Map<String, Object> ad = readAndExpand(path);
    String adSlot = getAdSlot(path);
    ad = override(adDefault.get(adSlot), ad);

    ArrayList<Map<String, Object>> ads = (ArrayList<Map<String, Object>>) ad.get("ads");

    // smaato ad units does not participate in the sorting, they should stay where they are after
    // sorting. So, before sorting, we extract them out from the ad units, and put them back to
    // sorted ad units by using their index.
    List<Pair<Integer, Map<String, Object>>> smaato =
        new ArrayList<Pair<Integer, Map<String, Object>>>();
    for (int i = 0; i < ads.size(); i++) {
      Map<String, Object> adUnit = ads.get(i);
      if ("ad_smaato_native".equals(adUnit.get("ctype"))) {
        smaato.add(new Pair<Integer, Map<String, Object>>(i, adUnit));
      }
    }

    for (int i = smaato.size() - 1; i >= 0; i--) {
      ads.remove((int) smaato.get(i).getValue0());
    }

    AdUnitComparator comparator = new AdUnitComparator(isGreaterThanAndroidVersion810(path));
    ads.sort(comparator);

    for (Pair<Integer, Map<String, Object>> pair : smaato) {
      ads.add(pair.getValue0(), pair.getValue1());
    }

    ad.put("ads", ads);
    return ad;
  }

  // Returns a new map representing an JSON object by replacing the fields in the parent with the
  // fields in the sub.
  private Map<String, Object> override(Map<String, Object> parent, Map<String, Object> sub) {
    Map<String, Object> cloned = (Map<String, Object>) clonePossibleMap(parent);
    for (String key : sub.keySet()) {
      cloned.put(key, sub.get(key));
    }
    return cloned;
  }

  // Read all ad units files.
  private Map<String, Map<String, Object>> readAdUnits() throws Exception {
    Path adUnitPath = Paths.get(this.adUnitsDir);
    Map<String, Map<String, Object>> adUnits =
        Files.walk(adUnitPath)
            .filter(path -> Files.isRegularFile(path))
            .collect(Collectors.toMap(path -> pathToKey(path), path -> readJson(path)));

    checkDuplicatedPlacementId(adUnits.values());
    checkPlacementIdValue(adUnits.values());
    return adUnits;
  }

  // Check if one placement id is being used in multiple ad units.
  private void checkDuplicatedPlacementId(Collection<Map<String, Object>> adUnits)
      throws Exception {
    Set<String> placementIds = new HashSet<String>();

    for (Map<String, Object> adUnit : adUnits) {
      String placementId = (String) adUnit.get("placement_id");
      if (placementId == null || "".equals(placementId)) {
        throw new Exception("Placement Id cannot be null or empty.");
      }

      if (placementIds.contains(placementId)) {
        if (!placementId.equals("153277661752118_1194686894277851")) {
          throw new Exception("Found duplicated placement Id: " + placementId);
        }
      }

      placementIds.add(placementId);
    }
  }

  // Check if the placement id matches regular expression pattern of each ctype.
  private void checkPlacementIdValue(Collection<Map<String, Object>> adUnits) throws Exception {
    for (Map<String, Object> adUnit : adUnits) {
      String placementId = (String) adUnit.get("placement_id");
      String ctype = (String) adUnit.get("ctype");

      boolean result = false;
      switch (ctype) {
        case "ad_aps_native":
          result = placementId.matches(pattern_aps);
          break;
        case "ad_admob_native":
          result =
              placementId.matches(pattern_admob1)
                  || placementId.matches(pattern_admob2)
                  || placementId.matches(pattern_admob3);
          break;
        case "ad_dfp_native":
        case "ad_dfp_video":
          result =
              placementId.matches(pattern_dfp1)
                  || placementId.matches(pattern_dfp2)
                  || placementId.matches(pattern_admob2);
          break;
        case "ad_fb_native":
          result = placementId.matches(pattern_fb);
          break;
        case "ad_smaato_native":
          result = placementId.matches(pattern_smaato);
          break;
        case "ad_prebid_native":
        case "ad_nb_native":
        case "ad_nova_native":
        case "ad_msp_native":
          result = true;
          break;
        case "ad_applovin_native":
          result = placementId.matches(pattern_apploving);
          break;
        case "ad_unity_native":
          result = placementId.matches(pattern_unity);
          break;
        default:
          throw new Exception(MessageFormat.format("Unknown ctype: {0}", ctype));
      }

      if (!result) {
        throw new Exception(
            MessageFormat.format(
                "Placement Id {0} does not match pattern for ctype {1}. Please check if there is"
                    + " typo",
                placementId, ctype));
      }
    }
  }

  private String pathToKey(Path path) {
    String[] sections = path.toFile().getName().split(".json");
    return sections[0];
  }

  private String getAdSlot(Path path) {
    String fileName = path.getFileName().toString();
    String[] sections = fileName.split("_");
    return sections[1];
  }

  private String getSegment(Path segmentPath) {
    String segment = segmentPath.getFileName().toString();
    if ("main".equals(segment)) {
      return "";
    } else {
      return segment;
    }
  }

  private Map<String, Object> readJson(Path path) throws RuntimeException {
    try {
      return this.objectMapper.readValue(
          path.toUri().toURL(), new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void writeJson(Path segmentPath, String fileName, Map<String, Object> ad) {
    String segment = getSegment(segmentPath);
    writeJson(segment, fileName, ad);
  }

  private void writeJson(String segment, String fileName, Map<String, Object> ad) {
    try {
      String segmentDir = createDir(segment);
      Path filePath = null;
      if (fileName.endsWith(".json")) {
        filePath = Paths.get(segmentDir, fileName);
      } else {
        filePath = Paths.get(segmentDir, fileName + ".json");
      }

      Files.write(
          filePath,
          this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(ad).getBytes());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String createDir(String segment) throws IOException {
    String projectPath = this.project.getBasedir().getPath();
    Path segmentDir = Paths.get(projectPath, "config", segment);
    if (!Files.exists(segmentDir)) {
      Files.createDirectory(segmentDir);
    }

    return segmentDir.toString();
  }

  private boolean isJsonFile(Path path) {
    return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json");
  }

  private Map<String, Object> getAdUnit(String name) throws RuntimeException {
    Map<String, Object> adUnit = this.adUnits.get(name);
    if (adUnit == null) {
      throw new RuntimeException("Could not find ad unit: " + name);
    }

    return (Map<String, Object>) clonePossibleMap(adUnit);
  }

  private Object clonePossibleMap(Object obj) {
    if (obj instanceof Map) {
      Map<String, Object> toBeCloned = (Map<String, Object>) obj;
      Map<String, Object> newMap = new HashMap<String, Object>();
      for (String key : toBeCloned.keySet()) {
        newMap.put(key, clonePossibleMap(toBeCloned.get(key)));
      }

      return newMap;
    } else {
      return obj;
    }
  }

  // Check if this file is android config and version is greater than or equal to 8.1.0
  private boolean isGreaterThanAndroidVersion810(Path path) {
    String fileName = path.getFileName().toString();
    String[] sections = fileName.split("_");
    if ("android".equals(sections[0])) {
      String version = sections[2];
      String[] digits = version.split("\\.");
      int mainVersion = Integer.parseInt(digits[0]);
      int minorVersion = Integer.parseInt(digits[1]);
      return mainVersion + minorVersion / 10.0 >= 8.1;
    }

    return false;
  }

  // For debugging purpose only.
  private void print(Object obj) throws JsonProcessingException {
    Map<String, Object> map = (Map<String, Object>) obj;
    for (String key : map.keySet()) {
      getLog().info(key);
      getLog().info(objectMapper.writeValueAsString(map.get(key)));
    }
  }
}
