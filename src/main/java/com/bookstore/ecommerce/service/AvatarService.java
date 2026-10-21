package com.bookstore.ecommerce.service;

import com.bookstore.ecommerce.api.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Profile pictures. An upload is identified by its content (magic bytes), never by its filename or
 * the browser's MIME type. It is decoded, centre-cropped to a square, scaled down and re-encoded, and
 * only that re-encoded image is stored, so metadata and anything hidden in the original file are
 * dropped. Files live under {@code app.uploads.dir} with random names; MongoDB holds only the key,
 * e.g. "avatars/9f1c….jpg".
 */
@Service
public class AvatarService {

    public record StoredImage(String key, byte[] bytes, String contentType) {
    }

    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);
    private static final Pattern KEY = Pattern.compile("avatars/[a-f0-9]{32}\\.(jpg|png)");
    private static final int OUTPUT_SIZE = 400;
    private static final int MAX_DIMENSION = 8000;
    private static final long MAX_PIXELS = 40_000_000L;

    private final Path root;
    private final long maxBytes;
    private final SecureRandom random = new SecureRandom();

    public AvatarService(@Value("${app.uploads.dir:data/uploads}") String dir,
                         @Value("${app.avatar.max-bytes:2097152}") long maxBytes) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
        Files.createDirectories(root.resolve("avatars"));
        ImageIO.scanForPlugins(); // pick up the WebP reader on the classpath
        ImageIO.setUseCache(false);
    }

    /** Validates and stores an uploaded picture, returning its storage key. */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("avatar", "Choose an image to upload.");
        }
        if (file.getSize() > maxBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "Profile pictures must be " + (maxBytes / (1024 * 1024)) + " MB or smaller.", "avatar");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("avatar", "The upload could not be read. Please try again.");
        }
        String format = detectFormat(data);
        if (format == null) {
            throw ApiException.badRequest("avatar", "Upload a JPG, PNG or WebP image.");
        }
        BufferedImage image = decode(data, format);
        BufferedImage square = cropAndScale(image);
        boolean alpha = image.getColorModel().hasAlpha();
        String ext = alpha ? "png" : "jpg";
        byte[] encoded = alpha ? writePng(square) : writeJpeg(square);

        String key = "avatars/" + HexFormat.of().formatHex(randomBytes()) + "." + ext;
        try {
            Files.write(resolve(key), encoded, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            log.error("Could not save a profile picture", e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "We couldn't save your picture. Please try again.", "avatar");
        }
        return key;
    }

    public Optional<StoredImage> load(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            return Optional.empty();
        }
        try {
            Path path = resolve(key);
            if (!Files.isRegularFile(path)) {
                return Optional.empty();
            }
            return Optional.of(new StoredImage(key, Files.readAllBytes(path), key.endsWith(".png") ? "image/png" : "image/jpeg"));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public void delete(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete old profile picture {}", key);
        }
    }

    /** Public URL the frontend uses for a stored picture (served by MediaController). */
    public static String urlFor(String key) {
        return key == null ? null : "/media/" + key;
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }

    /** Identifies the format from the file's first bytes. */
    static String detectFormat(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "png";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private static BufferedImage decode(byte[] data, String format) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
            if (!readers.hasNext()) {
                throw ApiException.badRequest("avatar", "That image format isn't supported. Upload a JPG or PNG.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                // Check the size from the header before decoding, so a tiny file can't expand into a huge bitmap
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w > MAX_DIMENSION || h > MAX_DIMENSION || (long) w * h > MAX_PIXELS) {
                    throw ApiException.badRequest("avatar", "That image is too large. Use one under " + MAX_DIMENSION + " × " + MAX_DIMENSION + " pixels.");
                }
                if (w < 32 || h < 32) {
                    throw ApiException.badRequest("avatar", "That image is too small. Use one at least 32 × 32 pixels.");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw ApiException.badRequest("avatar", "We couldn't read that image. It may be damaged; try another file.");
        }
    }

    private static BufferedImage cropAndScale(BufferedImage src) {
        int side = Math.min(src.getWidth(), src.getHeight());
        int x = (src.getWidth() - side) / 2;
        int y = (src.getHeight() - side) / 2;
        int out = Math.min(side, OUTPUT_SIZE);
        boolean alpha = src.getColorModel().hasAlpha();
        BufferedImage dst = new BufferedImage(out, out, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, out, out, x, y, x + side, y + side, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    private static byte[] writePng(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PNG encoding failed", e);
        }
    }

    private static byte[] writeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.88f);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("JPEG encoding failed", e);
        } finally {
            writer.dispose();
        }
    }

    private byte[] randomBytes() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        return b;
    }
}
