package net.dvmn2.soundmanager.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Player-side settings stored in {@code config/soundmanager.json}.
 * <p>
 * The server cannot override these values. Access through {@link #get()}; fields are read and written on the
 * client thread only.
 */
public final class SoundManagerConfig {

    /** Upper bound of {@link #maxVolume} and of any volume the server may request. */
    public static final float MAX_VOLUME = 10.0f;

    private static final Logger LOGGER = LoggerFactory.getLogger("soundmanager");
    private static final String FILE_NAME = "soundmanager.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static SoundManagerConfig instance;

    /** When false, all packets from the server are ignored and no managed sound plays. */
    public boolean enabled = true;
    /** Client-side cap for the volume of managed sounds, 0.0..10.0. */
    public float maxVolume = MAX_VOLUME;

    private SoundManagerConfig() {
    }

    /** Returns the loaded config, reading it from disk on first use. */
    public static synchronized SoundManagerConfig get() {
        if (instance == null) {
            instance = load();
            if (!Files.exists(path())) {
                save();
            }
        }
        return instance;
    }

    /** Writes the current values to disk. */
    public static synchronized void save() {
        if (instance == null) {
            return;
        }
        Path target = path();
        try {
            Files.createDirectories(target.getParent());
            Path tmp = target.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(tmp, GSON.toJson(instance), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            LOGGER.warn("Could not save {}", target, ex);
        }
    }

    private static SoundManagerConfig load() {
        Path source = path();
        SoundManagerConfig loaded = null;
        if (Files.isRegularFile(source)) {
            try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, SoundManagerConfig.class);
            } catch (IOException | JsonParseException ex) {
                LOGGER.warn("Could not read {}; using defaults", source, ex);
            }
        }
        if (loaded == null) {
            loaded = new SoundManagerConfig();
        }
        loaded.sanitize();
        return loaded;
    }

    private void sanitize() {
        if (Float.isNaN(maxVolume)) {
            maxVolume = MAX_VOLUME;
        }
        maxVolume = Math.max(0.0f, Math.min(MAX_VOLUME, maxVolume));
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
