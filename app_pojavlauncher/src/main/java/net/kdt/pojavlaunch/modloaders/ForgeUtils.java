package net.kdt.pojavlaunch.modloaders;

import android.content.Intent;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

public class ForgeUtils {
    private static final String FORGE_METADATA_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    private static final String FORGE_INSTALLER_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge/%1$s/forge-%1$s-installer.jar";
    public static List<String> downloadForgeVersions() throws IOException {
        SAXParser saxParser;
        try {
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            saxParser = parserFactory.newSAXParser();
        }catch (SAXException | ParserConfigurationException e) {
            e.printStackTrace();
            // if we cant make a parser we might as well not even try to parse anything
            return null;
        }
        try {
            //of_test();
            return DownloadUtils.downloadStringCached(FORGE_METADATA_URL, "forge_versions", input -> {
                try {
                    ForgeVersionListHandler handler = new ForgeVersionListHandler();
                    saxParser.parse(new InputSource(new StringReader(input)), handler);
                    return handler.getVersions();
                    // IOException is present here StringReader throws it only if the parser called close()
                    // sooner than needed, which is a parser issue and not an I/O one
                }catch (SAXException | IOException e) {
                    throw new DownloadUtils.ParseException(e);
                }
            });
        }catch (DownloadUtils.ParseException e) {
            e.printStackTrace();
            return null;
        }

    }
    public static String getInstallerUrl(String version) {
        return String.format(FORGE_INSTALLER_URL, version);
    }

    public static void addAutoInstallArgs(Intent intent, File modInstallerJar, boolean createProfile) {
        intent.putExtra("javaArgs", "-javaagent:"+ Tools.DIR_DATA+"/forge_installer/forge_installer.jar"
                + (createProfile ? "=NPS" : "") + // No Profile Suppression
                " -jar "+modInstallerJar.getAbsolutePath());
    }
    public static void addAutoInstallArgs(Intent intent, File modInstallerJar, String modpackFixupId) {
        intent.putExtra("javaArgs", "-javaagent:"+ Tools.DIR_DATA+"/forge_installer/forge_installer.jar"
                + "=\"" + modpackFixupId +"\"" +
                " -jar "+modInstallerJar.getAbsolutePath());
    }

    /**
     * Extracts "<mcVersion>-<forgeVersion>" (e.g. "1.20.1-47.4.10") from a Forge versionId or its JSON.
     */
    public static String extractFullForgeVersion(String versionId) {
        if (versionId == null || versionId.isEmpty()) return null;
        File versionJson = new File(Tools.DIR_HOME_VERSION, versionId + "/" + versionId + ".json");
        if (versionJson.isFile()) {
            try {
                String raw = Tools.read(versionJson.getAbsolutePath());
                com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
                String mcVer = null, forgeVer = null;
                if (root.has("arguments") && root.get("arguments").isJsonObject()) {
                    com.google.gson.JsonObject args = root.getAsJsonObject("arguments");
                    if (args.has("game") && args.get("game").isJsonArray()) {
                        com.google.gson.JsonArray gameArgs = args.getAsJsonArray("game");
                        for (int i = 0; i < gameArgs.size() - 1; i++) {
                            if (!gameArgs.get(i).isJsonPrimitive() || !gameArgs.get(i + 1).isJsonPrimitive()) continue;
                            String k = gameArgs.get(i).getAsString();
                            String v = gameArgs.get(i + 1).getAsString();
                            if ("--fml.mcVersion".equals(k)) mcVer = v;
                            else if ("--fml.forgeVersion".equals(k)) forgeVer = v;
                        }
                    }
                }
                if (mcVer != null && forgeVer != null) {
                    return mcVer + "-" + forgeVer;
                }
            } catch (Exception ignored) {}
        }
        int idx = versionId.indexOf("-forge-");
        if (idx > 0) {
            String mc = versionId.substring(0, idx);
            String forge = versionId.substring(idx + "-forge-".length());
            if (!mc.isEmpty() && !forge.isEmpty()) {
                return mc + "-" + forge;
            }
        }
        return null;
    }

