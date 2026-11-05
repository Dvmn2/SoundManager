package net.dvmn2.soundmanager.client.catalog;

import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * Reads the length of an Ogg Vorbis file without decoding it.
 * <p>
 * The length is the granule position of the last Ogg page divided by the sample rate from the Vorbis
 * identification header. Pure functions; safe to call from any thread.
 */
public final class OggDuration {

    private static final Logger LOGGER = LoggerFactory.getLogger("soundmanager");

    /** Files larger than this are not read, bytes. */
    private static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
    private static final int PAGE_HEADER_BYTES = 27;
    private static final int SEGMENT_COUNT_OFFSET = 26;
    private static final int GRANULE_OFFSET = 6;
    /** Offset of the sample rate inside the identification packet: type, "vorbis", version, channels. */
    private static final int SAMPLE_RATE_OFFSET = 12;
    private static final int IDENTIFICATION_MIN_BYTES = 16;
    private static final String CAPTURE = "OggS";
    private static final String VORBIS = "vorbis";
    private static final long MS_PER_SECOND = 1000L;

    private OggDuration() {
    }

    /**
     * Length of the sound file in milliseconds, or -1 if the file is missing, too large or not a Vorbis stream.
     * Blocking: run it off the client thread.
     *
     * @param file sound file id as in {@code sounds.json} (without {@code sounds/} and {@code .ogg})
     */
    public static long read(ResourceManager manager, Identifier file) {
        Identifier path = Identifier.of(file.getNamespace(), "sounds/" + file.getPath() + ".ogg");
        Optional<Resource> resource = manager.getResource(path);
        if (resource.isEmpty()) {
            return -1L;
        }
        try (InputStream in = resource.get().getInputStream()) {
            byte[] data = in.readNBytes(MAX_FILE_BYTES + 1);
            return data.length > MAX_FILE_BYTES ? -1L : durationMs(data);
        } catch (IOException ex) {
            LOGGER.debug("Could not read {}", path, ex);
            return -1L;
        }
    }

    /** Length of the Ogg Vorbis data in milliseconds, or -1 if it cannot be determined. */
    static long durationMs(byte[] data) {
        int sampleRate = readSampleRate(data);
        if (sampleRate <= 0) {
            return -1L;
        }
        long granule = readLastGranule(data);
        if (granule <= 0) {
            return -1L;
        }
        return granule * MS_PER_SECOND / sampleRate;
    }

    private static int readSampleRate(byte[] data) {
        if (data.length < PAGE_HEADER_BYTES || !matches(data, 0, CAPTURE)) {
            return -1;
        }
        int start = PAGE_HEADER_BYTES + (data[SEGMENT_COUNT_OFFSET] & 0xFF);
        if (data.length < start + IDENTIFICATION_MIN_BYTES || data[start] != 1 || !matches(data, start + 1, VORBIS)) {
            return -1;
        }
        return readIntLe(data, start + SAMPLE_RATE_OFFSET);
    }

    /**
     * Granule position of the last page. The last page is the one that ends exactly at the end of the data, which
     * rules out the capture pattern appearing by chance inside audio data.
     */
    private static long readLastGranule(byte[] data) {
        for (int page = data.length - PAGE_HEADER_BYTES; page >= 0; page--) {
            if (data[page + 4] != 0 || !matches(data, page, CAPTURE)) {
                continue;
            }
            int segments = data[page + SEGMENT_COUNT_OFFSET] & 0xFF;
            int header = PAGE_HEADER_BYTES + segments;
            if (page + header > data.length) {
                continue;
            }
            long body = 0;
            for (int i = 0; i < segments; i++) {
                body += data[page + PAGE_HEADER_BYTES + i] & 0xFF;
            }
            if (page + header + body == data.length) {
                return readLongLe(data, page + GRANULE_OFFSET);
            }
        }
        return -1L;
    }

    private static boolean matches(byte[] data, int offset, String ascii) {
        if (offset + ascii.length() > data.length) {
            return false;
        }
        for (int i = 0; i < ascii.length(); i++) {
            if (data[offset + i] != (byte) ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int readIntLe(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | (data[offset + 1] & 0xFF) << 8
                | (data[offset + 2] & 0xFF) << 16
                | (data[offset + 3] & 0xFF) << 24;
    }

    private static long readLongLe(byte[] data, int offset) {
        long value = 0;
        for (int i = 7; i >= 0; i--) {
            value = (value << 8) | (data[offset + i] & 0xFF);
        }
        return value;
    }
}
