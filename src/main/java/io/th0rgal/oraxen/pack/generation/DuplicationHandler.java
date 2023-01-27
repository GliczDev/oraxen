package io.th0rgal.oraxen.pack.generation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.lumine.mythic.utils.logging.Log;
import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.config.Settings;
import io.th0rgal.oraxen.utils.Utils;
import io.th0rgal.oraxen.utils.logs.Logs;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DuplicationHandler {

    public DuplicationHandler() {
    }

    /**
     * Check if the file already exists in the zip file
     */
    public static void checkForDuplicate(ZipOutputStream out, ZipEntry entry) {
        String name = entry.getName();
        try {
            out.putNextEntry(entry);
        } catch (IOException e) {
            Logs.logWarning("Duplicate file detected: <blue>" +  name + "</blue> - Attempting to migrate it");
            if (!Settings.ATTEMPT_TO_MIGRATE_DUPLICATES.toBool()) {
                Logs.logError("Not attempting to migrate duplicate file as <#22b14c>attempt_to_migrate_duplicates</#22b14c> is disabled in settings.yml");
            }
            else if (attemptToMigrateDuplicate(name)) {
                Logs.logSuccess("Duplicate file fixed:<blue> " + name);
                try {
                    OraxenPlugin.get().getDataFolder().toPath().resolve("pack/" + name).toFile().delete();
                    Logs.logSuccess("Deleted the imported <blue>" + Utils.removeParentDirs(name) + "</blue> and migrated it to its supported Oraxen config(s)");
                } catch (Exception ignored) {
                    Log.error("Failed to delete the imported <blue>" + Utils.removeParentDirs(name) + "</blue> after migrating it");
                }
                Logs.logSuccess("Might need to restart your server ones before the resourcepack works fully");
            }
            Logs.newline();
        }
    }

    private static boolean attemptToMigrateDuplicate(String name) {
        if (name.startsWith("assets/minecraft/models/item/")) {
            Logs.logWarning("Found a duplicate <blue>" + Utils.removeParentDirs(name) + "</blue>, attempting to migrate it into Oraxen item configs");
            return migrateItemJson(name);
        } else if (name.matches("assets/minecraft/font/default.json")) {
            Logs.logWarning("Found a default.json duplicate, trying to migrate it into Oraxens glyph configs");
            return migrateDefaultFontJson(name);
        } else if (name.matches("assets/.*/sounds.json")) {
            Logs.logWarning("Found a sounds.json duplicate, trying to migrate it into Oraxens sound.yml config");
            return migrateSoundJson(name);
        } else if (name.startsWith("assets/minecraft/shaders")) {
            Logs.logWarning("Failed to migrate duplicate file-entry, file is a shader file");
            Logs.logWarning("Merging this is too advanced and should be migrated manually or deleted.");
            return false;
        } else if (name.startsWith("assets/minecraft/textures/models/armor/leather_layer")) {
            Logs.logWarning("Failed to migrate duplicate file-entry, file is a combined custom armor texture");
            Logs.logWarning("You should not import already combined armor layer files, but individual ones for every armor set you want.");
            Logs.logWarning("Please refer to https://docs.oraxen.com/configuration/custom-armors for more information");
            return false;
        } else if (name.startsWith("assets/minecraft/textures")) {
            Logs.logWarning("Failed to migrate duplicate file-entry, file is a texture file");
            Logs.logWarning("Cannot migrate texture files, rename this or the duplicate entry");
            return false;
        } else {
            Logs.logWarning("Failed to migrate duplicate file-entry, file is not a file that Oraxen can migrate right now");
            Logs.logWarning("Please refer to https://docs.oraxen.com/ on how to solve this, or ask in the support Discord");
            return false;
        }
    }

    private static boolean migrateItemJson(String name) {
        String itemMaterial = Utils.removeParentDirs(name).split(".json")[0].toUpperCase();
        try {
            Material.valueOf(itemMaterial);
        } catch (IllegalArgumentException e) {
            Logs.logWarning("Failed to migrate duplicate file-entry, could not find material");
            return false;
        }

        if (!name.endsWith(".json")) {
            Logs.logWarning("Failed to migrate duplicate file-entry, file is not a .json file");
            return false;
        }
        YamlConfiguration migratedYaml = loadMigrateYaml("items");
        if (migratedYaml == null) {
            Logs.logWarning("Failed to migrate duplicate file-entry, failed to load items/migrated_duplicates.yml");
            return false;
        }
        Path path = Path.of(OraxenPlugin.get().getDataFolder().getAbsolutePath(), "\\pack\\", name);
        String fileContent;
        try {
            fileContent = Files.readString(path);
        } catch (IOException e) {
            Logs.logWarning("Failed to migrate duplicate file-entry, could not read file");
            return false;
        }

        JsonObject json = JsonParser.parseString(fileContent).getAsJsonObject();
        if (json.getAsJsonArray("overrides") != null) for (JsonElement element : json.getAsJsonArray("overrides")) {
            JsonObject predicate = element.getAsJsonObject().get("predicate").getAsJsonObject();
            String modelPath = element.getAsJsonObject().get("model").getAsString().replace("\\", "/");
            String id = "migrated_" + Utils.removeParentDirs(modelPath);
            int cmd;
            try {
                cmd = predicate.get("custom_model_data").getAsInt();
            } catch (NullPointerException e) {
                Logs.logWarning("Failed to migrate duplicate file-entry, could not find custom_model_data");
                return false;
            }

            migratedYaml.set(id + ".material", itemMaterial);
            migratedYaml.set(id + ".excludeFromInventory", true);
            migratedYaml.set(id + ".excludeFromCommands", true);
            migratedYaml.set(id + ".Pack.custom_model_data", cmd);
            migratedYaml.set(id + ".Pack.model", modelPath);
            migratedYaml.set(id + ".Pack.generate_model", false);
        }

        try {
            migratedYaml.save(new File(OraxenPlugin.get().getDataFolder(), "/items/migrated_duplicates.yml"));
        } catch (IOException e) {
            Logs.logWarning("Failed to migrate duplicate file-entry, could not save migrated_duplicates.yml");
            return false;
        }

        return true;
    }

    private static boolean migrateSoundJson(String name) {
        Path path = Path.of(OraxenPlugin.get().getDataFolder().getAbsolutePath(), "/pack/", name);
        try {
            String fileContent;
            try {
                fileContent = Files.readString(path);
            } catch (IOException e) {
                Logs.logWarning("Failed to migrate duplicate file-entry, could not read file");
                return false;
            }

            JsonObject sounds = JsonParser.parseString(fileContent).getAsJsonObject();
            YamlConfiguration soundYaml = YamlConfiguration.loadConfiguration(new File(OraxenPlugin.get().getDataFolder().getAbsolutePath(), "/sound.yml"));
            for (String id : sounds.keySet()) {
                if (soundYaml.contains("sounds." + id)) {
                    Logs.logWarning("Sound " + id + " is already defined in sound.yml, skipping");
                    continue;
                }
                JsonObject sound = sounds.get(id).getAsJsonObject();
                boolean replace = sound.get("replace") != null && sound.get("replace").getAsBoolean();
                String category = sound.get("category") != null && sound.get("category").getAsString() != null ? sound.get("category").getAsString() : null;
                String subtitle = sound.get("subtitle").getAsString() != null ? sound.get("subtitle").getAsString() : null;
                JsonArray soundArray = sound.getAsJsonArray("sounds");
                List<String> soundList = new ArrayList<>();
                if (soundArray != null) for (JsonElement s : soundArray) soundList.add(s.getAsString());

                soundYaml.set("sounds." + id + ".replace", replace);
                soundYaml.set("sounds." + id + ".category", category != null ? category : "master");
                if (subtitle != null) soundYaml.set("sounds." + id + ".subtitle", subtitle);
                soundYaml.set("sounds." + id + ".sounds", soundList);

                try {
                    soundYaml.save(new File(OraxenPlugin.get().getDataFolder().getAbsolutePath(), "/sound.yml"));
                    Logs.logSuccess("Successfully migrated sound <blue>" + id + "</blue> into sound.yml");
                } catch (IOException e) {
                    Logs.logWarning("Failed to migrate duplicate file-entry, could not save <blue>" + id + "</blue> to sound.yml");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private static boolean migrateDefaultFontJson(String name) {
        Path path = Path.of(OraxenPlugin.get().getDataFolder().getAbsolutePath(), "/pack/", name);
        try {
            YamlConfiguration glyphYaml = loadMigrateYaml("glyphs");

            if (glyphYaml == null) {
                Logs.logWarning("Failed to migrate duplicate file-entry, failed to load glyphs/migrated_duplicates.yml");
                return false;
            }

            String fileContent;
            try {
                fileContent = Files.readString(path);
            } catch (IOException e) {
                Logs.logWarning("Failed to migrate duplicate file-entry, could not read file");
                return false;
            }

            JsonObject json = JsonParser.parseString(fileContent).getAsJsonObject();
            if (json.getAsJsonArray("providers") == null) {
                Logs.logWarning("Failed to migrate duplicate file-entry, file is not a valid font file");
                return false;
            }

            for (JsonElement element : json.getAsJsonArray("providers")) {
                JsonObject provider = element.getAsJsonObject();
                String file = provider.get("file").getAsString();
                JsonArray charsArray = provider.getAsJsonArray("chars");
                List<String> charList = new ArrayList<>();
                if (charsArray != null) for (JsonElement c : charsArray) charList.add(c.getAsString());
                int ascent, height;

                try {
                    ascent = provider.get("ascent").getAsInt();
                    height = provider.get("height").getAsInt();
                } catch (IllegalStateException e) {
                    ascent = 8;
                    height = 8;
                }

                glyphYaml.set("glyphs." + file + ".file", file != null ? file : "required/exit_icon.png");
                glyphYaml.set("glyphs." + file + ".chars", charList);
                glyphYaml.set("glyphs." + file + ".ascent", ascent);
                glyphYaml.set("glyphs." + file + ".height", height);

                try {
                    glyphYaml.save(new File(OraxenPlugin.get().getDataFolder(), "/glyphs/migrated_duplicates.yml"));
                } catch (IOException e) {
                    Logs.logWarning("Failed to migrate duplicate file-entry, could not save migrated_duplicates.yml");
                    return false;
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private static YamlConfiguration loadMigrateYaml(String folder) {
        File file = new File(OraxenPlugin.get().getDataFolder(), "\\" + folder + "\\" + "migrated_duplicates.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                return null;
            }
        }
        try {
            return YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            return null;
        }
    }
}
