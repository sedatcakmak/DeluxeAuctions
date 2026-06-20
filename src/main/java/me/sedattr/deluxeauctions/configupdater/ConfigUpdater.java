package me.sedattr.deluxeauctions.configupdater;

import com.google.common.base.Preconditions;
import me.sedattr.deluxeauctions.DeluxeAuctions;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.YamlConstructor;
import org.bukkit.configuration.file.YamlRepresenter;
import org.bukkit.plugin.Plugin;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ConfigUpdater {
    private static final char SEPARATOR = '.';

    public static void update(Plugin plugin, String resourceName, File toUpdate, String... ignoredSections) throws IOException {
        update(plugin, resourceName, toUpdate, Arrays.asList(ignoredSections));
    }

    public static void update(Plugin plugin, String resourceName, File toUpdate, List<String> ignoredSections) throws IOException {
        Preconditions.checkArgument(toUpdate.exists(), "The toUpdate file doesn't exist!");

        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(plugin.getResource(resourceName)), StandardCharsets.UTF_8));
        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(toUpdate);
        Map<String, String> comments = parseComments(plugin, resourceName, defaultConfig);
        Map<String, String> ignoredSectionsValues = parseIgnoredSections(toUpdate, comments, ignoredSections == null ? Collections.emptyList() : ignoredSections);
        StringWriter writer = new StringWriter();
        write(defaultConfig, currentConfig, new BufferedWriter(writer), comments, ignoredSectionsValues);
        String value = writer.toString();

        Path toUpdatePath = toUpdate.toPath();
        if (!value.equals(Files.readString(toUpdatePath))) {
            Files.writeString(toUpdatePath, value);
        }
    }

    private static void write(FileConfiguration defaultConfig, FileConfiguration currentConfig, BufferedWriter writer, Map<String, String> comments, Map<String, String> ignoredSectionsValues) throws IOException {
        FileConfiguration parserConfig = new YamlConfiguration();

       for (String fullKey : getMergedKeys(defaultConfig, currentConfig)) {
            String indents = KeyUtils.getIndents(fullKey, SEPARATOR);

           if (!ignoredSectionsValues.isEmpty()) {
               if (writeIgnoredSectionValueIfExists(ignoredSectionsValues, writer, fullKey))
                   continue;
           }

           writeCommentIfExists(comments, writer, fullKey, indents);
           Object currentValue = currentConfig.get(fullKey);

           if (currentValue == null)
               currentValue = defaultConfig.get(fullKey);
           if (fullKey.equalsIgnoreCase("file_versions"))
               currentValue = DeluxeAuctions.getInstance().fileVersions;

           String[] splitFullKey = fullKey.split("[" + SEPARATOR + "]");
           String trailingKey = splitFullKey[splitFullKey.length - 1];

           if (currentValue instanceof ConfigurationSection) {
               writeConfigurationSection(writer, indents, trailingKey, (ConfigurationSection) currentValue);
               continue;
           }
           writeYamlValue(parserConfig, writer, indents, trailingKey, currentValue);
       }

        String danglingComments = comments.get(null);

        if (danglingComments != null)
            writer.write(danglingComments);

        writer.close();
    }

    // Build the ordered list of keys to write: every key from the bundled default
    // (so its order and comments are preserved) PLUS any key the user added that the
    // default doesn't define. The original code walked only defaultConfig.getKeys(true),
    // which silently dropped user-added keys (e.g. per-currency economy_fees /
    // economy_formulas) on every update. Walking the union keeps them intact.
    private static List<String> getMergedKeys(ConfigurationSection defaultConfig, ConfigurationSection currentConfig) {
        List<String> keys = new ArrayList<>();
        collectKeys(defaultConfig, currentConfig, "", keys);
        return keys;
    }

    private static void collectKeys(ConfigurationSection defaultSection, ConfigurationSection currentSection, String parent, List<String> keys) {
        // Default keys first (keeps default ordering), then any user-only keys appended.
        LinkedHashSet<String> childKeys = new LinkedHashSet<>();
        if (defaultSection != null)
            childKeys.addAll(defaultSection.getKeys(false));
        if (currentSection != null)
            childKeys.addAll(currentSection.getKeys(false));

        for (String child : childKeys) {
            String fullKey = parent.isEmpty() ? child : parent + SEPARATOR + child;
            keys.add(fullKey);

            ConfigurationSection defaultChild = defaultSection != null ? defaultSection.getConfigurationSection(child) : null;
            ConfigurationSection currentChild = currentSection != null ? currentSection.getConfigurationSection(child) : null;
            if (defaultChild != null || currentChild != null)
                collectKeys(defaultChild, currentChild, fullKey, keys);
        }
    }

    private static Map<String, String> parseComments(Plugin plugin, String resourceName, FileConfiguration defaultConfig) throws IOException {
        List<String> keys = new ArrayList<>(defaultConfig.getKeys(true));
        BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(plugin.getResource(resourceName))));
        Map<String, String> comments = new LinkedHashMap<>();
        StringBuilder commentBuilder = new StringBuilder();
        KeyBuilder keyBuilder = new KeyBuilder(defaultConfig, SEPARATOR);
        String currentValidKey = null;

        String line;
        while ((line = reader.readLine()) != null) {
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("-")) continue;

            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                commentBuilder.append(trimmedLine).append("\n");
            } else {
                if (!line.startsWith(" ")) {
                    keyBuilder.clear();
                    currentValidKey = trimmedLine;
                }

                keyBuilder.parseLine(trimmedLine, true);
                String key = keyBuilder.toString();

                if (!commentBuilder.isEmpty()) {
                    comments.put(key, commentBuilder.toString());
                    commentBuilder.setLength(0);
                }

                int nextKeyIndex = keys.indexOf(keyBuilder.toString()) + 1;
                if (nextKeyIndex < keys.size()) {

                    String nextKey = keys.get(nextKeyIndex);
                    while (!keyBuilder.isEmpty() && !nextKey.startsWith(keyBuilder.toString())) {
                        keyBuilder.removeLastKey();
                    }

                    if (keyBuilder.isEmpty()) {
                        keyBuilder.parseLine(currentValidKey, false);
                    }
                }
            }
        }
        reader.close();

        if (!commentBuilder.isEmpty())
            comments.put(null, commentBuilder.toString());

        return comments;
    }

    private static Map<String, String> parseIgnoredSections(File toUpdate, Map<String, String> comments, List<String> ignoredSections) throws IOException {
        Map<String, String> ignoredSectionValues = new LinkedHashMap<>(ignoredSections.size());

        DumperOptions options = new DumperOptions();
        options.setLineBreak(DumperOptions.LineBreak.UNIX);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(new YamlConstructor(), new YamlRepresenter(), options);

        Map<Object, Object> root = yaml.load(new FileReader(toUpdate));
        ignoredSections.forEach(section -> {
            String[] split = section.split("[" + SEPARATOR + "]");
            String key = split[split.length - 1];
            Map<Object, Object> map = getSection(section, root);

            StringBuilder keyBuilder = new StringBuilder();
            for (int i = 0; i < split.length; i++) {
                if (i != split.length - 1) {
                    if (!keyBuilder.isEmpty())
                        keyBuilder.append(SEPARATOR);

                    keyBuilder.append(split[i]);
                }
            }

            ignoredSectionValues.put(section, buildIgnored(key, map, comments, keyBuilder, new StringBuilder(), yaml));
        });

        return ignoredSectionValues;
    }

    private static Map<Object, Object> getSection(String fullKey, Map<Object, Object> root) {
        String[] keys = fullKey.split("[" + SEPARATOR + "]", 2);
        String key = keys[0];
        Object value = root.get(getKeyAsObject(key, root));

        if (keys.length == 1) {
            if (value instanceof Map)
                return root;

            throw new IllegalArgumentException("Ignored sections must be a ConfigurationSection not a value!");
        }

        if (!(value instanceof Map))
            throw new IllegalArgumentException("Invalid ignored ConfigurationSection specified! Information: " + fullKey + " , " + value + " , " + root);

        return getSection(keys[1], (Map<Object, Object>) value);
    }

    private static String buildIgnored(String fullKey, Map<Object, Object> ymlMap, Map<String, String> comments, StringBuilder keyBuilder, StringBuilder ignoredBuilder, Yaml yaml) {
        String[] keys = fullKey.split("[" + SEPARATOR + "]", 2);
        String key = keys[0];
        Object originalKey = getKeyAsObject(key, ymlMap);

        if (!keyBuilder.isEmpty())
            keyBuilder.append(".");

        keyBuilder.append(key);

        if (!ymlMap.containsKey(originalKey)) {
            if (keys.length == 1)
                throw new IllegalArgumentException("Invalid ignored section: " + keyBuilder);

            throw new IllegalArgumentException("Invalid ignored section: " + keyBuilder + "." + keys[1]);
        }

        String comment = comments.get(keyBuilder.toString());
        String indents = KeyUtils.getIndents(keyBuilder.toString(), SEPARATOR);

        if (comment != null)
            ignoredBuilder.append(addIndentation(comment, indents)).append("\n");

        ignoredBuilder.append(addIndentation(key, indents)).append(":");
        Object obj = ymlMap.get(originalKey);

        if (obj instanceof Map) {
            Map<Object, Object> map = (Map<Object, Object>) obj;

            if (map.isEmpty()) {
                ignoredBuilder.append(" {}\n");
            } else {
                ignoredBuilder.append("\n");
            }

            StringBuilder preLoopKey = new StringBuilder(keyBuilder);

            for (Object o : map.keySet()) {
                buildIgnored(o.toString(), map, comments, keyBuilder, ignoredBuilder, yaml);
                keyBuilder = new StringBuilder(preLoopKey);
            }
        } else {
            writeIgnoredValue(yaml, obj, ignoredBuilder, indents);
        }

        return ignoredBuilder.toString();
    }

    private static void writeIgnoredValue(Yaml yaml, Object toWrite, StringBuilder ignoredBuilder, String indents) {
        String yml = yaml.dump(toWrite);
        if (toWrite instanceof Collection) {
            ignoredBuilder.append("\n").append(addIndentation(yml, indents)).append("\n");
        } else {
            ignoredBuilder.append(" ").append(yml);
        }
    }

    private static String addIndentation(String s, String indents) {
        StringBuilder builder = new StringBuilder();
        String[] split = s.split("\n");

        for (String value : split) {
            if (!builder.isEmpty())
                builder.append("\n");

            builder.append(indents).append(value);
        }

        return builder.toString();
    }

    private static void writeCommentIfExists(Map<String, String> comments, BufferedWriter writer, String fullKey, String indents) throws IOException {
        String comment = comments.get(fullKey);

        if (comment != null)
            writer.write(indents + comment.substring(0, comment.length() - 1).replace("\n", "\n" + indents) + "\n");
    }

    private static Object getKeyAsObject(String key, Map<Object, Object> sectionContext) {
        if (sectionContext.containsKey(key))
            return key;

        try {
            Float keyFloat = Float.parseFloat(key);

            if (sectionContext.containsKey(keyFloat))
                return keyFloat;
        } catch (NumberFormatException ignored) {}

        try {
            Double keyDouble = Double.parseDouble(key);

            if (sectionContext.containsKey(keyDouble))
                return keyDouble;
        } catch (NumberFormatException ignored) {}

        try {
            Integer keyInteger = Integer.parseInt(key);

            if (sectionContext.containsKey(keyInteger))
                return keyInteger;
        } catch (NumberFormatException ignored) {}

        try {
            Long longKey = Long.parseLong(key);

            if (sectionContext.containsKey(longKey))
                return longKey;
        } catch (NumberFormatException ignored) {}

        return null;
    }

	private static void writeYamlValue(final FileConfiguration parserConfig, final BufferedWriter bufferedWriter, final String indents, final String trailingKey, final Object currentValue) throws IOException {
		parserConfig.set(trailingKey, currentValue);
		String yaml = parserConfig.saveToString();
		yaml = yaml.substring(0, yaml.length() - 1).replace("\n", "\n" + indents);
		final String toWrite = indents + yaml + "\n";
		parserConfig.set(trailingKey, null);
		bufferedWriter.write(toWrite);
	}

    private static boolean writeIgnoredSectionValueIfExists(final Map<String, String> ignoredSectionsValues, final BufferedWriter bufferedWriter, final String fullKey) throws IOException {
        String ignored = ignoredSectionsValues.get(fullKey);
        if (ignored != null) {
            bufferedWriter.write(ignored);
            return true;
        }
        for (final Map.Entry<String, String> entry : ignoredSectionsValues.entrySet()) {
            if (KeyUtils.isSubKeyOf(entry.getKey(), fullKey, SEPARATOR)) {
                return true;
            }
        }
        return false;
    }

	private static void writeConfigurationSection(final BufferedWriter bufferedWriter, final String indents, final String trailingKey, final ConfigurationSection configurationSection) throws IOException {
		bufferedWriter.write(indents + trailingKey + ":");
		if (!(configurationSection).getKeys(false).isEmpty()) {
			bufferedWriter.write("\n");
		} else {
			bufferedWriter.write(" {}\n");
		}
	}
}
