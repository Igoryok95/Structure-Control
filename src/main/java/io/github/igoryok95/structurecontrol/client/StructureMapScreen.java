package io.github.igoryok95.structurecontrol.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.igoryok95.structurecontrol.MapRequest;
import io.github.igoryok95.structurecontrol.MapSnapshot;
import io.github.igoryok95.structurecontrol.StructureControl;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.network.PacketDistributor;

public final class StructureMapScreen extends Screen {
    private static final ResourceLocation TERRAIN = ResourceLocation.fromNamespaceAndPath(StructureControl.MOD_ID, "map_terrain");
    private static final int ACTIVE = 0xff45bd7f, DISABLED = 0xffe66a68, REMOVED = 0xff9a9da5;
    private MapSnapshot snapshot;
    private MapSnapshot.Entry selected;
    private int centreX, centreZ, radius, sequence;
    private int mapX, mapY, mapSize, panelX, panelWidth;
    private String token = "";
    private Component status = Component.empty();
    private boolean error, waiting, dragging;
    private double pressX, pressY;
    private int dragCentreX, dragCentreZ;
    private long requestAt, navigationAt;
    private boolean navigationPending;
    private DynamicTexture terrain;
    private final List<Button> navigation = new ArrayList<>();
    private Button toggle, unregister, confirm, cancel, undo;
    private int infoScroll, infoMaxScroll;

    public StructureMapScreen(MapSnapshot snapshot) {
        super(tr("ui.title"));
        this.snapshot = snapshot;
        this.centreX = snapshot.centreX(); this.centreZ = snapshot.centreZ(); this.radius = snapshot.radius();
        this.sequence = snapshot.sequence(); this.status = snapshot.message(); this.error = snapshot.error();
    }

    public static Component tr(String key, Object... args) { return Component.translatable("structure_control." + key, args); }

    @Override protected void init() {
        clearWidgets(); navigation.clear();
        int sidebar = Math.max(140, Math.min(210, width / 3));
        mapX = 8; mapY = 34;
        mapSize = Math.max(64, Math.min(width - sidebar - 24, height - 104));
        panelX = mapX + mapSize + 8; panelWidth = width - panelX - 8;
        addRenderableWidget(Button.builder(tr("ui.refresh"), button -> request(MapRequest.Action.SNAPSHOT)).bounds(8, 7, 78, 20).build());
        addRenderableWidget(Button.builder(tr("ui.close"), button -> onClose()).bounds(width - 78, 7, 70, 20).build());
        int navY = mapY + mapSize + 4;
        String[] names = {"west", "north", "centre", "south", "east", "zoom_in", "zoom_out"};
        int navigationWidth = Math.max(12, Math.min(20, mapSize / 7 - 1));
        for (int i = 0; i < names.length; i++) {
            final String action = names[i];
            var button = Button.builder(tr("ui." + action), ignored -> navigate(action)).bounds(mapX + i * (navigationWidth + 1), navY, navigationWidth, 18).build();
            navigation.add(addRenderableWidget(button));
        }
        int buttonsY = Math.max(mapY + 20, height - 108);
        toggle = addRenderableWidget(Button.builder(tr("ui.disable"), button -> request(selected.disabled() ? MapRequest.Action.SPAWNS_ON : MapRequest.Action.SPAWNS_OFF))
                .bounds(panelX, buttonsY, panelWidth, 20).build());
        unregister = addRenderableWidget(Button.builder(tr("ui.unregister"), button -> request(MapRequest.Action.PREVIEW))
                .bounds(panelX, buttonsY + 23, panelWidth, 20).build());
        confirm = addRenderableWidget(Button.builder(tr("ui.confirm"), button -> request(MapRequest.Action.CONFIRM))
                .bounds(panelX, buttonsY, panelWidth, 20).build());
        cancel = addRenderableWidget(Button.builder(tr("ui.cancel"), button -> request(MapRequest.Action.CANCEL))
                .bounds(panelX, buttonsY + 23, panelWidth, 20).build());
        undo = addRenderableWidget(Button.builder(tr("ui.undo"), button -> request(MapRequest.Action.UNDO))
                .bounds(panelX, buttonsY + 46, panelWidth, 20).build());
        updateButtons();
        rebuildTerrain();
    }

