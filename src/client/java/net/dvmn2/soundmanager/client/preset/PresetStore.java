package net.dvmn2.soundmanager.client.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.dvmn2.soundmanager.client.AtomicFiles;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Named presets of the GUI stored in {@code config/soundmanager_presets.json}.
 * <p>
 * Loaded lazily and written after every change; used on the client thread only. A damaged file is treated as an
 * empty list and logged, invalid entries are skipped.
 */
public final class PresetStore {

    /** Most presets the store keeps. */
    public static final int MAX_PRESETS = 200;
    /** Longest preset name, characters. */
    public static final int MAX_NAME_LENGTH = 40;

    private static final Logger LOGGER = LoggerFactory.getLogger("soundmanager");
    private static final String FILE_NAME = "soundmanager_presets.json";
    private static final int FORMAT_VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static PresetFile instance;

    private PresetStore() {
    }

    /** Outcome of {@link #save(PresetDto)}. */
    public enum SaveResult { SAVED, REPLACED, BAD_NAME, LIMIT_REACHED, WRITE_FAILED }

    /** A snapshot of the presets in saved order. */
    public static synchronized List<PresetDto> list() {
        return List.copyOf(file().presets);
    }

    /** Saves the preset; an existing preset with the same name (ignoring case) is replaced. */
    public static synchronized SaveResult save(PresetDto preset) {
        String name = preset.name.strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return SaveResult.BAD_NAME;
        }
        preset.name = name;
        PresetFile file = file();
        int index = indexOf(file.presets, name);
        if (index < 0 && file.presets.size() >= MAX_PRESETS) {
            return SaveResult.LIMIT_REACHED;
        }
        if (index >= 0) {
            file.presets.set(index, preset);
        } else {
            file.presets.add(preset);
        }
        if (!write(file)) {
            return SaveResult.WRITE_FAILED;
        }
        return index >= 0 ? SaveResult.REPLACED : SaveResult.SAVED;
    }

    /** Deletes the preset with the name (ignoring case); returns false if there is none. */
    public static synchronized boolean delete(String name) {
        PresetFile file = file();
        int index = indexOf(file.presets, name);
        if (index < 0) {
            return false;
        }
        file.presets.remove(index);
        write(file);
        return true;
    }

    // ---------- File ----------

    private static PresetFile file() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    private static PresetFile load() {
        Path source = path();
        PresetFile loaded = null;
        if (Files.isRegularFile(source)) {
            try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, PresetFile.class);
            } catch (IOException | JsonParseException ex) {
                LOGGER.warn("Could not read {}; starting with no presets", source, ex);
            }
        }
        PresetFile result = new PresetFile();
        if (loaded == null || loaded.presets == null) {
            return result;
        }
        for (PresetDto preset : loaded.presets) {
            if (result.presets.size() >= MAX_PRESETS) {
                LOGGER.warn("Ignored presets over the limit of {} in {}", MAX_PRESETS, source);
                break;
            }
            if (isValid(preset) && indexOf(result.presets, preset.name) < 0) {
                preset.name = preset.name.strip();
                result.presets.add(preset);
            } else {
                LOGGER.warn("Skipped an invalid or duplicate preset in {}", source);
            }
        }
        return result;
    }

    private static boolean write(PresetFile file) {
        Path target = path();
        try {
            AtomicFiles.writeString(target, GSON.toJson(file));
            return true;
        } catch (IOException ex) {
            LOGGER.warn("Could not save {}", target, ex);
            return false;
        }
    }

    private static boolean isValid(PresetDto preset) {
        if (preset == null || preset.name == null || preset.mode == null || preset.targets == null
                || preset.sound == null || preset.category == null) {
            return false;
        }
        String name = preset.name.strip();
        return !name.isEmpty() && name.length() <= MAX_NAME_LENGTH;
    }

    private static int indexOf(List<PresetDto> presets, String name) {
        String wanted = name.strip().toLowerCase(Locale.ROOT);
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).name.toLowerCase(Locale.ROOT).equals(wanted)) {
                return i;
            }
        }
        return -1;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    /** On-disk layout of the presets file. */
    private static final class PresetFile {
        int version = FORMAT_VERSION;
        List<PresetDto> presets = new ArrayList<>();
    }
}
