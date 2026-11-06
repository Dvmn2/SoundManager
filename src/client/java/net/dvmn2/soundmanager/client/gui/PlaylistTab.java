package net.dvmn2.soundmanager.client.gui;

import net.dvmn2.soundmanager.client.catalog.CatalogLoader;
import net.dvmn2.soundmanager.client.catalog.SoundCatalog;
import net.dvmn2.soundmanager.client.network.PlaylistInfo;
import net.dvmn2.soundmanager.client.network.PlaylistPayload;
import net.dvmn2.soundmanager.client.preset.PresetDto;
import net.dvmn2.soundmanager.client.preset.PresetStore;
import net.dvmn2.soundmanager.client.state.PlaylistState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

/**
 * The Playlists tab of the window: shows the playlists of the server and edits and runs them with
 * {@code /soundmanager playlist ...} commands, so the permission checks of the server apply. Playback is entirely
 * on the server and has nothing to do with the local preview.
 * <p>
 * The tab keeps no data of its own: lists are drawn from {@link PlaylistState}, the selection lives in
 * {@link GuiState}. A new tab object is created every time the screen is built. Client thread only.
 */
final class PlaylistTab {

    /** What the tab needs from the screen that hosts it. */
    interface Host {

        /** Adds a widget to the screen. */
        <T extends ClickableWidget> T add(T widget);

        /** Lets the screen scroll the list with the mouse wheel. */
        void register(PagedButtonList<?> list);

        /** Shows a message in the status line of the screen. */
        void status(String key, int color, Object... args);

        /** The sound catalog, or null while it is loading. */
        @Nullable
        SoundCatalog catalog();

        /** Called after the shared form (the target players) changed. */
        void formChanged();

        /** Rebuilds the screen, e.g. after the set of widgets of the tab changed. */
        void rebuild();
    }

    /** Longest playlist name the server accepts, characters. */
    private static final int NAME_MAX_LENGTH = 40;
    private static final int MAX_TARGETS_LENGTH = 120;
    private static final int PLAYLIST_ROWS = 3;
    private static final int EDIT_BUTTON_COUNT = 4;
    private static final String MINECRAFT_PREFIX = "minecraft:";
    private static final String PLAYLIST_LITERAL = "playlist";
    private static final double MS_PER_SECOND = 1000.0;

    private final Host host;
    private final TextRenderer font;
    private final int leftX;
    private final int rightX;
    private final int columnWidth;
    private final int top;
    private final int bottom;

    @Nullable
    private PagedButtonList<PlaylistInfo> playlistList;
    @Nullable
    private PagedButtonList<Integer> trackList;
    @Nullable
    private PagedButtonList<PresetDto> presetList;
    @Nullable
    private ButtonWidget createButton;
    @Nullable
    private ButtonWidget deleteButton;
    @Nullable
    private ButtonWidget startButton;
    @Nullable
    private ButtonWidget stopButton;
    @Nullable
    private ButtonWidget pauseButton;
    @Nullable
    private ButtonWidget nextButton;
    @Nullable
    private ButtonWidget loopButton;
    @Nullable
    private ButtonWidget shuffleButton;
    @Nullable
    private ButtonWidget upButton;
    @Nullable
    private ButtonWidget downButton;
    @Nullable
    private ButtonWidget removeButton;
    @Nullable
    private ButtonWidget addButton;

    private String newName = "";
    private int shownVersion = -1;
    private int hintY;
    private int tracksTop;
    private boolean allowed;
    @Nullable
    private PendingAdd pending;

    /** A preset waiting for the length of its sound before it can be added to a playlist. */
    private record PendingAdd(String playlist, PresetDto preset, double startSec, CompletableFuture<Long> length) {
    }

    /**
     * @param leftX       left edge of the left column (controls)
     * @param rightX      left edge of the right column (lists)
     * @param columnWidth width of both columns
     * @param top         top of the content area
     * @param bottom      bottom of the content area
     */
    PlaylistTab(Host host, TextRenderer font, int leftX, int rightX, int columnWidth, int top, int bottom) {
        this.host = host;
        this.font = font;
        this.leftX = leftX;
        this.rightX = rightX;
        this.columnWidth = columnWidth;
        this.top = top;
        this.bottom = bottom;
    }

    // ---------- Building ----------