    private void updateButtons() {
        boolean ready = !waiting && !navigationPending;
        toggle.visible = unregister.visible = token.isEmpty();
        confirm.visible = cancel.visible = !token.isEmpty();
        toggle.active = ready && selected != null && selected.registered() && selected.hasSpawns();
        unregister.active = ready && selected != null && selected.registered();
        confirm.active = cancel.active = ready;
        undo.active = ready && snapshot.canUndo() && token.isEmpty();
        toggle.setMessage(tr(selected != null && selected.disabled() ? "ui.enable" : "ui.disable"));
        navigation.forEach(button -> button.active = ready && token.isEmpty());
    }

    public void apply(MapSnapshot update) {
        if (update.sequence() < sequence) return;
        sequence = update.sequence(); waiting = false;
        status = update.message(); error = update.error();
        if (update.error() && update.entries().isEmpty()) { updateButtons(); return; }
        snapshot = update;
        centreX = update.centreX(); centreZ = update.centreZ(); radius = update.radius();
        token = update.token();
        var previous = selected;
        selected = update.entries().stream().filter(entry -> entry.same(previous)).findFirst().orElse(null);
        rebuildTerrain();
        updateButtons();
    }

    private void request(MapRequest.Action action) {
        if (minecraft == null || minecraft.getConnection() == null || !minecraft.getConnection().hasChannel(MapRequest.TYPE)) {
            status = tr("error.server_missing"); error = true; return;
        }
        if (waiting) return;
        waiting = true; requestAt = System.currentTimeMillis(); status = tr("ui.wait"); error = false;
        PacketDistributor.sendToServer(new MapRequest(++sequence, action, centreX, centreZ, radius,
                selected == null ? "" : selected.structure(), selected == null ? 0 : selected.startX(), selected == null ? 0 : selected.startZ(), token, false));
        updateButtons();
    }

    private void navigate(String action) {
        int step = Math.max(1, radius / 2);
        switch (action) {
            case "west" -> centreX -= step;
            case "east" -> centreX += step;
            case "north" -> centreZ -= step;
            case "south" -> centreZ += step;
            case "zoom_in" -> radius = Math.max(2, radius - 1);
            case "zoom_out" -> radius = Math.min(8, radius + 1);
            case "centre" -> { if (minecraft.player != null) { centreX = minecraft.player.chunkPosition().x; centreZ = minecraft.player.chunkPosition().z; } }
        }
        queueNavigation();
    }

    private void queueNavigation() {
        centreX = Math.max(-1_875_000, Math.min(1_875_000, centreX));
        centreZ = Math.max(-1_875_000, Math.min(1_875_000, centreZ));
        navigationPending = true; navigationAt = System.currentTimeMillis(); updateButtons();
    }

    @Override public void tick() {
        if (navigationPending && !waiting && !dragging && System.currentTimeMillis() - navigationAt >= 160) {
            navigationPending = false; request(MapRequest.Action.SNAPSHOT);
        }
        if (waiting && System.currentTimeMillis() - requestAt > 5000) {
            waiting = false; error = true; status = tr("error.request_timeout"); updateButtons();
        }
    }

    private int span() { return (snapshot.radius() * 2 + 1) * 16; }
    private int minX() { return (snapshot.centreX() - snapshot.radius()) * 16; }
    private int minZ() { return (snapshot.centreZ() - snapshot.radius()) * 16; }
    private int pixelX(double block) { return mapX + (int) Math.round((block - minX()) * mapSize / span()); }
    private int pixelZ(double block) { return mapY + (int) Math.round((block - minZ()) * mapSize / span()); }
    private boolean onMap(double x, double y) { return x >= mapX && y >= mapY && x < mapX + mapSize && y < mapY + mapSize; }

