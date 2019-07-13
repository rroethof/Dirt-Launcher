package net.dirtcraft.dirtlauncher.backend.game;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.therandomlabs.curseapi.CurseException;
import com.therandomlabs.curseapi.project.CurseProject;
import com.therandomlabs.utils.io.NetUtils;
import javafx.application.Platform;
import javafx.scene.text.Text;
import net.dirtcraft.dirtlauncher.Controllers.Install;
import net.dirtcraft.dirtlauncher.backend.config.Directories;
import net.dirtcraft.dirtlauncher.backend.jsonutils.JsonFetcher;
import net.dirtcraft.dirtlauncher.backend.objects.OptionalMod;
import net.dirtcraft.dirtlauncher.backend.objects.Pack;
import net.dirtcraft.dirtlauncher.backend.utils.FileUtils;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

public class DownloadManager {

    public static void completePackSetup(Pack pack, List<OptionalMod> optionalMods) throws IOException  {
        JsonObject versionManifest = JsonFetcher.getVersionManifestJson(pack.getGameVersion());

        boolean installMinecraft = true;
        boolean installAssets = true;
        boolean installForge = true;
        boolean installPack = true;

        for(JsonElement jsonElement : FileUtils.readJsonFromFile(Directories.getDirectoryManifest(Directories.getVersionsDirectory())).getAsJsonArray("versions")) {
            if(jsonElement.getAsJsonObject().get("version").getAsString().equals(pack.getGameVersion())) installMinecraft = false;
        }
        for(JsonElement jsonElement : FileUtils.readJsonFromFile(Directories.getDirectoryManifest(Directories.getAssetsDirectory())).getAsJsonArray("assets")) {
            if(jsonElement.getAsJsonObject().get("version").getAsString().equals(versionManifest.get("assets").getAsString())) installAssets = false;
        }
        for(JsonElement jsonElement : FileUtils.readJsonFromFile(Directories.getDirectoryManifest(Directories.getForgeDirectory())).getAsJsonArray("forgeVersions")) {
            if(jsonElement.getAsJsonObject().get("version").getAsString().equals(pack.getForgeVersion())) installForge = false;
        }
        for(JsonElement jsonElement : FileUtils.readJsonFromFile(Directories.getDirectoryManifest(Directories.getInstancesDirectory())).getAsJsonArray("packs")) {
            if(jsonElement.getAsJsonObject().get("name").getAsString().equals(pack.getName()) && jsonElement.getAsJsonObject().get("version").getAsString().equals(pack.getVersion())) installPack = false;
        }

        int totalSteps = optionalMods.size();
        int completedSteps = 0;
        if(installMinecraft) totalSteps += 2;
        if(installAssets) totalSteps++;
        if(installForge) totalSteps += 3;
        if(installPack) totalSteps += 2;
        setTotalProgressPercent(completedSteps, totalSteps);

        if(installMinecraft) {
            setProgressPercent(0, 1);
            installMinecraft(versionManifest, completedSteps, totalSteps);
            completedSteps += 2;
            setTotalProgressPercent(completedSteps, totalSteps);
        }
        if(installAssets) {
            setProgressPercent(0, 1);
            installAssets(versionManifest, completedSteps, totalSteps);
            completedSteps++;
            setTotalProgressPercent(completedSteps, totalSteps);
        }
        if(installForge) {
            setProgressPercent(0, 1);
            installForge(pack, completedSteps, totalSteps);
            completedSteps += 3;
            setTotalProgressPercent(completedSteps, totalSteps);
        }
        if(installPack) {
            setProgressPercent(0, 1);
            installPack(pack, completedSteps, totalSteps);
            completedSteps += 2;
            setTotalProgressPercent(completedSteps, totalSteps);
        }

        /*

         */

        Platform.runLater(() -> Install.getInstance().getButtonPane().setVisible(true));
        setTotalProgressPercent(1, 1);
        setProgressPercent(1, 1);
        setProgressText("Successfully Installed " + pack.getName() + "!");
    }

    public static void setProgressText(String text) {
        Platform.runLater(() -> ((Text) Install.getInstance().getNotificationText().getChildren().get(0)).setText(text + "..."));
    }

