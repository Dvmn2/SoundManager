package net.dvmn2.soundmanager.client.gui;

import com.mojang.brigadier.tree.CommandNode;
import net.dvmn2.soundmanager.client.catalog.CatalogLoader;
import net.dvmn2.soundmanager.client.catalog.SoundCatalog;
import net.dvmn2.soundmanager.client.preset.PresetDto;
import net.dvmn2.soundmanager.client.preset.PresetStore;
import net.dvmn2.soundmanager.client.sound.PlayParams;
import net.dvmn2.soundmanager.client.sound.PreviewController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleConsumer;

/**
 * The window for configuring a sound and previewing it before sending it to other players.
 * <p>
 * Shows every argument of {@code /soundmanager play|playat|playon}, previews the result locally through
 * {@link PreviewController}, and sends the finished command as a chat command, so the server's own permission
 * checks apply. The values live in {@link GuiState}. Client thread only.
 */
public final class SoundManagerScreen extends Screen {

    private static final String SERVER_COMMAND_ROOT = "soundmanager";

    private static final int MARGIN = 10;
    private static final int GAP = 4;
    private static final int TITLE_Y = 8;
    private static final int TABS_Y = 20;
    private static final int CONTENT_TOP = 46;
    private static final int BOTTOM_HEIGHT = 80;
    private static final int WIDGET_HEIGHT = 20;
    private static final int ROW_STEP = 22;
    private static final int LIST_ROW_HEIGHT = 16;
    private static final int INFO_HEIGHT = 12;
    private static final int RANGE_TEXT_WIDTH = 70;
    private static final int QUICK_BUTTON_WIDTH = 28;
    private static final int BOTTOM_BUTTON_COUNT = 6;
    private static final int COMMAND_Y_OFFSET = 78;
    private static final int TIMELINE_Y_OFFSET = 66;
    private static final int STATUS_Y_OFFSET = 36;
    private static final int BUTTONS_Y_OFFSET = 24;

    private static final int MAX_SEARCH_LENGTH = 64;
    private static final int MAX_TARGETS_LENGTH = 120;
    private static final double DEFAULT_SLIDER_SECONDS = 300.0;
    private static final double DEFAULT_FADE_SLIDER_SECONDS = 30.0;
    private static final double MAX_FADE_SLIDER_SECONDS = 60.0;
    private static final double MS_PER_SECOND = 1000.0;
    private static final double ENTITY_RADIUS = 64.0;
    private static final int MAX_ENTITIES = 64;
    private static final int STATUS_TICKS = 80;

    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_GRAY = 0xFFAAAAAA;
    private static final int COLOR_WARNING = 0xFFFFAA00;
    private static final int COLOR_ERROR = 0xFFFF5555;
    private static final int COLOR_OK = 0xFF55FF55;

    private final PlayForm form = GuiState.FORM;
    private final Timeline timeline = new Timeline();
    private final List<PagedButtonList<?>> lists = new ArrayList<>();

    private Tab tab = Tab.SOUND;
    @Nullable
    private SoundCatalog catalog;
    @Nullable
    private CompletableFuture<SoundCatalog> catalogFuture;
    @Nullable
    private CompletableFuture<Long> durationFuture;
    private long durationMs = -1L;
    private int variantCount;
    private String soundFilter = "";
    private List<PlayForm.EntityRef> nearbyEntities = List.of();
    private String presetName = "";
    @Nullable
    private String selectedPreset;

    private String command = "";
    private String commandLine = "";
    private PlayForm.Issue issue = PlayForm.Issue.NONE;
    private boolean sendAllowed;
    private PreviewState previewState = PreviewState.STOPPED;
    private Text idleStatus = Text.empty();
    private int idleColor = COLOR_GRAY;
    private Text status = Text.empty();
    private int statusColor = COLOR_GRAY;
    private int statusTicks;
    private Text soundInfoLeft = Text.empty();
    private Text soundInfoRight = Text.empty();

    @Nullable
    private PagedButtonList<Identifier> soundList;
    @Nullable
    private PagedButtonList<PresetDto> presetList;
    @Nullable
    private PagedButtonList<PlayForm.EntityRef> entityList;
    @Nullable
    private ValueRow startRow;
    @Nullable
    private ValueRow endRow;
    @Nullable
    private ValueRow fadeInRow;
    @Nullable
    private ValueRow fadeOutRow;
    @Nullable
    private ButtonWidget pauseButton;
    @Nullable
    private ButtonWidget stopButton;
    @Nullable
    private ButtonWidget sendButton;

