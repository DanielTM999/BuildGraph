package dtm.builder.build.resources;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/** Image decoding and deterministic, aspect preserving icon rendering. */
public final class IconConverter {
    private IconConverter() { }

    public static BufferedImage read(Path source) throws IOException {
        byte[] bytes = Files.readAllBytes(source);
        String ext = source.getFileName().toString().toLowerCase(Locale.ROOT);
        BufferedImage image;
        if (ext.endsWith(".ico")) image = readIco(bytes);
        else if (ext.endsWith(".icns")) image = readIcns(bytes);
        else if (ext.endsWith(".png") || ext.endsWith(".jpg") || ext.endsWith(".jpeg") || ext.endsWith(".bmp")) {
            boolean valid = ext.endsWith(".png") ? isPng(bytes)
                    : ext.endsWith(".bmp") ? bytes.length > 2 && bytes[0] == 'B' && bytes[1] == 'M'
                    : bytes.length > 2 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8;
            if (!valid) throw new IOException("Formato de icon nao corresponde a extensao: " + source);
            image = ImageIO.read(new ByteArrayInputStream(bytes));
        }
        else throw new IOException("Formato de icon nao suportado: " + source);
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0)
            throw new IOException("Imagem de icon invalida: " + source);
        return image;
    }

    private static BufferedImage readIco(byte[] bytes) throws IOException {
        if (bytes.length < 6 || bytes[0] != 0 || bytes[1] != 0 || bytes[2] != 1 || bytes[3] != 0)
            throw new IOException("ICO invalido");
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int count = Short.toUnsignedInt(b.getShort(4));
        BufferedImage best = null;
        int area = -1;
        for (int i = 0; i < count; i++) {
            int at = 6 + i * 16;
            if (at + 16 > bytes.length) throw new IOException("Tabela ICO truncada");
            long size = Integer.toUnsignedLong(b.getInt(at + 8));
            long offset = Integer.toUnsignedLong(b.getInt(at + 12));
            if (offset + size > bytes.length || size > Integer.MAX_VALUE) throw new IOException("Dados ICO truncados");
            byte[] entry = Arrays.copyOfRange(bytes, (int) offset, (int) (offset + size));
            BufferedImage image = isPng(entry) ? ImageIO.read(new ByteArrayInputStream(entry)) : readIcoDib(entry);
            if (image != null && image.getWidth() * image.getHeight() > area) {
                best = image;
                area = image.getWidth() * image.getHeight();
            }
        }
        if (best == null) throw new IOException("ICO sem entrada PNG ou DIB 24/32 bits legivel");
        return best;
    }

    private static BufferedImage readIcoDib(byte[] entry) throws IOException {
        if (entry.length < 40) return null;
        ByteBuffer b = ByteBuffer.wrap(entry).order(ByteOrder.LITTLE_ENDIAN);
        int header = b.getInt(0), width = b.getInt(4), doubledHeight = b.getInt(8);
        int bits = Short.toUnsignedInt(b.getShort(14));
        if (header < 40 || header > entry.length || width <= 0 || doubledHeight <= 0
                || (doubledHeight & 1) != 0 || (bits != 24 && bits != 32) || b.getInt(16) != 0) return null;
        int height = doubledHeight / 2;
        long rowSize = ((long) width * bits + 31) / 32 * 4;
        long maskRow = ((long) width + 31) / 32 * 4;
        long maskOffset = header + rowSize * height;
        if (maskOffset + maskRow * height > entry.length || width > 4096 || height > 4096) return null;
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        boolean hasAlpha = false;
        if (bits == 32) for (int y = 0; y < height && !hasAlpha; y++)
            for (int x = 0; x < width; x++) if ((entry[(int) (header + rowSize * y + x * 4 + 3)] & 255) != 0) {
                hasAlpha = true; break;
            }
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int at = (int) (header + rowSize * (height - 1 - y) + (long) x * bits / 8);
            int blue = entry[at] & 255, green = entry[at + 1] & 255, red = entry[at + 2] & 255;
            int alpha = bits == 32 && hasAlpha ? entry[at + 3] & 255 : 255;
            int maskAt = (int) (maskOffset + maskRow * (height - 1 - y) + x / 8);
            if (((entry[maskAt] >>> (7 - x % 8)) & 1) != 0) alpha = 0;
            result.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
        }
        return result;
    }

    private static BufferedImage readIcns(byte[] bytes) throws IOException {
        if (bytes.length < 8 || !new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("icns"))
            throw new IOException("ICNS invalido");
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        if (b.getInt(4) != bytes.length) throw new IOException("Tamanho ICNS invalido");
        BufferedImage best = null;
        for (int at = 8; at + 8 <= bytes.length;) {
            int size = b.getInt(at + 4);
            if (size < 8 || (long) at + size > bytes.length) throw new IOException("Entrada ICNS invalida");
            byte[] entry = Arrays.copyOfRange(bytes, at + 8, at + size);
            if (isPng(entry)) {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(entry));
                if (image != null && (best == null || image.getWidth() > best.getWidth())) best = image;
            }
            at += size;
        }
        if (best == null) throw new IOException("ICNS sem entrada PNG legivel");
        return best;
    }

    private static boolean isPng(byte[] data) {
        return data.length > 8 && data[0] == (byte) 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
    }

    public static byte[] png(BufferedImage source, int size) throws IOException {
        BufferedImage canvas = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        double scale = Math.min((double) size / source.getWidth(), (double) size / source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, (size - width) / 2, (size - height) / 2, width, height, null);
        } finally { graphics.dispose(); }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(canvas, "png", out)) throw new IOException("Encoder PNG indisponivel");
        return out.toByteArray();
    }
}
