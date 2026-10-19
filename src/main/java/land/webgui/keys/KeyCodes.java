package land.webgui.keys;

import com.mojang.blaze3d.platform.InputConstants;
import land.webgui.common.KeyNames;

import java.util.HashMap;
import java.util.Map;

/**
 * Key names (see {@link KeyNames}) and the codes the running game numbers them with.
 *
 * <p>Every code comes from {@link InputConstants}, never a literal: up to 26.2 the numbers are
 * GLFW's, from 26.3 they are SDL's scan codes, and the game knows which it is on. Names the
 * server may use that a version lacks a constant for simply resolve to nothing.
 */
public final class KeyCodes {
    private KeyCodes() {}

    private static final Map<String, int[]> BY_NAME = new HashMap<>();
    private static final Map<Integer, String> BY_CODE = new HashMap<>();

    private static void def(String name, int... codes) {
        BY_NAME.put(name, codes);
        for (int code : codes) {
            // The first name wins, and the generic modifier names are defined before the
            // sided ones, so Left Shift reads back as "shift".
            BY_CODE.putIfAbsent(code, name);
        }
    }

    static {
        def("a", InputConstants.KEY_A);
        def("b", InputConstants.KEY_B);
        def("c", InputConstants.KEY_C);
        def("d", InputConstants.KEY_D);
        def("e", InputConstants.KEY_E);
        def("f", InputConstants.KEY_F);
        def("g", InputConstants.KEY_G);
        def("h", InputConstants.KEY_H);
        def("i", InputConstants.KEY_I);
        def("j", InputConstants.KEY_J);
        def("k", InputConstants.KEY_K);
        def("l", InputConstants.KEY_L);
        def("m", InputConstants.KEY_M);
        def("n", InputConstants.KEY_N);
        def("o", InputConstants.KEY_O);
        def("p", InputConstants.KEY_P);
        def("q", InputConstants.KEY_Q);
        def("r", InputConstants.KEY_R);
        def("s", InputConstants.KEY_S);
        def("t", InputConstants.KEY_T);
        def("u", InputConstants.KEY_U);
        def("v", InputConstants.KEY_V);
        def("w", InputConstants.KEY_W);
        def("x", InputConstants.KEY_X);
        def("y", InputConstants.KEY_Y);
        def("z", InputConstants.KEY_Z);

        def("0", InputConstants.KEY_0);
        def("1", InputConstants.KEY_1);
        def("2", InputConstants.KEY_2);
        def("3", InputConstants.KEY_3);
        def("4", InputConstants.KEY_4);
        def("5", InputConstants.KEY_5);
        def("6", InputConstants.KEY_6);
        def("7", InputConstants.KEY_7);
        def("8", InputConstants.KEY_8);
        def("9", InputConstants.KEY_9);

        def("f1", InputConstants.KEY_F1);
        def("f2", InputConstants.KEY_F2);
        def("f3", InputConstants.KEY_F3);
        def("f4", InputConstants.KEY_F4);
        def("f5", InputConstants.KEY_F5);
        def("f6", InputConstants.KEY_F6);
        def("f7", InputConstants.KEY_F7);
        def("f8", InputConstants.KEY_F8);
        def("f9", InputConstants.KEY_F9);
        def("f10", InputConstants.KEY_F10);
        def("f11", InputConstants.KEY_F11);
        def("f12", InputConstants.KEY_F12);
        def("f13", InputConstants.KEY_F13);
        def("f14", InputConstants.KEY_F14);
        def("f15", InputConstants.KEY_F15);
        def("f16", InputConstants.KEY_F16);
        def("f17", InputConstants.KEY_F17);
        def("f18", InputConstants.KEY_F18);
        def("f19", InputConstants.KEY_F19);
        def("f20", InputConstants.KEY_F20);
        def("f21", InputConstants.KEY_F21);
        def("f22", InputConstants.KEY_F22);
        def("f23", InputConstants.KEY_F23);
        def("f24", InputConstants.KEY_F24);

        def("kp0", InputConstants.KEY_NUMPAD0);
        def("kp1", InputConstants.KEY_NUMPAD1);
        def("kp2", InputConstants.KEY_NUMPAD2);
        def("kp3", InputConstants.KEY_NUMPAD3);
        def("kp4", InputConstants.KEY_NUMPAD4);
        def("kp5", InputConstants.KEY_NUMPAD5);
        def("kp6", InputConstants.KEY_NUMPAD6);
        def("kp7", InputConstants.KEY_NUMPAD7);
        def("kp8", InputConstants.KEY_NUMPAD8);
        def("kp9", InputConstants.KEY_NUMPAD9);
        def("kpadd", InputConstants.KEY_ADD);
        def("kpmultiply", InputConstants.KEY_MULTIPLY);
        def("kpenter", InputConstants.KEY_NUMPADENTER);

        // Either key of a pair first, so a code reads back as the generic name.
        def("shift", InputConstants.KEY_LSHIFT, InputConstants.KEY_RSHIFT);
        def("ctrl", InputConstants.KEY_LCONTROL, InputConstants.KEY_RCONTROL);
        def("alt", InputConstants.KEY_LALT, InputConstants.KEY_RALT);
        def("lshift", InputConstants.KEY_LSHIFT);
        def("rshift", InputConstants.KEY_RSHIFT);
        def("lctrl", InputConstants.KEY_LCONTROL);
        def("rctrl", InputConstants.KEY_RCONTROL);
        def("lalt", InputConstants.KEY_LALT);
        def("ralt", InputConstants.KEY_RALT);

        def("space", InputConstants.KEY_SPACE);
        def("tab", InputConstants.KEY_TAB);
        def("enter", InputConstants.KEY_RETURN);
        def("escape", InputConstants.KEY_ESCAPE);
        def("backspace", InputConstants.KEY_BACKSPACE);
        def("delete", InputConstants.KEY_DELETE);
        def("insert", InputConstants.KEY_INSERT);
        def("home", InputConstants.KEY_HOME);
        def("end", InputConstants.KEY_END);
        def("pageup", InputConstants.KEY_PAGEUP);
        def("pagedown", InputConstants.KEY_PAGEDOWN);
        def("up", InputConstants.KEY_UP);
        def("down", InputConstants.KEY_DOWN);
        def("left", InputConstants.KEY_LEFT);
        def("right", InputConstants.KEY_RIGHT);
        def("grave", InputConstants.KEY_GRAVE);
        def("minus", InputConstants.KEY_MINUS);
        def("equal", InputConstants.KEY_EQUALS);
        def("comma", InputConstants.KEY_COMMA);
        def("period", InputConstants.KEY_PERIOD);
        def("slash", InputConstants.KEY_SLASH);
        def("backslash", InputConstants.KEY_BACKSLASH);
        def("semicolon", InputConstants.KEY_SEMICOLON);
        def("apostrophe", InputConstants.KEY_APOSTROPHE);
        def("leftbracket", InputConstants.KEY_LBRACKET);
        def("rightbracket", InputConstants.KEY_RBRACKET);
        def("capslock", InputConstants.KEY_CAPSLOCK);
    }

    /** The codes that satisfy a canonical key name, or null for a name this version cannot press. */
    public static int[] codes(String name) {
        return BY_NAME.get(name);
    }

    /** The canonical name of a key code, or null for a key with no name. */
    public static String name(int code) {
        return BY_CODE.get(code);
    }

    /** Whether the key is a modifier, which on its own is not a combination worth capturing. */
    public static boolean isModifier(String name) {
        return "shift".equals(name) || "ctrl".equals(name) || "alt".equals(name)
                || "lshift".equals(name) || "rshift".equals(name)
                || "lctrl".equals(name) || "rctrl".equals(name)
                || "lalt".equals(name) || "ralt".equals(name);
    }
}
