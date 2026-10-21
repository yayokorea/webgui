package space.webgui.wmc.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import space.webgui.wmc.api.Damage;

class CefFrameTest {
    private static void paint(CefFrame frame, int width, int height, int value) {
        ByteBuffer pixels = ByteBuffer.allocate(width * height * 4);
        while (pixels.hasRemaining()) pixels.put((byte) value);
        pixels.flip();
        frame.accept(pixels, new Damage(0, 0, width, height), width, height);
    }

    @Test
    void shrinkClipsRememberedDamageAndCopiesTheNewImage() {
        CefFrame frame = new CefFrame();
        paint(frame, 9, 7, 1);
        var before = frame.snapshotSince(-1, null);
        paint(frame, 9, 7, 2);
        paint(frame, 3, 2, 3);
        var after = frame.snapshotSince(before.revision(), before.pixels());
        assertEquals(3, after.width());
        assertEquals(2, after.height());
        assertEquals(new Damage(0, 0, 3, 2), after.damage());
        assertEquals(24, after.pixels().remaining());
        while (after.pixels().hasRemaining()) assertEquals(3, after.pixels().get());
    }

    @Test
    void snapshotKeepsItsPixelsAfterAnotherPaintAndReusesReaderBuffer() {
        CefFrame frame = new CefFrame();
        paint(frame, 3, 2, 7);
        var first = frame.snapshotSince(-1, null);
        paint(frame, 2, 2, 9);
        assertEquals(7, first.pixels().get(0));
        var second = frame.snapshotSince(first.revision(), first.pixels());
        assertSame(first.pixels(), second.pixels());
        assertEquals(9, second.pixels().get(0));
        var unchanged = frame.snapshotSince(second.revision(), second.pixels());
        assertTrue(unchanged.damage().isEmpty());
    }

    @Test
    void partialUpdatesAndPopupPixelsAreIncludedInSnapshot() {
        CefFrame frame = new CefFrame();
        paint(frame, 4, 3, 1);
        long revision = frame.revision();
        frame.acceptPopup(ByteBuffer.wrap(new byte[] {9, 8, 7, 6}), 1, 1);
        frame.placePopup(true, 2, 1, 1, 1);
        var snapshot = frame.snapshotSince(revision, null);
        assertEquals(new Damage(2, 1, 1, 1), snapshot.damage());
        assertArrayEquals(new byte[] {9, 8, 7, 6}, new byte[] {
                snapshot.pixels().get(), snapshot.pixels().get(),
                snapshot.pixels().get(), snapshot.pixels().get()});
    }

    @Test
    void concurrentResizeNeverMixesMetadataWithAnotherPaint() throws Exception {
        CefFrame frame = new CefFrame();
        paint(frame, 3, 2, 3);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var writer = executor.submit(() -> {
                start.await();
                for (int i = 0; i < 2000; i++) {
                    int size = i % 2 == 0 ? 3 : 11;
                    paint(frame, size, 2, size);
                }
                return null;
            });
            start.countDown();
            ByteBuffer reusable = null;
            for (int i = 0; i < 2000; i++) {
                var snapshot = frame.snapshotSince(-1, reusable);
                reusable = snapshot.pixels();
                assertEquals(new Damage(0, 0, snapshot.width(), 2), snapshot.damage());
                assertEquals(snapshot.width() * 2 * 4, reusable.remaining());
                while (reusable.hasRemaining()) assertEquals(snapshot.width(), reusable.get());
            }
            writer.get(10, TimeUnit.SECONDS);
        }
    }
}