    private void rebuildTerrain() {
        if (minecraft == null || minecraft.level == null) return;
        if (terrain == null) {
            terrain = new DynamicTexture(new NativeImage(128, 128, false));
            minecraft.getTextureManager().register(TERRAIN, terrain);
        }
        NativeImage pixels = terrain.getPixels();
        var level = minecraft.level;
        var pos = new BlockPos.MutableBlockPos();
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            int wx = minX() + x * span() / 128, wz = minZ() + y * span() / 128;
            var chunk = level.getChunkSource().getChunk(wx >> 4, wz >> 4, ChunkStatus.FULL, false);
            int colour = ((x / 8 + y / 8) & 1) == 0 ? 0xff25272c : 0xff2b2e34;
            if (chunk != null) {
                int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx & 15, wz & 15);
                pos.set(wx, height, wz);
                var state = level.getBlockState(pos);
                if (state.isAir()) { pos.move(0, -1, 0); state = level.getBlockState(pos); }
                MapColor mapColour = state.getMapColor(level, pos);
                if (mapColour != MapColor.NONE) colour = mapColour.calculateRGBColor(MapColor.Brightness.NORMAL);
            }
            pixels.setPixelRGBA(x, y, colour);
        }
        terrain.upload();
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The map draws its own background. Screen.render must not blur the map before drawing widgets.
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xf012151c);
        graphics.drawCenteredString(font, title, width / 2, 13, 0xffeeeeee);
        graphics.fill(mapX - 1, mapY - 1, mapX + mapSize + 1, mapY + mapSize + 1, 0xff65717c);
        if (terrain != null) graphics.blit(TERRAIN, mapX, mapY, mapSize, mapSize, 0, 0, 128, 128, 128, 128);
        graphics.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);
        for (int i = 0; i <= snapshot.radius() * 2 + 1; i++) {
            int line = (int) Math.round(i * mapSize / (double) (snapshot.radius() * 2 + 1));
            graphics.fill(mapX + line, mapY, mapX + line + 1, mapY + mapSize, 0x66000000);
            graphics.fill(mapX, mapY + line, mapX + mapSize, mapY + line + 1, 0x66000000);
        }
        for (MapSnapshot.Entry entry : snapshot.entries()) {
            int colour = !entry.registered() ? REMOVED : entry.disabled() ? DISABLED : ACTIVE;
            int x1 = pixelX(entry.minX()), z1 = pixelZ(entry.minZ()), x2 = pixelX(entry.maxX() + 1.0), z2 = pixelZ(entry.maxZ() + 1.0);
            graphics.fill(x1, z1, x2, z2, (colour & 0xffffff) | 0x55000000);
            border(graphics, x1, z1, x2, z2, entry.same(selected) ? 0xffffdd61 : colour, entry.same(selected) ? 2 : 1);
        }
        if (minecraft.player != null) {
            int px = pixelX(minecraft.player.getX()), pz = pixelZ(minecraft.player.getZ());
            graphics.fill(px - 2, pz - 2, px + 3, pz + 3, 0xff101010);
            graphics.fill(px - 1, pz - 1, px + 2, pz + 2, 0xffffffff);
        }
        graphics.disableScissor();
        graphics.fill(panelX - 3, mapY - 1, width - 5, height - 37, 0xff1d232d);
        graphics.enableScissor(panelX, mapY, width - 8, Math.max(mapY + 8, height - 113));
        int y = mapY + 3 - infoScroll;
        y = wrapped(graphics, tr("ui.count", snapshot.entries().size()), panelX, y, panelWidth, 0xffb7c6d8) + 7;
        if (selected == null) y = wrapped(graphics, tr("ui.choose"), panelX, y, panelWidth, 0xffeeeeee);
        else {
            y = wrapped(graphics, Component.literal(selected.structure()), panelX, y, panelWidth, 0xffffdd61) + 5;
            y = wrapped(graphics, tr("ui.start", selected.startX(), selected.startZ()), panelX, y, panelWidth, 0xffc6ccd4);
            y = wrapped(graphics, tr("ui.bounds_x", selected.minX(), selected.maxX()), panelX, y, panelWidth, 0xffc6ccd4);
            y = wrapped(graphics, tr("ui.bounds_y", selected.minY(), selected.maxY()), panelX, y, panelWidth, 0xffc6ccd4);
            y = wrapped(graphics, tr("ui.bounds_z", selected.minZ(), selected.maxZ()), panelX, y, panelWidth, 0xffc6ccd4) + 5;
            y = wrapped(graphics, tr("ui.spawns", tr(!selected.registered() ? "state.unregistered" : selected.disabled() ? "state.off" : "state.on")), panelX, y, panelWidth, 0xffeeeeee);
            if (!selected.hasSpawns()) y = wrapped(graphics, tr("ui.no_special_spawns"), panelX, y + 4, panelWidth, 0xffafb6c0);
            if (!token.isEmpty()) y = wrapped(graphics, tr("ui.confirm_warning"), panelX, y + 5, panelWidth, 0xffffc55e);
        }
        infoMaxScroll = Math.max(0, y + infoScroll - (height - 113));
        infoScroll = Math.min(infoScroll, infoMaxScroll);
        graphics.disableScissor();
        int legendY = Math.max(mapY + mapSize + 25, height - 37);
        int legendX = mapX;
        for (int i = 0; i < 3; i++) {
            int colour = i == 0 ? ACTIVE : i == 1 ? DISABLED : REMOVED;
            Component label = tr(i == 0 ? "ui.legend_on" : i == 1 ? "ui.legend_off" : "ui.legend_removed");
            graphics.fill(legendX, legendY, legendX + 6, legendY + 6, colour);
            graphics.drawString(font, label, legendX + 9, legendY - 1, 0xffcdd3dc, false);
            legendX += 18 + font.width(label);
        }
        graphics.enableScissor(8, height - 25, width - 8, height);
        Component footer = snapshot.truncated() ? tr("ui.truncated") : status;
        int footerY = height - 24;
        for (var line : font.split(footer, width - 16)) {
            graphics.drawString(font, line, 8, footerY, error ? 0xffff8d8d : 0xffc1ccd7, false);
            footerY += 10;
        }
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
        if (onMap(mouseX, mouseY) && !dragging) {
            var hovered = hits(mouseX, mouseY);
            if (!hovered.isEmpty()) graphics.renderComponentTooltip(font,
                    List.of(Component.literal(hovered.getFirst().structure()), tr("ui.click_cycle")), mouseX, mouseY);
        }
    }

    private static void border(GuiGraphics graphics, int x1, int y1, int x2, int y2, int colour, int thickness) {
        graphics.fill(x1, y1, x2, y1 + thickness, colour); graphics.fill(x1, y2 - thickness, x2, y2, colour);
        graphics.fill(x1, y1, x1 + thickness, y2, colour); graphics.fill(x2 - thickness, y1, x2, y2, colour);
    }

    private int wrapped(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int colour) {
        for (FormattedCharSequence line : font.split(text, Math.max(20, maxWidth))) {
            graphics.drawString(font, line, x, y, colour, false); y += 10;
        }
        return y;
    }

    private List<MapSnapshot.Entry> hits(double x, double y) {
        double wx = minX() + (x - mapX) * span() / mapSize, wz = minZ() + (y - mapY) * span() / mapSize;
        return snapshot.entries().stream().filter(entry -> entry.covers(wx, wz)).sorted(Comparator.comparingLong(MapSnapshot.Entry::area)).toList();
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && onMap(x, y) && !waiting && token.isEmpty()) {
            dragging = true; pressX = x; pressY = y; dragCentreX = centreX; dragCentreZ = centreZ;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging && button == 0) {
            centreX = dragCentreX - (int) Math.round((x - pressX) * span() / mapSize / 16);
            centreZ = dragCentreZ - (int) Math.round((y - pressY) * span() / mapSize / 16);
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (dragging && button == 0) {
            dragging = false;
            if (Math.hypot(x - pressX, y - pressY) < 4) {
                var hits = hits(x, y);
                if (hits.isEmpty()) selected = null;
                else {
                    int current = -1;
                    for (int i = 0; i < hits.size(); i++) if (hits.get(i).same(selected)) current = i;
                    selected = hits.get((current + 1) % hits.size());
                }
                updateButtons();
                infoScroll = 0;
            } else queueNavigation();
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (onMap(x, y) && !waiting && token.isEmpty()) { navigate(vertical > 0 ? "zoom_in" : "zoom_out"); return true; }
        if (x >= panelX && y >= mapY && y < height - 113) {
            infoScroll = Math.max(0, Math.min(infoMaxScroll, infoScroll - (int) (vertical * 15)));
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public void removed() {
        if (terrain != null && minecraft != null) { minecraft.getTextureManager().release(TERRAIN); terrain = null; }
    }

    @Override public boolean isPauseScreen() { return false; }
}