    private SoundManagerScreen(MinecraftClient client) {
        super(Text.translatable("soundmanager.gui.title"));
        this.catalogFuture = CatalogLoader.loadCatalog(client.getResourceManager());
    }

    /** Opens the window. */
    public static void open(MinecraftClient client) {
        client.setScreen(new SoundManagerScreen(client));
    }

    private enum Tab {
        SOUND("sound"), PARAMS("params"), TARGET("target"), PRESETS("presets");

        private final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private enum PreviewState { STOPPED, PLAYING, PAUSED }

    // ---------- Screen lifecycle ----------

    @Override
    protected void init() {
        lists.clear();
        soundList = null;
        presetList = null;
        entityList = null;
        startRow = null;
        endRow = null;
        fadeInRow = null;
        fadeOutRow = null;

        addTabButtons();
        switch (tab) {
            case SOUND -> addSoundTab();
            case PARAMS -> addParamsTab();
            case TARGET -> addTargetTab();
            case PRESETS -> addPresetsTab();
        }
        addBottomButtons();
        refreshSoundInfo();
        onFormChanged();
        refreshPreviewButtons();
    }

    @Override
    public void tick() {
        super.tick();
        pollFutures();
        updatePreviewState();
        boolean allowed = canSend();
        if (allowed != sendAllowed) {
            sendAllowed = allowed;
            refreshSendButton();
            refreshIdleStatus();
        }
        if (statusTicks > 0) {
            statusTicks--;
        }
    }

    @Override
    public void removed() {
        PreviewController.stop();
        super.removed();
    }

    /** The game keeps running while the window is open, otherwise a preview in single player would freeze. */
    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        for (PagedButtonList<?> list : lists) {
            if (list.contains(mouseX, mouseY)) {
                list.scroll(vertical);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, getTitle(), width / 2, TITLE_Y, COLOR_TEXT);
        renderTabDetails(context);
        renderBottom(context);
    }

    private void renderTabDetails(DrawContext context) {
        int contentBottom = height - BOTTOM_HEIGHT;
        switch (tab) {
            case SOUND -> {
                PagedButtonList<Identifier> list = soundList;
                if (list != null) {
                    String range = list.rangeText();
                    context.drawTextWithShadow(textRenderer, range,
                            width - MARGIN - textRenderer.getWidth(range), CONTENT_TOP + 6, COLOR_GRAY);
                    if (list.isEmpty()) {
                        Text empty = Text.translatable(catalog == null
                                ? "soundmanager.gui.sound.loading" : "soundmanager.gui.sound.none_found");
                        context.drawTextWithShadow(textRenderer, empty, MARGIN, CONTENT_TOP + ROW_STEP + 4,
                                COLOR_GRAY);
                    }
                }
                int infoY = contentBottom - INFO_HEIGHT + 2;
                context.drawTextWithShadow(textRenderer, soundInfoLeft, MARGIN, infoY, COLOR_TEXT);
                context.drawTextWithShadow(textRenderer, soundInfoRight,
                        width - MARGIN - textRenderer.getWidth(soundInfoRight), infoY, COLOR_GRAY);
            }
            case TARGET -> {
                int x = rightColumnX();
                if (form.mode == PlayForm.Mode.PLAY) {
                    context.drawTextWithShadow(textRenderer, Text.translatable("soundmanager.gui.mode.play_hint"),
                            x, CONTENT_TOP + 6, COLOR_GRAY);
                } else if (form.mode == PlayForm.Mode.PLAY_ON && entityList != null && entityList.isEmpty()) {
                    context.drawTextWithShadow(textRenderer, Text.translatable("soundmanager.gui.entity.none"),
                            x, CONTENT_TOP + ROW_STEP + 4, COLOR_GRAY);
                }
            }
            case PRESETS -> {
                if (presetList != null && presetList.isEmpty()) {
                    context.drawTextWithShadow(textRenderer, Text.translatable("soundmanager.gui.preset.empty"),
                            rightColumnX(), CONTENT_TOP + 6, COLOR_GRAY);
                }
            }
            case PARAMS -> {
                // Everything on this tab is made of widgets.
            }
        }
    }

    private void renderBottom(DrawContext context) {
        context.drawTextWithShadow(textRenderer, commandLine, MARGIN, height - COMMAND_Y_OFFSET, COLOR_GRAY);
        timeline.draw(context, textRenderer, MARGIN, height - TIMELINE_Y_OFFSET, width - 2 * MARGIN,
                form, durationMs, PreviewController.positionMs());
        Text line = statusTicks > 0 ? status : idleStatus;
        int color = statusTicks > 0 ? statusColor : idleColor;
        context.drawTextWithShadow(textRenderer, line, MARGIN, height - STATUS_Y_OFFSET, color);
    }

