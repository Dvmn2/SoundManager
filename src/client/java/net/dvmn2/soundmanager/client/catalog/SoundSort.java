package net.dvmn2.soundmanager.client.catalog;

/**
 * Order of the sound list in the window. "Path" sorts by the name after the colon (then by namespace), "namespace"
 * groups by the part before the colon (then by name).
 */
public enum SoundSort {
    PATH_ASC, PATH_DESC, NAMESPACE_ASC, NAMESPACE_DESC;

    /** The next order in the cycle of the sort button. */
    public SoundSort next() {
        SoundSort[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public boolean isByNamespace() {
        return this == NAMESPACE_ASC || this == NAMESPACE_DESC;
    }

    public boolean isDescending() {
        return this == PATH_DESC || this == NAMESPACE_DESC;
    }
}
