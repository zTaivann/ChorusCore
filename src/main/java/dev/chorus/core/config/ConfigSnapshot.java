package dev.chorus.core.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Every value in every open config file, so a reload can say what changed. */
public record ConfigSnapshot(Map<String, String> values) {

    /** What a reload did. */
    public record Change(List<String> added, List<String> removed, List<String> changed) {

        public int total() {
            return added.size() + removed.size() + changed.size();
        }

        public boolean isEmpty() {
            return total() == 0;
        }

        /** The first few, for a line in chat. */
        public List<String> first(int count) {
            List<String> all = new ArrayList<>(changed);
            all.addAll(added);
            all.addAll(removed);
            return all.size() <= count ? all : all.subList(0, count);
        }
    }

    /** Read from what is already loaded, so taking one reads no file. */
    public static ConfigSnapshot of(ConfigFiles configs) {
        Map<String, String> values = new HashMap<>();
        for (String path : configs.paths()) {
            YamlConfiguration file = configs.get(path).data();
            for (String key : file.getKeys(true)) {
                if (!file.isConfigurationSection(key)) {
                    values.put(path + " " + key, String.valueOf(file.get(key)));
                }
            }
        }
        return new ConfigSnapshot(Map.copyOf(values));
    }

    public Change since(ConfigSnapshot before) {
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();

        for (String key : new TreeSet<>(values.keySet())) {
            String was = before.values().get(key);
            if (was == null) {
                added.add(key);
            } else if (!was.equals(values.get(key))) {
                changed.add(key);
            }
        }
        for (String key : new TreeSet<>(before.values().keySet())) {
            if (!values.containsKey(key)) {
                removed.add(key);
            }
        }
        return new Change(List.copyOf(added), List.copyOf(removed), List.copyOf(changed));
    }
}
