package net.dvmn2.soundmanager.client.sound;

/** Linear transition of a value over time. Used on the client thread only. */
final class Ramp {

    private static final long NANOS_PER_MS = 1_000_000L;

    private float from;
    private float to;
    private long startNs;
    private long durationNs;

    Ramp(float value) {
        this.from = value;
        this.to = value;
    }

    /** Value at the given {@link System#nanoTime()} moment. */
    float get(long nowNs) {
        if (durationNs <= 0 || nowNs >= startNs + durationNs) {
            return to;
        }
        return from + (to - from) * ((nowNs - startNs) / (float) durationNs);
    }

    /** The value this ramp is heading to. */
    float target() {
        return to;
    }

    /** Starts a new transition from the current value; {@code durationMs = 0} jumps immediately. */
    void retarget(float target, int durationMs, long nowNs) {
        this.from = get(nowNs);
        this.to = target;
        this.startNs = nowNs;
        this.durationNs = Math.max(0, durationMs) * NANOS_PER_MS;
    }
}