    public static void setProgressPercent(int completed, int total) {
        Platform.runLater(() -> Install.getInstance().getLoadingBar().setProgress(((double)completed) / total));
    }

    public static void setTotalProgressPercent(int completed, int total) {
        Platform.runLater(() -> Install.getInstance().getBottomBar().setProgress(((double)completed) / total));
    }

    public static void installMinecraft(JsonObject versionManifest, int completedSteps, int totalSteps) throws IOException {
        setProgressText("Installing Minecraft " + versionManifest.get("id").getAsString());
        File versionFolder = new File(Directories.getVersionsDirectory() + File.separator + versionManifest.get("id").getAsString());
        FileUtils.deleteDirectory(versionFolder);
        versionFolder.mkdirs();

        // Write version JSON manifest
        FileUtils.writeJsonToFile(new File(versionFolder.getPath() + File.separator + versionManifest.get("id").getAsString() + ".json"), versionManifest);
        setProgressPercent(1, 2);

        // Download jar
        FileUtils.copyURLToFile(versionManifest.getAsJsonObject("downloads").getAsJsonObject("client").get("url").getAsString(), new File(versionFolder.getPath() + File.separator + versionManifest.get("id").getAsString() + ".jar"));
        setTotalProgressPercent(completedSteps + 1, totalSteps);

        // Download Libraries
        setProgressText("Downloading Libraries");
        int completedLibraries = 0;
        int totalLibraries = versionManifest.getAsJsonArray("libraries").size();
        setProgressPercent(completedLibraries, totalLibraries);
        File librariesFolder = new File(versionFolder.getPath() + File.separator + "libraries");
        librariesFolder.mkdirs();
        File nativesFolder = new File(versionFolder.getPath() + File.separator + "natives");
        nativesFolder.mkdirs();
        String librariesLaunchCode = "";

        libraryLoop:
        for(JsonElement libraryElement : versionManifest.getAsJsonArray("libraries")) {
            JsonObject library = libraryElement.getAsJsonObject();
            // Check if the library has conditions
            if(library.has("rules")) {
                for(JsonElement rule : library.getAsJsonArray("rules")) {
                    switch(rule.getAsJsonObject().get("action").getAsString()) {
                        case "allow":
                            if(!rule.getAsJsonObject().has("os")) break;
                            switch(rule.getAsJsonObject().getAsJsonObject("os").get("name").getAsString()) {
                                case "windows":
                                    if(!SystemUtils.IS_OS_WINDOWS) {
                                        completedLibraries++;
                                        continue libraryLoop;
                                    }
                                case "osx":
                                    if(!SystemUtils.IS_OS_MAC_OSX) {
                                        completedLibraries++;
                                        continue libraryLoop;
                                    }
                                case "linux":
                                    if(!SystemUtils.IS_OS_LINUX) {
                                        completedLibraries++;
                                        continue libraryLoop;
                                    }
                            }
                            break;
                        case "disallow":
                            if(!rule.getAsJsonObject().has("os")) break;
                            switch(rule.getAsJsonObject().getAsJsonObject("os").get("name").getAsString()) {
                                case "windows":
                                    if(SystemUtils.IS_OS_WINDOWS) {
                                        completedLibraries++;
                                        continue libraryLoop;
                                    }
                                case "osx":
                                    if(SystemUtils.IS_OS_MAC_OSX) {
                                        completedLibraries++;
                                        continue libraryLoop;
                                    }
                                case "linux":
                                    if(SystemUtils.IS_OS_LINUX) {
                                        completedLibraries++;
                                        continue libraryLoop;
                                    }
                            }
                    }
                }
            }
            // The library is not conditional. Continue with the download.
            JsonObject libraryDownloads = library.getAsJsonObject("downloads");
            // Download any standard libraries
            if(libraryDownloads.has("artifact")) {
                new File(librariesFolder + File.separator + StringUtils.substringBeforeLast(libraryDownloads.getAsJsonObject("artifact").get("path").getAsString(), "/").replace("/", File.separator)).mkdirs();
                String filePath = librariesFolder.getPath() + File.separator + libraryDownloads.getAsJsonObject("artifact").get("path").getAsString().replace("/", File.separator);
                FileUtils.copyURLToFile(libraryDownloads.getAsJsonObject("artifact").get("url").getAsString(), new File(filePath));
                librariesLaunchCode += filePath;
                librariesLaunchCode += ";";
            }
            // Download any natives
            if(libraryDownloads.has("classifiers")) {
                String nativesType = "";
                if(SystemUtils.IS_OS_WINDOWS) nativesType = "natives-windows";
                if(SystemUtils.IS_OS_MAC_OSX) nativesType = "natives-osx";
                if(SystemUtils.IS_OS_LINUX) nativesType = "natives-linux";
                if(libraryDownloads.getAsJsonObject("classifiers").has(nativesType)) {
                    JsonObject nativeJson = libraryDownloads.getAsJsonObject("classifiers").getAsJsonObject(nativesType);
                    File outputFile = new File(nativesFolder + File.separator + nativeJson.get("sha1").getAsString());
                    FileUtils.copyURLToFile(nativeJson.get("url").getAsString(), outputFile);
                    FileUtils.extractJar(outputFile.getPath(), nativesFolder.getPath());
                    outputFile.delete();
                }
            }
            completedLibraries++;
            setProgressPercent(completedLibraries, totalLibraries);
        }

        // Populate Versions Manifest
        JsonObject versionJsonObject = new JsonObject();
        versionJsonObject.addProperty("version", versionManifest.get("id").getAsString());
        versionJsonObject.addProperty("classpathLibraries", StringUtils.substringBeforeLast(librariesLaunchCode, ";"));
        JsonObject versionsManifest = FileUtils.readJsonFromFile(Directories.getDirectoryManifest(Directories.getVersionsDirectory()));
        versionsManifest.getAsJsonArray("versions").add(versionJsonObject);
        FileUtils.writeJsonToFile(new File(Directories.getDirectoryManifest(Directories.getVersionsDirectory()).getPath()), versionsManifest);
    }

