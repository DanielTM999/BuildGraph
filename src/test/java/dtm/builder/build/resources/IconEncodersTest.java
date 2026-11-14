package dtm.builder.build.resources;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class IconEncodersTest {
    @Test void icoAndIcnsAreStructuredAndPreserveTransparency() throws Exception {
        BufferedImage source = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xffff0000);
        byte[] ico = IconEncoders.ico(source);
        ByteBuffer little = ByteBuffer.wrap(ico).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1, Short.toUnsignedInt(little.getShort(2)));
        assertEquals(7, Short.toUnsignedInt(little.getShort(4)));
        int offset = little.getInt(18);
        int length = little.getInt(14);
        BufferedImage first = ImageIO.read(new ByteArrayInputStream(ico, offset, length));
        assertEquals(0, first.getRGB(8, 0) >>> 24);
        byte[] icns = IconEncoders.icns(source);
        assertEquals("icns", new String(icns, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(icns.length, ByteBuffer.wrap(icns).getInt(4));
        assertEquals("icp4", new String(icns, 8, 4, java.nio.charset.StandardCharsets.US_ASCII));
    }

    @Test void windowsVersionScriptKeepsTextAndNumericVersionsDistinct() {
        String script = WindowsResourceProvider.script(true, java.util.Map.of(
                "fileVersion", "1.0.0-beta", "productName", "Máquina \"Virtual\""));
        assertTrue(script.contains("FILEVERSION 1,0,0,0"));
        assertTrue(script.contains("1 ICON \"icon.ico\""));
        assertTrue(script.contains("Máquina \\\"Virtual\\\""));
    }
}