    /** Creates the widgets. */
    void build() {
        allowed = isPlaylistCommandOffered();
        buildControls();
        buildLists();
        shownVersion = PlaylistState.getVersion();
        refreshLists();
        refreshButtons();
    }

    private void buildControls() {
        int half = (columnWidth - SoundManagerScreen.GAP) / 2;
        int secondX = leftX + half + SoundManagerScreen.GAP;
        int y = top;

        TextFieldWidget name = new TextFieldWidget(font, leftX, y, columnWidth, SoundManagerScreen.WIDGET_HEIGHT,
                Text.translatable("soundmanager.gui.playlist.name"));
        name.setPlaceholder(Text.translatable("soundmanager.gui.playlist.name"));
        name.setMaxLength(NAME_MAX_LENGTH);
        name.setText(newName);
        name.setChangedListener(value -> {
            newName = value;
            refreshButtons();
        });
        host.add(name);

        y += SoundManagerScreen.ROW_STEP;
        createButton = button("create", leftX, y, half, this::create);
        deleteButton = button("delete", secondX, y, half, this::delete);

        y += SoundManagerScreen.ROW_STEP;
        TextFieldWidget targets = new TextFieldWidget(font, leftX, y, columnWidth,
                SoundManagerScreen.WIDGET_HEIGHT, Text.translatable("soundmanager.gui.targets"));
        targets.setPlaceholder(Text.translatable("soundmanager.gui.targets"));
        targets.setMaxLength(MAX_TARGETS_LENGTH);
        targets.setText(GuiState.FORM.targets);
        targets.setChangedListener(value -> {
            GuiState.FORM.targets = value;
            host.formChanged();
        });
        host.add(targets);

        y += SoundManagerScreen.ROW_STEP;
        startButton = button("start", leftX, y, half, this::start);
        stopButton = button("stop", secondX, y, half, () -> control("stop"));

        y += SoundManagerScreen.ROW_STEP;
        pauseButton = button("pause", leftX, y, half, this::togglePause);
        nextButton = button("next", secondX, y, half, () -> control("next"));

        y += SoundManagerScreen.ROW_STEP;
        loopButton = button("loop", leftX, y, half, () -> toggle("loop"));
        shuffleButton = button("shuffle", secondX, y, half, () -> toggle("shuffle"));
        hintY = y + SoundManagerScreen.ROW_STEP + 2;
    }

    private void buildLists() {
        int rowHeight = SoundManagerScreen.LIST_ROW_HEIGHT;
        PagedButtonList<PlaylistInfo> playlists = new PagedButtonList<>(font, rightX, top, columnWidth, rowHeight,
                PLAYLIST_ROWS, this::playlistLabel, this::pickPlaylist);
        playlists.addTo(host::add);
        host.register(playlists);
        playlistList = playlists;

        tracksTop = top + PLAYLIST_ROWS * rowHeight + SoundManagerScreen.GAP;
        int buttonsY = bottom - SoundManagerScreen.WIDGET_HEIGHT;
        int rows = Math.max(1, (buttonsY - SoundManagerScreen.GAP - tracksTop) / rowHeight);
        if (GuiState.pickingPreset) {
            PagedButtonList<PresetDto> presets = new PagedButtonList<>(font, rightX, tracksTop, columnWidth,
                    rowHeight, rows, preset -> Text.literal(preset.name), this::addPreset);
            presets.addTo(host::add);
            presets.setItems(PresetStore.list());
            host.register(presets);
            presetList = presets;
            addButton = null;
            button("cancel", rightX, buttonsY, columnWidth, this::cancelPicking);
            return;
        }
        PagedButtonList<Integer> tracks = new PagedButtonList<>(font, rightX, tracksTop, columnWidth, rowHeight,
                rows, this::trackLabel, this::pickTrack);
        tracks.addTo(host::add);
        host.register(tracks);
        trackList = tracks;

        int width = (columnWidth - (EDIT_BUTTON_COUNT - 1) * SoundManagerScreen.GAP) / EDIT_BUTTON_COUNT;
        int step = width + SoundManagerScreen.GAP;
        upButton = button("up", rightX, buttonsY, width, this::moveUp);
        downButton = button("down", rightX + step, buttonsY, width, this::moveDown);
        removeButton = button("remove", rightX + 2 * step, buttonsY, width, this::removeTrack);
        addButton = button("add", rightX + 3 * step, buttonsY, width, this::startPicking);
    }

