package net.dvmn2.soundmanager.client.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Immutable index of the sound events known to the client, built from every {@code sounds.json} of the loaded
 * resource packs.
 * <p>
 * Built by {@link #load(ResourceManager)} off the client thread; the result may be used from any thread.
 */
public final class SoundCatalog {

    private static final Logger LOGGER = LoggerFactory.getLogger("soundmanager");

    private static final String SOUNDS_JSON = "sounds.json";
    private static final int MAX_EVENTS = 100_000;
    private static final int MAX_REFS_PER_EVENT = 1_024;
    private static final int MAX_RESOLVE_DEPTH = 4;
    private static final String TYPE_EVENT = "event";

    private static final SoundCatalog EMPTY = new SoundCatalog(Map.of());

    private final Map<Identifier, List<Ref>> events;
    private final List<Identifier> ids;

    private SoundCatalog(Map<Identifier, List<Ref>> events) {
        this.events = events;
        List<Identifier> sorted = new ArrayList<>(events.keySet());
        sorted.sort(null);
        this.ids = List.copyOf(sorted);
    }

    /** A catalog without sounds, used while loading and after a failure. */
    public static SoundCatalog empty() {
        return EMPTY;
    }

    /**
     * Reads every {@code sounds.json} of the resource manager. Blocking: run it off the client thread.
     * Files are merged in pack priority order; {@code "replace": true} drops the sounds of lower packs.
     */
    public static SoundCatalog load(ResourceManager manager) {
        Map<Identifier, List<Ref>> events = new HashMap<>();
        for (String namespace : manager.getAllNamespaces()) {
            for (Resource resource : manager.getAllResources(Identifier.of(namespace, SOUNDS_JSON))) {
                readFile(namespace, resource, events);
            }
        }
        Map<Identifier, List<Ref>> frozen = new HashMap<>();
        events.forEach((id, refs) -> frozen.put(id, List.copyOf(refs)));
        return new SoundCatalog(Map.copyOf(frozen));
    }

    /** All sound event ids, sorted. */
    public List<Identifier> ids() {
        return ids;
    }

    /** Ids that contain the query text (case-insensitive); an empty query returns all ids. */
    public List<Identifier> search(String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return ids;
        }
        List<Identifier> found = new ArrayList<>();
        for (Identifier id : ids) {
            if (id.toString().contains(needle)) {
                found.add(id);
            }
        }
        return found;
    }

    /** Number of sound files the event can pick from, following references to other events. */
    public int variants(Identifier event) {
        return countVariants(event, 0);
    }

    /**
     * The first sound file of the event (id without the {@code sounds/} prefix and the {@code .ogg} suffix),
     * or null if the event is unknown or has no file.
     */
    @Nullable
    public Identifier resolveFile(Identifier event) {
        return resolve(event, 0);
    }

    // ---------- Lookup ----------

    @Nullable
    private Identifier resolve(Identifier event, int depth) {
        List<Ref> refs = events.get(event);
        if (refs == null || depth > MAX_RESOLVE_DEPTH) {
            return null;
        }
        for (Ref ref : refs) {
            if (!ref.isEvent()) {
                return ref.name();
            }
            Identifier nested = resolve(ref.name(), depth + 1);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private int countVariants(Identifier event, int depth) {
        List<Ref> refs = events.get(event);
        if (refs == null || depth > MAX_RESOLVE_DEPTH) {
            return 0;
        }
        int count = 0;
        for (Ref ref : refs) {
            count += ref.isEvent() ? countVariants(ref.name(), depth + 1) : 1;
        }
        return count;
    }

    // ---------- Parsing ----------

    private static void readFile(String namespace, Resource resource, Map<Identifier, List<Ref>> events) {
        try (Reader reader = resource.getReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                if (events.size() >= MAX_EVENTS) {
                    return;
                }
                readEvent(namespace, entry.getKey(), entry.getValue(), events);
            }
        } catch (IOException | JsonParseException | IllegalStateException ex) {
            LOGGER.warn("Could not read {} of namespace '{}'", SOUNDS_JSON, namespace, ex);
        }
    }

    private static void readEvent(String namespace, String key, JsonElement value,
                                  Map<Identifier, List<Ref>> events) {
        Identifier id = Identifier.tryParse(namespace + ":" + key);
        if (id == null || !value.isJsonObject()) {
            return;
        }
        JsonObject object = value.getAsJsonObject();
        List<Ref> refs = events.computeIfAbsent(id, ignored -> new ArrayList<>());
        JsonElement replace = object.get("replace");
        if (replace != null && replace.isJsonPrimitive() && replace.getAsBoolean()) {
            refs.clear();
        }
        JsonElement sounds = object.get("sounds");
        if (sounds == null || !sounds.isJsonArray()) {
            return;
        }
        for (JsonElement sound : sounds.getAsJsonArray()) {
            if (refs.size() >= MAX_REFS_PER_EVENT) {
                return;
            }
            Ref ref = parseRef(namespace, sound);
            if (ref != null) {
                refs.add(ref);
            }
        }
    }

    @Nullable
    private static Ref parseRef(String namespace, JsonElement element) {
        String name;
        boolean isEvent = false;
        if (element.isJsonPrimitive()) {
            name = element.getAsString();
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            JsonElement nameElement = object.get("name");
            if (nameElement == null || !nameElement.isJsonPrimitive()) {
                return null;
            }
            name = nameElement.getAsString();
            JsonElement type = object.get("type");
            isEvent = type != null && type.isJsonPrimitive() && TYPE_EVENT.equals(type.getAsString());
        } else {
            return null;
        }
        Identifier id = Identifier.tryParse(name.indexOf(':') >= 0 ? name : namespace + ":" + name);
        return id == null ? null : new Ref(id, isEvent);
    }

    /** A sound entry of an event: a file, or another event when {@code isEvent} is true. */
    private record Ref(Identifier name, boolean isEvent) {
    }
}
