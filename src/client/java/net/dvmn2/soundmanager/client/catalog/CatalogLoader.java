package net.dvmn2.soundmanager.client.catalog;

import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the resource reads of the GUI on one background thread and caches sound lengths.
 * <p>
 * Futures returned here never complete exceptionally: a failure is logged and replaced by an empty result.
 * Poll them on the client thread.
 */
public final class CatalogLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("soundmanager");

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "soundmanager-io");
        thread.setDaemon(true);
        return thread;
    });
    /** Sound file id to length in milliseconds (-1 = unknown). */
    private static final Map<Identifier, Long> DURATIONS = new ConcurrentHashMap<>();

    private CatalogLoader() {
    }

    /** Builds the catalog in the background. Also drops cached lengths, since the resource packs may have changed. */
    public static CompletableFuture<SoundCatalog> loadCatalog(ResourceManager manager) {
        DURATIONS.clear();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return SoundCatalog.load(manager);
            } catch (RuntimeException ex) {
                LOGGER.warn("Could not build the sound list", ex);
                return SoundCatalog.empty();
            }
        }, IO);
    }

    /** Reads the length of a sound file in the background; -1 if it is unknown. */
    public static CompletableFuture<Long> loadDuration(ResourceManager manager, Identifier file) {
        Long cached = DURATIONS.get(file);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            long durationMs = -1L;
            try {
                durationMs = OggDuration.read(manager, file);
            } catch (RuntimeException ex) {
                LOGGER.warn("Could not read the length of {}", file, ex);
            }
            DURATIONS.put(file, durationMs);
            return durationMs;
        }, IO);
    }
}