    private ButtonWidget button(String key, int x, int y, int width, Runnable action) {
        ButtonWidget button = ButtonWidget.builder(Text.translatable("soundmanager.gui.playlist." + key),
                        pressed -> action.run())
                .dimensions(x, y, width, SoundManagerScreen.WIDGET_HEIGHT).build();
        return host.add(button);
    }

    // ---------- Frame ----------

    /** Called every tick: follows updates of the server state and finishes a pending add. */
    void tick() {
        boolean offered = isPlaylistCommandOffered();
        int version = PlaylistState.getVersion();
        if (version != shownVersion || offered != allowed) {
            shownVersion = version;
            allowed = offered;
            refreshLists();
            refreshButtons();
        }
        pollPending();
    }

    /** Draws the hints that are not widgets. */
    void render(DrawContext context) {
        if (!allowed) {
            context.drawTextWithShadow(font, Text.translatable("soundmanager.gui.playlist.no_permission"),
                    leftX, hintY, SoundManagerScreen.COLOR_GRAY);
        }
        PagedButtonList<PlaylistInfo> playlists = playlistList;
        if (playlists != null && playlists.isEmpty()) {
            context.drawTextWithShadow(font, Text.translatable("soundmanager.gui.playlist.none"),
                    rightX, top + 4, SoundManagerScreen.COLOR_GRAY);
        }
        PagedButtonList<PresetDto> presets = presetList;
        PagedButtonList<Integer> tracks = trackList;
        if (presets != null && presets.isEmpty()) {
            context.drawTextWithShadow(font, Text.translatable("soundmanager.gui.preset.empty"),
                    rightX, tracksTop + 4, SoundManagerScreen.COLOR_GRAY);
        } else if (tracks != null && tracks.isEmpty()) {
            String key = selected() == null ? "soundmanager.gui.playlist.select" : "soundmanager.gui.playlist.empty";
            context.drawTextWithShadow(font, Text.translatable(key),
                    rightX, tracksTop + 4, SoundManagerScreen.COLOR_GRAY);
        }
    }

    // ---------- Selection and labels ----------

    @Nullable
    private PlaylistInfo selected() {
        String name = GuiState.selectedPlaylist;
        return name.isEmpty() ? null : PlaylistState.find(name);
    }

    private Text playlistLabel(PlaylistInfo playlist) {
        boolean isSelected = playlist.name().equalsIgnoreCase(GuiState.selectedPlaylist);
        String text = (isSelected ? "> " : "") + playlist.name() + " (" + playlist.tracks().size() + ")";
        if (playlist.state() != PlaylistInfo.RunState.STOPPED) {
            text += " - " + Text.translatable("soundmanager.gui.playlist.state."
                    + playlist.state().name().toLowerCase(Locale.ROOT)).getString();
        }
        return Text.literal(text);
    }

    private Text trackLabel(Integer index) {
        PlaylistInfo playlist = selected();
        if (playlist == null || index >= playlist.tracks().size()) {
            return Text.empty();
        }
        PlaylistInfo.TrackInfo track = playlist.tracks().get(index);
        boolean playing = playlist.state() != PlaylistInfo.RunState.STOPPED && index == playlist.current();
        boolean isSelected = index == GuiState.selectedTrack;
        String marker = (playing ? "*" : "") + (isSelected ? ">" : "") + (playing || isSelected ? " " : "");
        return Text.literal(marker + (index + 1) + ". " + shortSound(track.sound()) + range(track));
    }

    private static String shortSound(String sound) {
        return sound.startsWith(MINECRAFT_PREFIX) ? sound.substring(MINECRAFT_PREFIX.length()) : sound;
    }

    private static String range(PlaylistInfo.TrackInfo track) {
        if (track.startMs() <= 0 && track.endMs() < 0) {
            return "";
        }
        String end = track.endMs() < 0 ? "..." : Timeline.formatTime(track.endMs());
        return " [" + Timeline.formatTime(track.startMs()) + "-" + end + "]";
    }

    private void pickPlaylist(PlaylistInfo playlist) {
        GuiState.selectedPlaylist = playlist.name();
        GuiState.selectedTrack = -1;
        if (GuiState.pickingPreset) {
            GuiState.pickingPreset = false;
            host.rebuild();
            return;
        }
        refreshLists();
        refreshButtons();
    }

    private void pickTrack(Integer index) {
        GuiState.selectedTrack = index;
        refreshLists();
        refreshButtons();
    }

