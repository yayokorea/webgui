package land.webgui.keys;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Remembers which keys are down and says when a press completes a combination.
 *
 * <p>Fed from the game's key events rather than polled, so a tap shorter than a tick is not
 * lost. A combination fires once, on the press that completes it: holding the keys, and the
 * auto-repeat that follows, do not fire it again, and neither does pressing a modifier that is
 * already covered by the other one of its pair. Extra keys held at the time are allowed — a
 * player walking while pressing Shift+F still means Shift+F — so when several combinations
 * complete on one press, the one with the most keys wins (Ctrl+Shift+F over Shift+F), and the
 * first listed among equals.
 */
public final class KeyComboTracker {
    private final Set<Integer> down = new HashSet<>();

    /**
     * A key went down.
     *
     * @return the candidate this press completed, or null if none, or the key was already down
     */
    public <T> T press(int key, List<T> candidates, Function<T, KeyCombo> comboOf) {
        if (down.contains(key)) {
            return null;
        }
        List<T> wasHeld = new ArrayList<>();
        for (T candidate : candidates) {
            KeyCombo combo = comboOf.apply(candidate);
            if (combo != null && combo.heldIn(down)) {
                wasHeld.add(candidate);
            }
        }
        down.add(key);
        T best = null;
        int bestSize = 0;
        for (T candidate : candidates) {
            KeyCombo combo = comboOf.apply(candidate);
            if (combo == null || !combo.uses(key) || wasHeld.contains(candidate) || !combo.heldIn(down)) {
                continue;
            }
            if (combo.size() > bestSize) {
                best = candidate;
                bestSize = combo.size();
            }
        }
        return best;
    }

    /** A key went up. */
    public void release(int key) {
        down.remove(key);
    }

    /** Forgets every key, for when the game may have missed their release. */
    public void clear() {
        down.clear();
    }

    /** The keys currently down, as a copy. */
    public Set<Integer> held() {
        return new HashSet<>(down);
    }
}
