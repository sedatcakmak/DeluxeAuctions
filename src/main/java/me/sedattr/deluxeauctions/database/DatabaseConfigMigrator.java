package me.sedattr.deluxeauctions.database;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Moves the legacy top-level {@code database:} section of config.yml into database.yml.
 * <p>
 * Steps, each one guarded so a failure leaves the old setup working:
 * <ol>
 *     <li>config.yml is copied to {@code backups/} first;</li>
 *     <li>database.yml is written and read back, and every value is compared with the original;</li>
 *     <li>only then is the block cut out of config.yml -- as text, so the user's comments and
 *     formatting elsewhere in the file are kept -- and the result is checked to still parse and
 *     to have lost nothing but {@code database}.</li>
 * </ol>
 * Kept free of plugin state so it can be exercised outside a running server.
 */
public final class DatabaseConfigMigrator {
    public enum Result { NOT_NEEDED, MIGRATED, FAILED }

    private static final Pattern TOP_LEVEL_DATABASE = Pattern.compile("^database\\s*:\\s*(#.*)?$");

    private static final String HEADER = String.join("\n",
            "# PLUGIN DATABASE",
            "# Database settings, automatically moved here from config.yml on %date%.",
            "# A copy of the old config.yml is in backups/%backup%.",
            "# Database Types: mysql, sqlite. MySQL driver: com.mysql.jdbc.Driver (new: com.mysql.cj.jdbc.Driver)",
            "");

    private DatabaseConfigMigrator() {
    }

    public static Result migrate(File dataFolder, List<String> log) {
        File configFile = new File(dataFolder, "config.yml");
        File databaseFile = new File(dataFolder, "database.yml");
        if (databaseFile.exists() || !configFile.exists())
            return Result.NOT_NEEDED;

        try {
            String configText = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
            YamlConfiguration config = new YamlConfiguration();
            config.loadFromString(configText);

            ConfigurationSection legacy = config.getConfigurationSection("database");
            if (legacy == null)
                return Result.NOT_NEEDED;

            Map<String, Object> values = leafValues(legacy);
            if (values.isEmpty())
                return Result.NOT_NEEDED;

            // 1) Backup
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            File backupFolder = new File(dataFolder, "backups");
            if (!backupFolder.exists() && !backupFolder.mkdirs())
                throw new IOException("could not create " + backupFolder);
            String backupName = "config_before_database_move_" + stamp + ".yml";
            Files.copy(configFile.toPath(), new File(backupFolder, backupName).toPath(), StandardCopyOption.REPLACE_EXISTING);

            // 2) database.yml, verified by reading it back
            YamlConfiguration database = new YamlConfiguration();
            for (Map.Entry<String, Object> entry : values.entrySet())
                database.set("database." + entry.getKey(), entry.getValue());

            String header = HEADER
                    .replace("%date%", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()))
                    .replace("%backup%", backupName);
            String databaseText = header + database.saveToString();

            File temp = new File(dataFolder, "database.yml.tmp");
            Files.writeString(temp.toPath(), databaseText, StandardCharsets.UTF_8);

            YamlConfiguration check = new YamlConfiguration();
            check.load(temp);
            ConfigurationSection checkSection = check.getConfigurationSection("database");
            if (checkSection == null || !leafValues(checkSection).equals(values)) {
                Files.deleteIfExists(temp.toPath());
                log.add("database.yml did not read back identically; keeping settings in config.yml.");
                return Result.FAILED;
            }

            // 3) Cut the block out of config.yml, then make sure only "database" went away
            String newConfigText = removeTopLevelBlock(configText);
            if (newConfigText == null) {
                Files.deleteIfExists(temp.toPath());
                log.add("Could not locate the database block in config.yml as text; keeping settings in config.yml.");
                return Result.FAILED;
            }

            YamlConfiguration newConfig = new YamlConfiguration();
            newConfig.loadFromString(newConfigText);
            Map<String, Object> before = leafValues(config);
            before.keySet().removeIf(key -> key.equals("database") || key.startsWith("database."));
            if (newConfig.contains("database") || !leafValues(newConfig).equals(before)) {
                Files.deleteIfExists(temp.toPath());
                log.add("Removing the database block would change other config.yml values; keeping settings in config.yml.");
                return Result.FAILED;
            }

            Files.move(temp.toPath(), databaseFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(configFile.toPath(), newConfigText, StandardCharsets.UTF_8);

            log.add("Database settings moved from config.yml to database.yml (backup: backups/" + backupName + ").");
            return Result.MIGRATED;
        } catch (Exception e) {
            new File(dataFolder, "database.yml.tmp").delete();
            log.add("Database settings could not be moved to database.yml (" + e.getMessage() + "); keeping settings in config.yml.");
            return Result.FAILED;
        }
    }

    /** Every non-section value under the section, keyed by its relative path. */
    private static Map<String, Object> leafValues(ConfigurationSection section) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String key : section.getKeys(true))
            if (!section.isConfigurationSection(key))
                values.put(key, section.get(key));
        return values;
    }

    /**
     * Removes the top-level {@code database:} key and everything nested under it, replacing it with
     * a pointer comment. Comment lines directly above it are kept unless they were indented into
     * the block. Returns null if the key is not found exactly once.
     */
    static String removeTopLevelBlock(String text) {
        String newline = text.contains("\r\n") ? "\r\n" : "\n";
        String[] lines = text.split("\r?\n", -1);

        int start = -1;
        for (int i = 0; i < lines.length; i++)
            if (TOP_LEVEL_DATABASE.matcher(lines[i]).matches()) {
                if (start != -1)
                    return null;
                start = i;
            }
        if (start == -1)
            return null;

        // The block runs until the next line that starts at column 0 and is not blank. Indented
        // comments and blank lines in between belong to it; a column-0 comment ends it.
        int end = start + 1;
        while (end < lines.length) {
            String line = lines[end];
            if (!line.isBlank() && !Character.isWhitespace(line.charAt(0)))
                break;
            end++;
        }
        // Give trailing blank lines back to whatever follows.
        while (end > start + 1 && lines[end - 1].isBlank())
            end--;

        List<String> result = new ArrayList<>(Arrays.asList(lines).subList(0, start));
        result.add("# database: moved to database.yml");
        result.addAll(Arrays.asList(lines).subList(end, lines.length));
        return String.join(newline, result);
    }
}