    // ---------- Refresh ----------

    private void refreshLists() {
        PlaylistInfo playlist = selected();
        int size = playlist == null ? 0 : playlist.tracks().size();
        if (GuiState.selectedTrack >= size) {
            GuiState.selectedTrack = -1;
        }
        if (playlistList != null) {
            playlistList.replaceItems(PlaylistState.getAll());
        }
        if (trackList != null) {
            trackList.replaceItems(IntStream.range(0, size).boxed().toList());
        }
    }

    private void refreshButtons() {
        PlaylistInfo playlist = selected();
        boolean has = playlist != null;
        boolean running = has && playlist.state() != PlaylistInfo.RunState.STOPPED;
        boolean editable = allowed && has && !running;
        int size = has ? playlist.tracks().size() : 0;
        int track = GuiState.selectedTrack;

        setActive(createButton, allowed && !newName.isBlank());
        setActive(deleteButton, editable);
        setActive(startButton, allowed && has && size > 0);
        setActive(stopButton, allowed && running);
        setActive(pauseButton, allowed && running);
        setActive(nextButton, allowed && running);
        setActive(loopButton, allowed && has);
        setActive(shuffleButton, allowed && has);
        setActive(upButton, editable && track > 0);
        setActive(downButton, editable && track >= 0 && track < size - 1);
        setActive(removeButton, editable && track >= 0);
        setActive(addButton, editable && size < PlaylistPayload.MAX_TRACKS);

        boolean paused = has && playlist.state() == PlaylistInfo.RunState.PAUSED;
        setMessage(pauseButton, paused ? "resume" : "pause");
        setSwitch(loopButton, "loop", has && playlist.loop());
        setSwitch(shuffleButton, "shuffle", has && playlist.shuffle());
    }

    private static void setActive(@Nullable ButtonWidget button, boolean active) {
        if (button != null) {
            button.active = active;
        }
    }

    private static void setMessage(@Nullable ButtonWidget button, String key) {
        if (button != null) {
            button.setMessage(Text.translatable("soundmanager.gui.playlist." + key));
        }
    }

    private static void setSwitch(@Nullable ButtonWidget button, String key, boolean on) {
        if (button != null) {
            button.setMessage(Text.translatable("soundmanager.gui.playlist." + key,
                    Text.translatable(on ? "options.on" : "options.off")));
        }
    }

    // ---------- Actions ----------

    private void create() {
        String name = newName.strip();
        if (name.isEmpty()) {
            host.status("soundmanager.gui.playlist.bad_name", SoundManagerScreen.COLOR_WARNING, NAME_MAX_LENGTH);
            return;
        }
        if (send(PlaylistCommand.create(name))) {
            GuiState.selectedPlaylist = name;
            GuiState.selectedTrack = -1;
            refreshButtons();
        }
    }

    private void delete() {
        PlaylistInfo playlist = selected();
        if (playlist != null && send(PlaylistCommand.delete(playlist.name()))) {
            GuiState.selectedPlaylist = "";
            GuiState.selectedTrack = -1;
        }
    }

    private void start() {
        PlaylistInfo playlist = selected();
        if (playlist == null) {
            return;
        }
        if (GuiState.FORM.targets.isBlank()) {
            host.status("soundmanager.gui.issue.no_targets", SoundManagerScreen.COLOR_WARNING);
            return;
        }
        send(PlaylistCommand.start(playlist.name(), GuiState.FORM.targets));
    }

    private void control(String verb) {
        PlaylistInfo playlist = selected();
        if (playlist != null) {
            send(PlaylistCommand.control(verb, playlist.name()));
        }
    }

    private void togglePause() {
        PlaylistInfo playlist = selected();
        if (playlist != null) {
            control(playlist.state() == PlaylistInfo.RunState.PAUSED ? "resume" : "pause");
        }
    }

    private void toggle(String option) {
        PlaylistInfo playlist = selected();
        if (playlist == null) {
            return;
        }
        boolean current = "loop".equals(option) ? playlist.loop() : playlist.shuffle();
        send(PlaylistCommand.set(playlist.name(), option, !current));
    }

    private void moveUp() {
        moveSelectedTrack(-1);
    }

    private void moveDown() {
        moveSelectedTrack(1);
    }

