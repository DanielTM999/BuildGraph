package dtm.builder.build.resources;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

public final class IconEncoders {
    private IconEncoders() { }
    public static byte[] ico(BufferedImage image) throws IOException {
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        byte[][] pngs = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) pngs[i] = IconConverter.png(image, sizes[i]);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        le16(out, 0); le16(out, 1); le16(out, sizes.length);
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            out.writeByte(sizes[i] == 256 ? 0 : sizes[i]);
            out.writeByte(sizes[i] == 256 ? 0 : sizes[i]);
            out.writeByte(0); out.writeByte(0);
            le16(out, 1); le16(out, 32);
            le32(out, pngs[i].length); le32(out, offset);
            offset += pngs[i].length;
        }
        for (byte[] png : pngs) out.write(png);
        return bytes.toByteArray();
    }
    public static byte[] icns(BufferedImage image) throws IOException {
        List<String> types = List.of("icp4", "icp5", "icp6", "ic07", "ic08", "ic09", "ic10", "ic11", "ic12", "ic13", "ic14");
        int[] sizes = {16, 32, 64, 128, 256, 512, 1024, 32, 64, 256, 512};
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(body);
        for (int i = 0; i < sizes.length; i++) {
            byte[] png = IconConverter.png(image, sizes[i]);
            data.writeBytes(types.get(i)); data.writeInt(png.length + 8); data.write(png);
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(result);
        out.writeBytes("icns"); out.writeInt(body.size() + 8); out.write(body.toByteArray());
        return result.toByteArray();
    }
    private static void le16(DataOutputStream out, int v) throws IOException { out.writeByte(v); out.writeByte(v >>> 8); }
    private static void le32(DataOutputStream out, int v) throws IOException {
        out.writeByte(v); out.writeByte(v >>> 8); out.writeByte(v >>> 16); out.writeByte(v >>> 24);
    }
}
