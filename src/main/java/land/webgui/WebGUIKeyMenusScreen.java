package land.webgui;

import com.mojang.blaze3d.platform.InputConstants;
import land.webgui.common.KeyMenu;
import land.webgui.common.KeyNames;
import land.webgui.compat.Mc;
import land.webgui.keys.KeyCodes;

//? if >=26 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///? } else {
import net.minecraft.client.gui.GuiGraphics;
//? }
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//? }
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a player chooses their own keys for the server's key menus.
 *
 * <p>One row per menu the server sent, so it is empty outside a server that has any. Click a
 * row, press the keys together, let go: whatever was held is the new combination. The choice is
 * kept per menu id and beats the server's keys until reset.
 *
 * <p>Paged rather than scrolled, for the same reason the settings screen is plain buttons: a
 * list widget's API differs on nearly every version this has to build on, and a server may send
 * any number of menus.
 */
public class WebGUIKeyMenusScreen extends Screen {

    private static final int ROW_HEIGHT = 24;
    private static final int KEY_WIDTH = 180;
    private static final int SMALL_WIDTH = 52;
    private static final int GAP = 2;
    private static final int TOP = 40;
    private static final int BOTTOM = 64;

    private final Screen parent;
    private int page;

    /** The menu being rebound, or null. */
    private KeyMenu capturing;
    private final List<String> captured = new ArrayList<>();
    private final Set<Integer> held = new HashSet<>();

    public WebGUIKeyMenusScreen(Screen parent) {
        super(Component.translatable("screen.webgui.keymenus.title"));
        this.parent = parent;
    }

    private int perPage() {
        return Math.max(1, (this.height - TOP - BOTTOM) / ROW_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();
        List<KeyMenu> menus = WebGUIKeyMenus.menus();
        int perPage = perPage();
        int pages = Math.max(1, (menus.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);

        int rowWidth = KEY_WIDTH + (SMALL_WIDTH + GAP) * 2;
        int left = this.width / 2 - rowWidth / 2;
        int from = page * perPage;
        int to = Math.min(menus.size(), from + perPage);
        for (int i = from; i < to; i++) {
            KeyMenu menu = menus.get(i);
            int top = TOP + (i - from) * ROW_HEIGHT;

            Button key = Button.builder(rowLabel(menu), b -> startCapture(menu))
                    .tooltip(Tooltip.create(Component.literal(menu.url())))
                    .bounds(left, top, KEY_WIDTH, 20)
                    .build();
            this.addRenderableWidget(key);

            Button unbind = Button.builder(Component.translatable("screen.webgui.keymenus.unbind"), b -> {
                        WebGUIClientConfig.setKeyOverride(menu.id(), List.of());
                        changed();
                    })
                    .bounds(left + KEY_WIDTH + GAP, top, SMALL_WIDTH, 20)
                    .build();
            unbind.active = capturing == null && !WebGUIKeyMenus.keysFor(menu).isEmpty();
            this.addRenderableWidget(unbind);

            Button reset = Button.builder(Component.translatable("screen.webgui.keymenus.reset"), b -> {
                        WebGUIClientConfig.clearKeyOverride(menu.id());
                        changed();
                    })
                    .bounds(left + KEY_WIDTH + GAP + SMALL_WIDTH + GAP, top, SMALL_WIDTH, 20)
                    .build();
            reset.active = capturing == null && WebGUIClientConfig.keyOverride(menu.id()) != null;
            this.addRenderableWidget(reset);
            key.active = capturing == null || capturing == menu;
        }

        int navTop = this.height - 52;
        Button previous = Button.builder(Component.literal("<"), b -> turn(-1))
                .bounds(this.width / 2 - 62, navTop, 20, 20).build();
        previous.active = page > 0 && capturing == null;
        this.addRenderableWidget(previous);
        Button next = Button.builder(Component.literal(">"), b -> turn(1))
                .bounds(this.width / 2 + 42, navTop, 20, 20).build();
        next.active = page < pages - 1 && capturing == null;
        this.addRenderableWidget(next);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private void turn(int by) {
        page += by;
        this.rebuildWidgets();
    }

    private void changed() {
        WebGUIKeyMenus.rebuild();
        this.rebuildWidgets();
    }

    private Component rowLabel(KeyMenu menu) {
        if (capturing == menu) {
            String so_far = captured.isEmpty() ? "" : " " + show(captured);
            return Component.translatable("screen.webgui.keymenus.press").append(so_far);
        }
        List<String> keys = WebGUIKeyMenus.keysFor(menu);
        Component shown = keys.isEmpty() ? Component.translatable("screen.webgui.keymenus.unbound") : Component.literal(show(keys));
        return Component.literal(menu.id() + ": ").append(shown);
    }

    private static String show(List<String> keys) {
        return KeyNames.join(keys).toUpperCase(java.util.Locale.ROOT).replace("+", " + ");
    }

    private void startCapture(KeyMenu menu) {
        capturing = menu;
        captured.clear();
        held.clear();
        this.rebuildWidgets();
    }

    private void stopCapture() {
        capturing = null;
        captured.clear();
        held.clear();
        this.rebuildWidgets();
    }

    private boolean capturePress(int key) {
        if (capturing == null) {
            return false;
        }
        if (key == InputConstants.KEY_ESCAPE && captured.isEmpty()) {
            stopCapture();
            return true;
        }
        held.add(key);
        String name = KeyCodes.name(key);
        if (name != null && !captured.contains(name) && captured.size() < KeyNames.MAX_KEYS) {
            captured.add(name);
            this.rebuildWidgets();
        }
        return true;
    }

    private boolean captureRelease(int key) {
        if (capturing == null) {
            return false;
        }
        held.remove(key);
        if (held.isEmpty() && !captured.isEmpty()) {
            WebGUIClientConfig.setKeyOverride(capturing.id(), new ArrayList<>(captured));
            capturing = null;
            captured.clear();
            changed();
        }
        return true;
    }

    //? if >=1.21.9 {
    @Override
    public boolean keyPressed(KeyEvent event) {
        return capturePress(event.key()) || super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        return captureRelease(event.key()) || super.keyReleased(event);
    }
    //? } else {
    /*@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return capturePress(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return captureRelease(keyCode) || super.keyReleased(keyCode, scanCode, modifiers);
    }*/
    //? }

    private Component hint() {
        return Component.translatable(capturing != null
                ? "screen.webgui.keymenus.hint.capture"
                : WebGUIKeyMenus.menus().isEmpty() ? "screen.webgui.keymenus.hint.none" : "screen.webgui.keymenus.hint");
    }

    //? if >=26 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        context.centeredText(this.font, hint(), this.width / 2, 26, 0xFFA0A0A0);
    }
    *///? } else {
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);
        context.drawCenteredString(this.font, hint(), this.width / 2, 26, 0xFFA0A0A0);
    }
    //? }

    @Override
    public void onClose() {
        Mc.openScreen(this.minecraft, this.parent);
    }
}