    // ---------- Layout ----------

    private int columnWidth() {
        return (width - 2 * MARGIN - GAP) / 2;
    }

    private int rightColumnX() {
        return MARGIN + columnWidth() + GAP;
    }

    private int listRows(int top) {
        return Math.max(1, (height - BOTTOM_HEIGHT - top) / LIST_ROW_HEIGHT);
    }

    private void addTabButtons() {
        Tab[] tabs = Tab.values();
        int tabWidth = (width - 2 * MARGIN - (tabs.length - 1) * GAP) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            Tab target = tabs[i];
            ButtonWidget button = ButtonWidget.builder(Text.translatable("soundmanager.gui.tab." + target.key),
                            pressed -> {
                                tab = target;
                                clearAndInit();
                            })
                    .dimensions(MARGIN + i * (tabWidth + GAP), TABS_Y, tabWidth, WIDGET_HEIGHT)
                    .build();
            button.active = target != tab;
            addDrawableChild(button);
        }
    }

    // ---------- Tab: sound ----------

    private void addSoundTab() {
        int fullWidth = width - 2 * MARGIN;
        TextFieldWidget search = new TextFieldWidget(textRenderer, MARGIN, CONTENT_TOP,
                fullWidth - RANGE_TEXT_WIDTH - GAP, WIDGET_HEIGHT, Text.translatable("soundmanager.gui.search"));
        search.setPlaceholder(Text.translatable("soundmanager.gui.search"));
        search.setMaxLength(MAX_SEARCH_LENGTH);
        search.setText(soundFilter);
        search.setChangedListener(value -> {
            soundFilter = value;
            applySoundFilter();
        });
        addDrawableChild(search);

        int listTop = CONTENT_TOP + ROW_STEP;
        int rows = listRows(listTop + INFO_HEIGHT);
        PagedButtonList<Identifier> list = new PagedButtonList<>(textRenderer, MARGIN, listTop, fullWidth,
                LIST_ROW_HEIGHT, rows, this::soundLabel, this::pickSound);
        list.addTo(this::addDrawableChild);
        lists.add(list);
        soundList = list;
        applySoundFilter();
    }

    private Text soundLabel(Identifier id) {
        String name = "minecraft".equals(id.getNamespace()) ? id.getPath() : id.toString();
        return Text.literal((id.equals(form.sound) ? "> " : "") + name);
    }

    private void pickSound(Identifier id) {
        form.sound = id;
        if (soundList != null) {
            soundList.refresh();
        }
        loadDuration();
        onFormChanged();
    }

    private void applySoundFilter() {
        SoundCatalog current = catalog;
        if (soundList != null) {
            soundList.setItems(current == null ? List.of() : current.search(soundFilter));
        }
    }

    /** Starts reading the length of the selected sound; the answer arrives in {@link #pollFutures()}. */
    private void loadDuration() {
        durationMs = -1L;
        variantCount = 0;
        durationFuture = null;
        Identifier sound = form.sound;
        if (sound != null && catalog != null) {
            variantCount = catalog.variants(sound);
            Identifier file = catalog.resolveFile(sound);
            if (file != null) {
                durationFuture = CatalogLoader.loadDuration(MinecraftClient.getInstance().getResourceManager(), file);
            }
        }
        applyDurationToRows();
        refreshSoundInfo();
    }

    private void applyDurationToRows() {
        double timeMax = durationMs > 0 ? durationMs / MS_PER_SECOND : DEFAULT_SLIDER_SECONDS;
        double fadeMax = durationMs > 0
                ? Math.min(durationMs / MS_PER_SECOND, MAX_FADE_SLIDER_SECONDS) : DEFAULT_FADE_SLIDER_SECONDS;
        if (startRow != null) {
            startRow.setSliderMax(timeMax);
        }
        if (endRow != null) {
            endRow.setSliderMax(timeMax);
        }
        if (fadeInRow != null) {
            fadeInRow.setSliderMax(fadeMax);
        }
        if (fadeOutRow != null) {
            fadeOutRow.setSliderMax(fadeMax);
        }
    }

