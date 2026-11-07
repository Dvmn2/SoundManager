package net.dvmn2.soundmanager.client.preset;

/**
 * One saved preset in {@code config/soundmanager_presets.json}. Public fields without logic; unknown fields are
 * ignored and missing ones keep the defaults below. Times are in seconds; {@code endSec = -1} means "to the end".
 */
public final class PresetDto {

    public String name = "";
    /** Command literal: {@code play}, {@code playat} or {@code playon}. */
    public String mode = "play";
    public String targets = "@s";
    /** Selector distance in blocks; 0 = no limit. */
    public double distanceBlocks = 0.0;
    /** Sound event id, {@code namespace:path}. */
    public String sound = "";
    /** Category id as in the {@code category} command argument. */
    public String category = "master";
    public float volume = 1.0f;
    public float pitch = 1.0f;
    public double startSec = 0.0;
    public double endSec = -1.0;
    public double fadeInSec = 0.0;
    public double fadeOutSec = 0.0;
    public double x = 0.0;
    public double y = 0.0;
    public double z = 0.0;
}
