package com.bookstore.ecommerce.service;

import com.bookstore.ecommerce.api.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AvatarServiceTest {

    @TempDir
    Path dir;
    AvatarService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new AvatarService(dir.toString(), 2 * 1024 * 1024);
    }

    static byte[] image(String format, int w, int h, boolean alpha) throws Exception {
        BufferedImage img = new BufferedImage(w, h, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(40, 120, 90, alpha ? 128 : 255));
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    @Test
    void storesJpegAsCroppedSquare() throws Exception {
        String key = service.store(new MockMultipartFile("file", "me.jpg", "image/jpeg", image("jpg", 900, 600, false)));
        assertTrue(key.matches("avatars/[a-f0-9]{32}\\.jpg"), key);
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(Files.readAllBytes(dir.resolve(key))));
        assertEquals(400, stored.getWidth());
        assertEquals(400, stored.getHeight());
        assertTrue(service.load(key).isPresent());
    }

    @Test
    void keepsTransparencyAsPng() throws Exception {
        String key = service.store(new MockMultipartFile("file", "x.png", "image/png", image("png", 120, 120, true)));
        assertTrue(key.endsWith(".png"));
    }

    @Test
    void ignoresFilenameAndBrowserMimeType() throws Exception {
        // A real PNG with a misleading name and type is accepted as a PNG...
        String key = service.store(new MockMultipartFile("file", "evil.exe", "application/x-msdownload", image("png", 64, 64, false)));
        assertTrue(key.matches("avatars/[a-f0-9]{32}\\.jpg"));
        // ...and a script dressed up as an image is rejected
        byte[] script = "<?php system($_GET['c']); ?>".getBytes();
        ApiException e = assertThrows(ApiException.class,
                () -> service.store(new MockMultipartFile("file", "photo.png", "image/png", script)));
        assertEquals("avatar", e.getField());
    }

    @Test
    void rejectsTruncatedImage() throws Exception {
        byte[] png = image("png", 200, 200, false);
        byte[] broken = java.util.Arrays.copyOf(png, 40);
        assertThrows(ApiException.class, () -> service.store(new MockMultipartFile("file", "a.png", "image/png", broken)));
    }

    @Test
    void rejectsGifAndEmptyAndOversized() throws Exception {
        assertThrows(ApiException.class, () -> service.store(new MockMultipartFile("file", "a.gif", "image/gif", image("gif", 64, 64, false))));
        assertThrows(ApiException.class, () -> service.store(new MockMultipartFile("file", "a.png", "image/png", new byte[0])));
        AvatarService tiny = new AvatarService(dir.toString(), 100);
        assertThrows(ApiException.class, () -> tiny.store(new MockMultipartFile("file", "a.png", "image/png", image("png", 64, 64, false))));
    }

    @Test
    void rejectsTooSmallImage() throws Exception {
        assertThrows(ApiException.class, () -> service.store(new MockMultipartFile("file", "a.png", "image/png", image("png", 10, 10, false))));
    }

    @Test
    void refusesPathTraversalKeys() {
        assertTrue(service.load("../../pom.xml").isEmpty());
        assertTrue(service.load("avatars/../../x.jpg").isEmpty());
        service.delete("../../pom.xml"); // no-op, must not throw or delete anything
    }

    @Test
    void detectsFormatsByMagicBytes() {
        assertEquals("jpeg", AvatarService.detectFormat(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        assertEquals("webp", AvatarService.detectFormat("RIFF\0\0\0\0WEBPVP8 ".getBytes()));
        assertNull(AvatarService.detectFormat("GIF89a".getBytes()));
    }
}