    private void refreshSoundInfo() {
        Identifier sound = form.sound;
        soundInfoLeft = sound == null
                ? Text.translatable("soundmanager.gui.sound.not_selected")
                : Text.literal(textRenderer.trimToWidth(sound.toString(), (width - 2 * MARGIN) * 3 / 5));
        if (catalog == null) {
            soundInfoRight = Text.empty();
        } else if (sound == null) {
            soundInfoRight = Text.empty();
        } else if (durationFuture != null) {
            soundInfoRight = Text.translatable("soundmanager.gui.sound.reading");
        } else {
            Text length = durationMs > 0
                    ? Text.translatable("soundmanager.gui.sound.length", Timeline.formatTime(durationMs))
                    : Text.translatable("soundmanager.gui.sound.length_unknown");
            soundInfoRight = variantCount > 1
                    ? Text.translatable("soundmanager.gui.sound.variants", length, variantCount)
                    : length;
        }
    }

    // ---------- Tab: parameters ----------

    private void addParamsTab() {
        int columnWidth = columnWidth();
        int leftX = MARGIN;
        int rightX = rightColumnX();
        int y = CONTENT_TOP;

        ButtonWidget categoryButton = ButtonWidget.builder(categoryText(), pressed -> {
            form.cycleCategory();
            pressed.setMessage(categoryText());
            onFormChanged();
        }).dimensions(leftX, y, columnWidth, WIDGET_HEIGHT).build();
        addDrawableChild(categoryButton);

        ValueRow volume = new ValueRow(textRenderer, leftX, y + ROW_STEP, columnWidth,
                Text.translatable("soundmanager.gui.volume"),
                0.0, PlayForm.MAX_VOLUME, PlayForm.MAX_VOLUME, form.volume, value -> {
                    form.volume = (float) value;
                    onFormChanged();
                });
        volume.addTo(this::addDrawableChild);

        ValueRow pitch = new ValueRow(textRenderer, leftX, y + 2 * ROW_STEP, columnWidth,
                Text.translatable("soundmanager.gui.pitch"),
                PlayParams.MIN_PITCH, PlayParams.MAX_PITCH, PlayParams.MAX_PITCH, form.pitch, value -> {
                    form.pitch = (float) value;
                    onFormChanged();
                });
        pitch.addTo(this::addDrawableChild);

        ButtonWidget toEndButton = ButtonWidget.builder(toEndText(), pressed -> {
            form.toEnd = !form.toEnd;
            pressed.setMessage(toEndText());
            if (endRow != null) {
                endRow.setActive(!form.toEnd);
            }
            onFormChanged();
        }).dimensions(leftX, y + 3 * ROW_STEP, columnWidth, WIDGET_HEIGHT).build();
        addDrawableChild(toEndButton);

        startRow = new ValueRow(textRenderer, rightX, y, columnWidth, Text.translatable("soundmanager.gui.start"),
                0.0, DEFAULT_SLIDER_SECONDS, PlayForm.MAX_TIME_SEC, form.startSec, value -> {
                    form.startSec = value;
                    onFormChanged();
                });
        endRow = new ValueRow(textRenderer, rightX, y + ROW_STEP, columnWidth,
                Text.translatable("soundmanager.gui.end"),
                0.0, DEFAULT_SLIDER_SECONDS, PlayForm.MAX_TIME_SEC, form.endSec, value -> {
                    form.endSec = value;
                    onFormChanged();
                });
        endRow.setActive(!form.toEnd);
        fadeInRow = new ValueRow(textRenderer, rightX, y + 2 * ROW_STEP, columnWidth,
                Text.translatable("soundmanager.gui.fade_in"),
                0.0, DEFAULT_FADE_SLIDER_SECONDS, PlayForm.MAX_FADE_SEC, form.fadeInSec, value -> {
                    form.fadeInSec = value;
                    onFormChanged();
                });
        fadeOutRow = new ValueRow(textRenderer, rightX, y + 3 * ROW_STEP, columnWidth,
                Text.translatable("soundmanager.gui.fade_out"),
                0.0, DEFAULT_FADE_SLIDER_SECONDS, PlayForm.MAX_FADE_SEC, form.fadeOutSec, value -> {
                    form.fadeOutSec = value;
                    onFormChanged();
                });
        startRow.addTo(this::addDrawableChild);
        endRow.addTo(this::addDrawableChild);
        fadeInRow.addTo(this::addDrawableChild);
        fadeOutRow.addTo(this::addDrawableChild);
        applyDurationToRows();
    }

    private Text categoryText() {
        String name = form.category.getName();
        return Text.translatable("soundmanager.gui.category",
                Text.translatableWithFallback("soundCategory." + name, name));
    }

