package land.webgui.keys;

import java.util.List;
import java.util.Set;

/**
 * Keys that have to be held together. Each slot is satisfied by any one of its codes, which is
 * how "shift" means either shift key.
 */
public final class KeyCombo {
    private final int[][] slots;

    private KeyCombo(int[][] slots) {
        this.slots = slots;
    }

    /** The combination for these canonical key names, or null if there are none or one is unknown. */
    public static KeyCombo of(List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        int[][] slots = new int[names.size()][];
        for (int i = 0; i < slots.length; i++) {
            int[] codes = KeyCodes.codes(names.get(i));
            if (codes == null) {
                return null;
            }
            slots[i] = codes;
        }
        return new KeyCombo(slots);
    }

    public int size() {
        return slots.length;
    }

    /** Whether {@code key} is one of the keys this combination is made of. */
    public boolean uses(int key) {
        for (int[] slot : slots) {
            for (int code : slot) {
                if (code == key) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether every slot has a key in {@code down}. */
    public boolean heldIn(Set<Integer> down) {
        for (int[] slot : slots) {
            boolean held = false;
            for (int code : slot) {
                if (down.contains(code)) {
                    held = true;
                    break;
                }
            }
            if (!held) {
                return false;
            }
        }
        return true;
    }
}