    /**
     * Verifies that a Forge or NeoForge version JSON and all of its processor-generated / maven-bundled
     * JARs exist and have valid ZIP END headers. Deletes any corrupt JARs found on disk.
     */
    public static boolean verifyForgeInstallation(String versionId, boolean deleteJsonIfIncomplete) {
        if (versionId == null || versionId.isEmpty()) return false;
        File versionJson = new File(Tools.DIR_HOME_VERSION, versionId + "/" + versionId + ".json");
        if (!versionJson.isFile() || versionJson.length() == 0) return false;
        try {
            String raw = Tools.read(versionJson.getAbsolutePath());
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();

            if (root.has("libraries") && root.get("libraries").isJsonArray()) {
                for (com.google.gson.JsonElement el : root.getAsJsonArray("libraries")) {
                    if (!el.isJsonObject()) continue;
                    com.google.gson.JsonObject lib = el.getAsJsonObject();
                    String path = null;
                    boolean hasUrl = lib.has("url") && !lib.get("url").getAsString().isEmpty();
                    if (lib.has("downloads") && lib.get("downloads").isJsonObject()) {
                        com.google.gson.JsonObject dl = lib.getAsJsonObject("downloads");
                        if (dl.has("artifact") && dl.get("artifact").isJsonObject()) {
                            com.google.gson.JsonObject art = dl.getAsJsonObject("artifact");
                            if (art.has("path")) path = art.get("path").getAsString();
                            if (art.has("url") && !art.get("url").getAsString().isEmpty()) hasUrl = true;
                        }
                    }
                    if ((path == null || path.isEmpty()) && lib.has("name")) {
                        path = ForgeDownloadTask.mavenCoordinateToPath(lib.get("name").getAsString());
                    }
                    if (path != null && !path.isEmpty()) {
                        File libFile = new File(Tools.DIR_HOME_LIBRARY, path);
                        if (libFile.exists() && DownloadUtils.isZipOrJarExtension(libFile) && !DownloadUtils.isValidZipOrJar(libFile)) {
                            android.util.Log.w("ForgeUtils", "Deleting corrupt library JAR: " + libFile.getAbsolutePath());
                            libFile.delete();
                        }
                        if (!hasUrl && !DownloadUtils.isValidZipOrJar(libFile)) {
                            android.util.Log.w("ForgeUtils", "Missing installer-bundled library JAR: " + libFile.getAbsolutePath());
                            if (deleteJsonIfIncomplete) versionJson.delete();
                            return false;
                        }
                    }
                }
            }

            String mcVer = null, forgeVer = null, mcpVer = null, neoFormVer = null, neoForgeVer = null;
            if (root.has("arguments") && root.get("arguments").isJsonObject()) {
                com.google.gson.JsonObject args = root.getAsJsonObject("arguments");
                if (args.has("game") && args.get("game").isJsonArray()) {
                    com.google.gson.JsonArray gameArgs = args.getAsJsonArray("game");
                    for (int i = 0; i < gameArgs.size() - 1; i++) {
                        if (!gameArgs.get(i).isJsonPrimitive() || !gameArgs.get(i + 1).isJsonPrimitive()) continue;
                        String k = gameArgs.get(i).getAsString();
                        String v = gameArgs.get(i + 1).getAsString();
                        if ("--fml.mcVersion".equals(k)) mcVer = v;
                        else if ("--fml.forgeVersion".equals(k)) forgeVer = v;
                        else if ("--fml.mcpVersion".equals(k)) mcpVer = v;
                        else if ("--fml.neoFormVersion".equals(k)) neoFormVer = v;
                        else if ("--fml.neoforgeVersion".equals(k) || "--fml.fmlVersion".equals(k)) {
                            if (neoForgeVer == null) neoForgeVer = v;
                        }
                    }
                }
            }

            if (mcVer != null && forgeVer != null && mcpVer != null) {
                File srgJar = new File(Tools.DIR_HOME_LIBRARY,
                        "net/minecraft/client/" + mcVer + "-" + mcpVer + "/client-" + mcVer + "-" + mcpVer + "-srg.jar");
                File extraJar = new File(Tools.DIR_HOME_LIBRARY,
                        "net/minecraft/client/" + mcVer + "-" + mcpVer + "/client-" + mcVer + "-" + mcpVer + "-extra.jar");
                File slimJar = new File(Tools.DIR_HOME_LIBRARY,
                        "net/minecraft/client/" + mcVer + "-" + mcpVer + "/client-" + mcVer + "-" + mcpVer + "-slim.jar");
                File forgeClientJar = new File(Tools.DIR_HOME_LIBRARY,
                        "net/minecraftforge/forge/" + mcVer + "-" + forgeVer + "/forge-" + mcVer + "-" + forgeVer + "-client.jar");
                File forgeUniversalJar = new File(Tools.DIR_HOME_LIBRARY,
                        "net/minecraftforge/forge/" + mcVer + "-" + forgeVer + "/forge-" + mcVer + "-" + forgeVer + "-universal.jar");

                if (slimJar.exists() && !DownloadUtils.isValidZipOrJar(slimJar)) {
                    slimJar.delete();
                }

                boolean allComplete = true;
                File[] required = new File[]{srgJar, extraJar, forgeClientJar, forgeUniversalJar};
                for (File req : required) {
                    if (!DownloadUtils.isValidZipOrJar(req)) {
                        android.util.Log.w("ForgeUtils", "Missing or corrupt Forge processor artifact: " + req.getAbsolutePath());
                        if (req.exists()) req.delete();
                        allComplete = false;
                    }
                }
                String[] fmlModules = new String[]{"fmlcore", "javafmllanguage", "lowcodelanguage", "mclanguage"};
                for (String mod : fmlModules) {
                    File modJar = new File(Tools.DIR_HOME_LIBRARY,
                            "net/minecraftforge/" + mod + "/" + mcVer + "-" + forgeVer + "/" + mod + "-" + mcVer + "-" + forgeVer + ".jar");
                    if (modJar.exists() && !DownloadUtils.isValidZipOrJar(modJar)) {
                        android.util.Log.w("ForgeUtils", "Deleting corrupt FML module JAR: " + modJar.getAbsolutePath());
                        modJar.delete();
                        allComplete = false;
                    }
                }
                if (!allComplete) {
                    if (deleteJsonIfIncomplete) versionJson.delete();
                    return false;
                }
            } else if (mcVer != null && neoFormVer != null) {
                File srgJar = new File(Tools.DIR_HOME_LIBRARY,
                        "net/minecraft/client/" + mcVer + "-" + neoFormVer + "/client-" + mcVer + "-" + neoFormVer + "-srg.jar");
                File extraJar = new File(Tools.DIR_HOME_LIBRARY,
                        "net/minecraft/client/" + mcVer + "-" + neoFormVer + "/client-" + mcVer + "-" + neoFormVer + "-extra.jar");
                File slimJar = new File(Tools.DIR_HOME_LIBRARY,
                        "net/minecraft/client/" + mcVer + "-" + neoFormVer + "/client-" + mcVer + "-" + neoFormVer + "-slim.jar");
                if (slimJar.exists() && !DownloadUtils.isValidZipOrJar(slimJar)) slimJar.delete();
                if (!DownloadUtils.isValidZipOrJar(srgJar) || !DownloadUtils.isValidZipOrJar(extraJar)) {
                    if (srgJar.exists() && !DownloadUtils.isValidZipOrJar(srgJar)) srgJar.delete();
                    if (extraJar.exists() && !DownloadUtils.isValidZipOrJar(extraJar)) extraJar.delete();
                    if (deleteJsonIfIncomplete) versionJson.delete();
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            android.util.Log.w("ForgeUtils", "Failed verifying Forge installation " + versionId, e);
            return false;
        }
    }
}