    private Text toEndText() {
        return Text.translatable("soundmanager.gui.to_end",
                Text.translatable(form.toEnd ? "options.on" : "options.off"));
    }

    // ---------- Tab: target ----------

    private void addTargetTab() {
        int columnWidth = columnWidth();
        int leftX = MARGIN;
        int y = CONTENT_TOP;

        ButtonWidget modeButton = ButtonWidget.builder(modeText(), pressed -> {
            form.mode = form.mode.next();
            if (form.mode == PlayForm.Mode.PLAY_ON) {
                refreshEntities();
            }
            clearAndInit();
        }).dimensions(leftX, y, columnWidth, WIDGET_HEIGHT).build();
        addDrawableChild(modeButton);

        int fieldWidth = columnWidth - 2 * (QUICK_BUTTON_WIDTH + 2);
        TextFieldWidget targets = new TextFieldWidget(textRenderer, leftX, y + ROW_STEP, fieldWidth, WIDGET_HEIGHT,
                Text.translatable("soundmanager.gui.targets"));
        targets.setPlaceholder(Text.translatable("soundmanager.gui.targets"));
        targets.setMaxLength(MAX_TARGETS_LENGTH);
        targets.setText(form.targets);
        targets.setChangedListener(value -> {
            form.targets = value;
            onFormChanged();
        });
        addDrawableChild(targets);
        addQuickTarget(targets, "@s", leftX + fieldWidth + 2, y + ROW_STEP);
        addQuickTarget(targets, "@a", leftX + fieldWidth + 2 + QUICK_BUTTON_WIDTH + 2, y + ROW_STEP);

        int playersTop = y + 2 * ROW_STEP;
        PagedButtonList<String> players = new PagedButtonList<>(textRenderer, leftX, playersTop, columnWidth,
                LIST_ROW_HEIGHT, listRows(playersTop), Text::literal, targets::setText);
        players.addTo(this::addDrawableChild);
        players.setItems(playerNames());
        lists.add(players);

        switch (form.mode) {
            case PLAY_AT -> addPositionControls();
            case PLAY_ON -> addEntityControls();
            case PLAY -> {
                // The global variant has no extra controls; a hint is drawn instead.
            }
        }
    }

    private void addQuickTarget(TextFieldWidget field, String selector, int x, int y) {
        addDrawableChild(ButtonWidget.builder(Text.literal(selector), pressed -> field.setText(selector))
                .dimensions(x, y, QUICK_BUTTON_WIDTH, WIDGET_HEIGHT).build());
    }

    private Text modeText() {
        return Text.translatable("soundmanager.gui.mode", Text.translatable("soundmanager.gui.mode."
                + form.mode.literal()));
    }

    private List<String> playerNames() {
        ClientPlayNetworkHandler handler = client == null ? null : client.getNetworkHandler();
        if (handler == null) {
            return List.of();
        }
        List<String> names = new ArrayList<>(handler.getCommandSource().getPlayerNames());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private void addPositionControls() {
        int x = rightColumnX();
        int y = CONTENT_TOP;
        int fieldWidth = (columnWidth() - 2 * GAP) / 3;
        TextFieldWidget fieldX = coordinateField(x, y, fieldWidth, "X", form.x, value -> form.x = value);
        TextFieldWidget fieldY = coordinateField(x + fieldWidth + GAP, y, fieldWidth, "Y", form.y,
                value -> form.y = value);
        TextFieldWidget fieldZ = coordinateField(x + 2 * (fieldWidth + GAP), y, fieldWidth, "Z", form.z,
                value -> form.z = value);
        addDrawableChild(ButtonWidget.builder(Text.translatable("soundmanager.gui.position.here"), pressed -> {
            ClientPlayerEntity player = client == null ? null : client.player;
            if (player != null) {
                fieldX.setText(Numbers.format(player.getX()));
                fieldY.setText(Numbers.format(player.getY()));
                fieldZ.setText(Numbers.format(player.getZ()));
            }
        }).dimensions(x, y + ROW_STEP, columnWidth(), WIDGET_HEIGHT).build());
    }

    private TextFieldWidget coordinateField(int x, int y, int fieldWidth, String name, double initial,
                                            DoubleConsumer setter) {
        TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, fieldWidth, WIDGET_HEIGHT, Text.literal(name));
        field.setPlaceholder(Text.literal(name));
        field.setMaxLength(16);
        field.setTextPredicate(text -> text.matches("-?[0-9]*[.,]?[0-9]*"));
        field.setText(Numbers.format(initial));
        field.setChangedListener(text -> {
            double parsed = Numbers.parse(text);
            if (!Double.isNaN(parsed)) {
                setter.accept(Numbers.clamp(parsed, -PlayForm.MAX_COORDINATE, PlayForm.MAX_COORDINATE));
                onFormChanged();
            }
        });
        addDrawableChild(field);
        return field;
    }