    public static void installAssets(JsonObject versionManifest, int completedSteps, int totalSteps) throws IOException {
        setProgressText("Downloading Assets");
        File assetsFolder = new File(Directories.getAssetsDirectory() + File.separator + versionManifest.get("assets").getAsString());
        FileUtils.deleteDirectory(assetsFolder);
        assetsFolder.mkdirs();

        // Write assets JSON manifest
        JsonObject assetsManifest = JsonFetcher.getJsonFromUrl(versionManifest.getAsJsonObject("assetIndex").get("url").getAsString());
        new File(assetsFolder.getPath() + File.separator + "indexes").mkdirs();
        FileUtils.writeJsonToFile(new File(assetsFolder.getPath() + File.separator + "indexes" + File.separator + versionManifest.get("assets").getAsString() + ".json"), assetsManifest);

        // Download assets
        int completedAssets = 0;
        int totalAssets = assetsManifest.getAsJsonObject("objects").keySet().size();
        setProgressPercent(completedAssets, totalAssets);
        for(String assetKey : assetsManifest.getAsJsonObject("objects").keySet()) {
            String hash = assetsManifest.getAsJsonObject("objects").getAsJsonObject(assetKey).get("hash").getAsString();
            File specificAssetFolder = new File(assetsFolder.getPath() + File.separator + "objects" + File.separator + hash.substring(0, 2));
            specificAssetFolder.mkdirs();
            FileUtils.copyURLToFile("http://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash, new File(specificAssetFolder.getPath() + File.separator + hash));
            completedAssets++;
            setProgressPercent(completedAssets, totalAssets);
        }

        // Populate Assets Manifest
        JsonObject assetsVersionJsonObject = new JsonObject();
        assetsVersionJsonObject.addProperty("version", versionManifest.get("assets").getAsString());
        JsonObject assetsFolderManifest = FileUtils.readJsonFromFile(Directories.getDirectoryManifest(Directories.getAssetsDirectory()));
        assetsFolderManifest.getAsJsonArray("assets").add(assetsVersionJsonObject);
        FileUtils.writeJsonToFile(new File(Directories.getDirectoryManifest(Directories.getAssetsDirectory()).getPath()), assetsFolderManifest);
    }

    public static void installForge(Pack pack, int completedSteps, int totalSteps) throws IOException {
        setProgressText("Downloading Forge Installer");
        File forgeFolder = new File(Directories.getForgeDirectory() + File.separator + pack.getForgeVersion());
        FileUtils.deleteDirectory(forgeFolder);
        forgeFolder.mkdirs();

        // Download Forge Installer
        File forgeInstaller = new File(forgeFolder.getPath() + File.separator + "installer.jar");
        // 1.7 did some strange stuff with forge file names
        if(pack.getGameVersion().equals("1.7.10")) FileUtils.copyURLToFile("https://files.minecraftforge.net/maven/net/minecraftforge/forge/" + pack.getGameVersion() + "-" + pack.getForgeVersion() + "-" + pack.getGameVersion() + "/forge-" + pack.getGameVersion() + "-" + pack.getForgeVersion() + "-" + pack.getGameVersion() + "-installer.jar", forgeInstaller);
        else FileUtils.copyURLToFile("https://files.minecraftforge.net/maven/net/minecraftforge/forge/" + pack.getGameVersion() + "-" + pack.getForgeVersion() + "/forge-" + pack.getGameVersion() + "-" + pack.getForgeVersion() + "-installer.jar", forgeInstaller);

        // Extract Forge Installer & Write forge JSON manifest
        setProgressText("Extracting Forge Installer");
        setTotalProgressPercent(completedSteps + 1, totalSteps);
        JsonObject forgeVersionManifest = FileUtils.extractForgeJar(forgeInstaller, forgeFolder.getPath());
        forgeInstaller.delete();
        setProgressPercent(1, 2);
        FileUtils.writeJsonToFile(new File(forgeFolder.getPath() + File.separator + pack.getForgeVersion() + ".json"), forgeVersionManifest);

        // Download forge libraries
        setProgressText("Downloading Forge Libraries");
        setTotalProgressPercent(completedSteps + 2, totalSteps);
        setProgressPercent(0, 1);
        int completedLibraries = 0;
        int totalLibraries = forgeVersionManifest.getAsJsonObject("versionInfo").getAsJsonArray("libraries").size() - 1;
        String librariesLaunchCode = "";

        libraryLoop:
        for(JsonElement libraryElement : forgeVersionManifest.getAsJsonObject("versionInfo").getAsJsonArray("libraries")) {
            JsonObject library = libraryElement.getAsJsonObject();
            String[] libraryMaven = library.get("name").getAsString().split(":");
            // We already got forge
            if(libraryMaven[1].equals("forge")) {
                completedLibraries++;
                setProgressPercent(completedLibraries, totalLibraries);
                continue;
            }
            File libraryPath = new File(forgeFolder + File.separator + "libraries" + File.separator + libraryMaven[0].replace(".", File.separator) + File.separator + libraryMaven[1] + File.separator + libraryMaven[2]);
            libraryPath.mkdirs();
            String url = "https://libraries.minecraft.net/";
            if(library.has("url")) {
                url = library.get("url").getAsString();
            }
            url += libraryMaven[0].replace(".", "/") + "/" + libraryMaven[1] + "/" + libraryMaven[2] + "/" + libraryMaven[1] + "-" + libraryMaven[2] + ".jar";

            String fileName = libraryPath + File.separator + libraryMaven[1] + "-" + libraryMaven[2] + ".jar";
            // Typesafe does some weird crap
            if(libraryMaven[0].contains("typesafe")) {
                url += ".pack.xz";
                fileName += ".pack.xz";
            }

            File libraryFile = new File(fileName);
            FileUtils.copyURLToFile(url, libraryFile);
            if(libraryFile.getName().contains(".pack.xz")) {
                FileUtils.unpackPackXZ(libraryFile);
            }
            librariesLaunchCode += StringUtils.substringBeforeLast(libraryFile.getPath(), ".pack.xz") + ";";
            completedLibraries++;
            setProgressPercent(completedLibraries, totalLibraries);
        }

        JsonObject forgeManifest = FileUtils.readJsonFromFile(Directories.getDirectoryManifest(Directories.getForgeDirectory()));
        JsonObject versionJsonObject = new JsonObject();
        versionJsonObject.addProperty("version", pack.getForgeVersion());
        versionJsonObject.addProperty("classpathLibraries", StringUtils.substringBeforeLast(forgeFolder + File.separator + "forge-" + pack.getGameVersion() + "-" + pack.getForgeVersion() + "-universal.jar;" + librariesLaunchCode, ";"));
        forgeManifest.getAsJsonArray("forgeVersions").add(versionJsonObject);
        FileUtils.writeJsonToFile(new File(Directories.getDirectoryManifest(Directories.getForgeDirectory()).getPath()), forgeManifest);
    }

    public static void installPack(Pack pack, int completedSteps, int totalSteps) throws IOException {
        switch(pack.getPackType()) {
            case CURSE:
                try {
                    setProgressText("Downloading ModPack Manifest");
                    File modpackFolder = new File(Directories.getInstancesDirectory() + File.separator + pack.getName().replace(" ", "-"));
                    FileUtils.deleteDirectory(modpackFolder);
                    modpackFolder.mkdirs();

                    // Download Modpack
                    File modpackZip = new File(modpackFolder.getPath() + File.separator + "modpack.zip");
                    FileUtils.copyURLToFile(NetUtils.getRedirectedURL(new URL(pack.getLink())).toString().replace("%2B", "+"), modpackZip);
                    setProgressPercent(1, 2);
                    File tempDir = new File(modpackFolder.getPath() + File.separator + "temp");
                    tempDir.mkdirs();
                    new ZipFile(modpackZip).extractAll(tempDir.getPath());
                    modpackZip.delete();
                    FileUtils.copyDirectory(new File(tempDir.getPath() + File.separator + "overrides"), modpackFolder);
                    JsonObject modpackManifest = FileUtils.readJsonFromFile(new File(tempDir.getPath() + File.separator + "manifest.json"));
                    FileUtils.writeJsonToFile(new File(modpackFolder.getPath() + File.separator + "manifest.json"), modpackManifest);
                    FileUtils.deleteDirectory(tempDir);
                    setProgressPercent(0, 0);
                    setTotalProgressPercent(completedSteps + 1, totalSteps);

                    // Download Mods
                    setProgressText("Downloading Mods");
                    int completedMods = 0;
                    int totalMods = modpackManifest.getAsJsonArray("files").size();
                    File modsFolder = new File(modpackFolder.getPath() + File.separator + "mods");

                    for(JsonElement modElement : modpackManifest.getAsJsonArray("files")) {
                        //JsonObject mod = modElement.getAsJsonObject();
                        //CurseProject project = CurseProject.fromID(mod.get("projectID").getAsString());
                        //FileUtils.copyURLToFile(project.fileWithID(mod.get("fileID").getAsInt()).downloadURLString(), new File(modsFolder.getPath() + File.separator + project.fileWithID(mod.get("fileID").getAsInt()).downloadInfo().getFileName()));
                        //System.out.print(project.fileWithID(mod.get("fileID").getAsInt()).downloadURLString());
                        //completedMods++;
                        //setProgressPercent(completedMods, totalMods);

                        JsonObject mod = modElement.getAsJsonObject();
                        JsonObject apiResponse = JsonFetcher.getJsonFromUrl("https://addons-ecs.forgesvc.net/api/v2/addon/" + mod.get("projectID").getAsString() + "/file/" + mod.get("fileID").getAsString());
                        FileUtils.copyURLToFile(apiResponse.get("downloadUrl").getAsString(), new File(modsFolder.getPath() + File.separator + apiResponse.get("fileName").getAsString()));
                        completedMods++;
                        setProgressPercent(completedMods, totalMods);
                    }

                    JsonObject instanceManifest = FileUtils.readJsonFromFile(Directories.getDirectoryManifest(Directories.getInstancesDirectory()));
                    JsonObject packJson = new JsonObject();
                    packJson.addProperty("name", pack.getName());
                    packJson.addProperty("version", pack.getVersion());
                    packJson.addProperty("gameVersion", pack.getGameVersion());
                    packJson.addProperty("forgeVersion", pack.getForgeVersion());
                    instanceManifest.getAsJsonArray("packs").add(packJson);
                    FileUtils.writeJsonToFile(new File(Directories.getDirectoryManifest(Directories.getInstancesDirectory()).getPath()), instanceManifest);
                } catch (Exception e) {
                    e.printStackTrace();
                }
        }
    }
}