    private void moveSelectedTrack(int delta) {
        PlaylistInfo playlist = selected();
        int index = GuiState.selectedTrack;
        if (playlist == null || index < 0) {
            return;
        }
        // The command uses one-based numbers.
        if (send(PlaylistCommand.move(playlist.name(), index + 1, index + 1 + delta))) {
            GuiState.selectedTrack = index + delta;
        }
    }

    private void removeTrack() {
        PlaylistInfo playlist = selected();
        int index = GuiState.selectedTrack;
        if (playlist != null && index >= 0 && send(PlaylistCommand.remove(playlist.name(), index + 1))) {
            GuiState.selectedTrack = -1;
        }
    }

    private void startPicking() {
        GuiState.pickingPreset = true;
        host.rebuild();
    }

    private void cancelPicking() {
        GuiState.pickingPreset = false;
        host.rebuild();
    }

    // ---------- Adding a preset ----------

    private void addPreset(PresetDto preset) {
        PlaylistInfo playlist = selected();
        if (playlist == null) {
            return;
        }
        PlayForm form = new PlayForm();
        form.apply(preset);
        if (form.sound == null) {
            host.status("soundmanager.gui.issue.no_sound", SoundManagerScreen.COLOR_WARNING);
            return;
        }
        if (form.mode == PlayForm.Mode.PLAY_ON) {
            host.status("soundmanager.gui.playlist.playon", SoundManagerScreen.COLOR_WARNING);
            return;
        }
        if (!form.toEnd) {
            sendAdd(playlist.name(), preset, -1.0);
            return;
        }
        if (pending != null) {
            host.status("soundmanager.gui.playlist.busy", SoundManagerScreen.COLOR_WARNING);
            return;
        }
        Identifier file = singleFile(form.sound);
        if (file == null) {
            host.status("soundmanager.gui.playlist.needs_end", SoundManagerScreen.COLOR_WARNING);
            return;
        }
        pending = new PendingAdd(playlist.name(), preset, form.startSec,
                CatalogLoader.loadDuration(MinecraftClient.getInstance().getResourceManager(), file));
        host.status("soundmanager.gui.sound.reading", SoundManagerScreen.COLOR_GRAY);
    }

    /** The only sound file of the event; null if the catalog is not loaded or the event has several files. */
    @Nullable
    private Identifier singleFile(Identifier event) {
        SoundCatalog catalog = host.catalog();
        if (catalog == null || catalog.variants(event) != 1) {
            return null;
        }
        return catalog.resolveFile(event);
    }

    private void pollPending() {
        PendingAdd waiting = pending;
        if (waiting == null || !waiting.length().isDone()) {
            return;
        }
        pending = null;
        long lengthMs = waiting.length().getNow(-1L);
        if (lengthMs <= Numbers.toMillis(waiting.startSec())) {
            host.status("soundmanager.gui.playlist.needs_end", SoundManagerScreen.COLOR_WARNING);
            return;
        }
        sendAdd(waiting.playlist(), waiting.preset(), lengthMs / MS_PER_SECOND);
    }

    private void sendAdd(String playlist, PresetDto preset, double lengthSec) {
        String command = PlaylistCommand.add(playlist, preset, lengthSec);
        if (command.length() > PlayCommand.MAX_LENGTH) {
            host.status("soundmanager.gui.issue.too_long", SoundManagerScreen.COLOR_WARNING, PlayCommand.MAX_LENGTH);
            return;
        }
        if (send(command) && GuiState.pickingPreset) {
            GuiState.pickingPreset = false;
            host.rebuild();
        }
    }

    // ---------- Server ----------

    private boolean send(String command) {
        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null) {
            host.status("soundmanager.gui.status.no_connection", SoundManagerScreen.COLOR_ERROR);
            return false;
        }
        handler.sendChatCommand(command);
        host.status("soundmanager.gui.status.sent", SoundManagerScreen.COLOR_OK);
        return true;
    }

    /** Whether the server offered the playlist commands to this player (no node = no permission or no plugin). */
    private static boolean isPlaylistCommandOffered() {
        ClientPlayNetworkHandler handler = MinecraftClient.getInstance().getNetworkHandler();
        if (handler == null) {
            return false;
        }
        var base = handler.getCommandDispatcher().getRoot().getChild(SoundManagerScreen.SERVER_COMMAND_ROOT);
        return base != null && base.getChild(PLAYLIST_LITERAL) != null;
    }
}