    private void addEntityControls() {
        int x = rightColumnX();
        int listTop = CONTENT_TOP + ROW_STEP;
        PagedButtonList<PlayForm.EntityRef> list = new PagedButtonList<>(textRenderer, x, listTop, columnWidth(),
                LIST_ROW_HEIGHT, listRows(listTop), this::entityLabel, this::pickEntity);
        entityList = list;
        if (nearbyEntities.isEmpty()) {
            refreshEntities();
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("soundmanager.gui.entity.refresh"), pressed -> {
            refreshEntities();
            list.setItems(nearbyEntities);
        }).dimensions(x, CONTENT_TOP, columnWidth(), WIDGET_HEIGHT).build());
        list.addTo(this::addDrawableChild);
        list.setItems(nearbyEntities);
        lists.add(list);
    }

    private Text entityLabel(PlayForm.EntityRef ref) {
        PlayForm.EntityRef chosen = form.entity;
        boolean selected = chosen != null && chosen.uuid().equals(ref.uuid());
        return Text.literal((selected ? "> " : "") + ref.label());
    }

    private void pickEntity(PlayForm.EntityRef ref) {
        form.entity = ref;
        if (entityList != null) {
            entityList.refresh();
        }
        onFormChanged();
    }

    private void refreshEntities() {
        ClientWorld world = client == null ? null : client.world;
        ClientPlayerEntity player = client == null ? null : client.player;
        if (world == null || player == null) {
            nearbyEntities = List.of();
            return;
        }
        List<PlayForm.EntityRef> found = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            double distance = entity.distanceTo(player);
            if (distance > ENTITY_RADIUS) {
                continue;
            }
            String label = Text.translatable("soundmanager.gui.entity.label", entity.getName().getString(),
                    String.valueOf(Math.round(distance))).getString();
            found.add(new PlayForm.EntityRef(entity.getId(), entity.getUuidAsString(), label,
                    entity.getX(), entity.getY(), entity.getZ(), distance));
        }
        found.sort(Comparator.comparingDouble(PlayForm.EntityRef::distance));
        nearbyEntities = found.size() > MAX_ENTITIES ? List.copyOf(found.subList(0, MAX_ENTITIES)) : found;
    }

    // ---------- Tab: presets ----------

    private void addPresetsTab() {
        int columnWidth = columnWidth();
        int leftX = MARGIN;
        int y = CONTENT_TOP;

        TextFieldWidget name = new TextFieldWidget(textRenderer, leftX, y, columnWidth, WIDGET_HEIGHT,
                Text.translatable("soundmanager.gui.preset.name"));
        name.setPlaceholder(Text.translatable("soundmanager.gui.preset.name"));
        name.setMaxLength(PresetStore.MAX_NAME_LENGTH);
        name.setText(presetName);
        name.setChangedListener(value -> presetName = value);
        addDrawableChild(name);

        int listX = rightColumnX();
        PagedButtonList<PresetDto> list = new PagedButtonList<>(textRenderer, listX, y, columnWidth,
                LIST_ROW_HEIGHT, listRows(y), this::presetLabel, preset -> loadPreset(preset, name));
        list.addTo(this::addDrawableChild);
        list.setItems(PresetStore.list());
        lists.add(list);
        presetList = list;

        addDrawableChild(ButtonWidget.builder(Text.translatable("soundmanager.gui.preset.save"),
                        pressed -> savePreset())
                .dimensions(leftX, y + ROW_STEP, columnWidth, WIDGET_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("soundmanager.gui.preset.delete"),
                        pressed -> deletePreset())
                .dimensions(leftX, y + 2 * ROW_STEP, columnWidth, WIDGET_HEIGHT).build());
    }

    private Text presetLabel(PresetDto preset) {
        return Text.literal((preset.name.equals(selectedPreset) ? "> " : "") + preset.name);
    }

    private void loadPreset(PresetDto preset, TextFieldWidget nameField) {
        form.apply(preset);
        selectedPreset = preset.name;
        nameField.setText(preset.name);
        if (presetList != null) {
            presetList.refresh();
        }
        loadDuration();
        onFormChanged();
        setStatus("soundmanager.gui.preset.loaded", COLOR_OK);
    }

    private void savePreset() {
        if (form.sound == null) {
            setStatus("soundmanager.gui.issue.no_sound", COLOR_WARNING);
            return;
        }
        PresetDto preset = form.toPreset(presetName);
        PresetStore.SaveResult result = PresetStore.save(preset);
        switch (result) {
            case SAVED -> setStatus("soundmanager.gui.preset.saved", COLOR_OK);
            case REPLACED -> setStatus("soundmanager.gui.preset.replaced", COLOR_OK);
            case BAD_NAME -> setStatus("soundmanager.gui.preset.bad_name", COLOR_WARNING,
                    PresetStore.MAX_NAME_LENGTH);
            case LIMIT_REACHED -> setStatus("soundmanager.gui.preset.limit", COLOR_WARNING,
                    PresetStore.MAX_PRESETS);
            case WRITE_FAILED -> setStatus("soundmanager.gui.preset.write_failed", COLOR_ERROR);
        }
        if (result == PresetStore.SaveResult.SAVED || result == PresetStore.SaveResult.REPLACED) {
            selectedPreset = preset.name;
            if (presetList != null) {
                presetList.setItems(PresetStore.list());
            }
        }
    }

    private void deletePreset() {
        String name = selectedPreset;
        if (name != null && PresetStore.delete(name)) {
            selectedPreset = null;
            if (presetList != null) {
                presetList.setItems(PresetStore.list());
            }
            setStatus("soundmanager.gui.preset.deleted", COLOR_OK);
        }
    }

    // ---------- Bottom bar ----------

    private void addBottomButtons() {
        int buttonWidth = (width - 2 * MARGIN - (BOTTOM_BUTTON_COUNT - 1) * GAP) / BOTTOM_BUTTON_COUNT;
        int y = height - BUTTONS_Y_OFFSET;
        int index = 0;

        addDrawableChild(ButtonWidget.builder(Text.translatable("soundmanager.gui.preview"), pressed -> preview())
                .dimensions(bottomX(index++, buttonWidth), y, buttonWidth, WIDGET_HEIGHT).build());
        pauseButton = ButtonWidget.builder(Text.translatable("soundmanager.gui.pause"), pressed -> togglePause())
                .dimensions(bottomX(index++, buttonWidth), y, buttonWidth, WIDGET_HEIGHT).build();
        addDrawableChild(pauseButton);
        stopButton = ButtonWidget.builder(Text.translatable("soundmanager.gui.stop"), pressed -> {
            PreviewController.stop();
            updatePreviewState();
        }).dimensions(bottomX(index++, buttonWidth), y, buttonWidth, WIDGET_HEIGHT).build();
        addDrawableChild(stopButton);
        addDrawableChild(ButtonWidget.builder(Text.translatable("soundmanager.gui.copy"), pressed -> copyCommand())
                .dimensions(bottomX(index++, buttonWidth), y, buttonWidth, WIDGET_HEIGHT).build());
        sendButton = ButtonWidget.builder(Text.translatable("soundmanager.gui.send"), pressed -> sendCommand())
                .dimensions(bottomX(index++, buttonWidth), y, buttonWidth, WIDGET_HEIGHT).build();
        addDrawableChild(sendButton);
        addDrawableChild(ButtonWidget.builder(Text.translatable("soundmanager.gui.close"), pressed -> close())
                .dimensions(bottomX(index, buttonWidth), y, buttonWidth, WIDGET_HEIGHT).build());
    }

    private static int bottomX(int index, int buttonWidth) {
        return MARGIN + index * (buttonWidth + GAP);
    }

    // ---------- Actions ----------

    private void preview() {
        PlayForm.Issue previewIssue = form.check(false, 0);
        if (previewIssue != PlayForm.Issue.NONE) {
            setStatus(issueKey(previewIssue), COLOR_WARNING);
            return;
        }
        reportStart(PreviewController.start(form.toParams()));
    }

    private void togglePause() {
        if (PreviewController.isPlaying()) {
            PreviewController.pause();
        } else if (PreviewController.isPaused()) {
            reportStart(PreviewController.resume());
        }
        updatePreviewState();
    }

    private void reportStart(PreviewController.StartResult result) {
        switch (result) {
            case STARTED, NOTHING_TO_RESUME -> {
                // The state line shows playing or paused; nothing to report.
            }
            case DISABLED -> setStatus("soundmanager.gui.status.disabled", COLOR_WARNING);
            case SEEK_UNAVAILABLE -> setStatus("soundmanager.gui.status.seek_unavailable", COLOR_WARNING);
        }
        updatePreviewState();
    }

    private void copyCommand() {
        if (client == null) {
            return;
        }
        client.keyboard.setClipboard("/" + command);
        setStatus("soundmanager.gui.status.copied", COLOR_OK);
    }

    private void sendCommand() {
        ClientPlayNetworkHandler handler = client == null ? null : client.getNetworkHandler();
        if (handler == null) {
            setStatus("soundmanager.gui.status.no_connection", COLOR_ERROR);
            return;
        }
        if (issue != PlayForm.Issue.NONE || !canSend()) {
            return;
        }
        handler.sendChatCommand(command);
        setStatus("soundmanager.gui.status.sent", COLOR_OK);
    }

    /**
     * Whether the server offered the command to this player. The server sends only the commands the player may
     * use, so a missing node means no permission (or no plugin).
     */
    private boolean canSend() {
        ClientPlayNetworkHandler handler = client == null ? null : client.getNetworkHandler();
        if (handler == null) {
            return false;
        }
        CommandNode<ClientCommandSource> root = handler.getCommandDispatcher().getRoot();
        CommandNode<ClientCommandSource> base = root.getChild(SERVER_COMMAND_ROOT);
        return base != null && base.getChild(form.mode.literal()) != null;
    }

    // ---------- State ----------

    private void pollFutures() {
        CompletableFuture<SoundCatalog> catalogLoad = catalogFuture;
        if (catalogLoad != null && catalogLoad.isDone()) {
            catalogFuture = null;
            catalog = catalogLoad.getNow(SoundCatalog.empty());
            applySoundFilter();
            loadDuration();
        }
        CompletableFuture<Long> durationLoad = durationFuture;
        if (durationLoad != null && durationLoad.isDone()) {
            durationFuture = null;
            durationMs = durationLoad.getNow(-1L);
            applyDurationToRows();
            refreshSoundInfo();
        }
    }

    private void updatePreviewState() {
        PreviewState now = PreviewController.isPlaying() ? PreviewState.PLAYING
                : PreviewController.isPaused() ? PreviewState.PAUSED : PreviewState.STOPPED;
        if (now != previewState) {
            previewState = now;
            refreshPreviewButtons();
            refreshIdleStatus();
        }
    }

    private void refreshPreviewButtons() {
        if (pauseButton != null) {
            pauseButton.setMessage(Text.translatable(previewState == PreviewState.PAUSED
                    ? "soundmanager.gui.resume" : "soundmanager.gui.pause"));
            pauseButton.active = previewState != PreviewState.STOPPED;
        }
        if (stopButton != null) {
            stopButton.active = previewState != PreviewState.STOPPED;
        }
    }

    private void refreshSendButton() {
        if (sendButton != null) {
            sendButton.active = sendAllowed && issue == PlayForm.Issue.NONE;
        }
    }

    /** Recomputes everything derived from the form after any value changed. */
    private void onFormChanged() {
        command = PlayCommand.format(form);
        commandLine = textRenderer.trimToWidth("/" + command, width - 2 * MARGIN);
        issue = form.check(true, command.length());
        sendAllowed = canSend();
        refreshSendButton();
        refreshIdleStatus();
    }

    private void refreshIdleStatus() {
        if (issue != PlayForm.Issue.NONE) {
            idleStatus = Text.translatable(issueKey(issue), PlayCommand.MAX_LENGTH);
            idleColor = COLOR_WARNING;
        } else if (!sendAllowed) {
            idleStatus = Text.translatable("soundmanager.gui.status.no_permission", form.mode.literal());
            idleColor = COLOR_GRAY;
        } else if (previewState == PreviewState.PLAYING) {
            idleStatus = Text.translatable("soundmanager.gui.status.playing");
            idleColor = COLOR_OK;
        } else if (previewState == PreviewState.PAUSED) {
            idleStatus = Text.translatable("soundmanager.gui.status.paused");
            idleColor = COLOR_GRAY;
        } else {
            idleStatus = Text.translatable("soundmanager.gui.status.ready");
            idleColor = COLOR_GRAY;
        }
    }

    private void setStatus(String key, int color, Object... args) {
        status = Text.translatable(key, args);
        statusColor = color;
        statusTicks = STATUS_TICKS;
    }

    private static String issueKey(PlayForm.Issue value) {
        return "soundmanager.gui.issue." + value.name().toLowerCase(Locale.ROOT);
    }
}
