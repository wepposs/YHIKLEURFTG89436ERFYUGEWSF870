import org.jcodec.api.FrameGrab;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Size;
import org.jcodec.containers.mp4.demuxer.MP4Demuxer;
import org.jcodec.scale.AWTUtil;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

/**
 * Dev-tool (НЕ часть мода): mp4 -> кадры JPEG + meta.txt для видео-фона меню.
 * Запуск:
 *   java -cp "<jcodec.jar>;<jcodec-javase.jar>" tools/BakeFrames.java \
 *        <src.mp4> <outDir> <width> <jpegQuality 0..1> <maxFps>
 * Кадры прореживаются до maxFps (если исходник быстрее). meta.txt: "count fps w h".
 */
public class BakeFrames {
    public static void main(String[] a) throws Exception {
        File src = new File(a[0]);
        File outDir = new File(a[1]);
        int OW = Integer.parseInt(a[2]);
        float Q = Float.parseFloat(a[3]);
        double maxFps = a.length > 4 ? Double.parseDouble(a[4]) : 30.0;
        outDir.mkdirs();

        double nativeFps = 30.0;
        int total = 0, srcW = 0, srcH = 0;
        try (SeekableByteChannel ch = NIOUtils.readableChannel(src)) {
            MP4Demuxer d = MP4Demuxer.createMP4Demuxer(ch);
            DemuxerTrackMeta m = d.getVideoTrack().getMeta();
            total = m.getTotalFrames();
            double dur = m.getTotalDuration();
            if (dur > 0 && total > 0) nativeFps = total / dur;
            try { Size s = m.getVideoCodecMeta().getSize(); srcW = s.getWidth(); srcH = s.getHeight(); } catch (Throwable ignored) {}
        }
        int stride = Math.max(1, (int) Math.round(nativeFps / maxFps));
        double effFps = nativeFps / stride;
        System.out.println("SRC res=" + srcW + "x" + srcH + " total=" + total
                + " nativeFps=" + String.format(java.util.Locale.ROOT, "%.3f", nativeFps)
                + " -> stride=" + stride + " effFps=" + String.format(java.util.Locale.ROOT, "%.3f", effFps));

        int n = 0, OH = 0, idx = 0;
        long t0 = System.currentTimeMillis();
        try (SeekableByteChannel ch = NIOUtils.readableChannel(src)) {
            FrameGrab g = FrameGrab.createFrameGrab(ch);
            Picture p;
            while ((p = g.getNativeFrame()) != null) {
                if (idx % stride == 0) {
                    BufferedImage bi = AWTUtil.toBufferedImage(p);
                    int ow = OW;
                    int oh = Math.max(2, Math.round(OW * (float) bi.getHeight() / bi.getWidth())) & ~1;
                    OH = oh;
                    BufferedImage out = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB);
                    Graphics2D gr = out.createGraphics();
                    gr.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    gr.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    gr.drawImage(bi, 0, 0, ow, oh, null);
                    gr.dispose();
                    writeJpeg(out, new File(outDir, String.format("f%04d.jpg", n)), Q);
                    n++;
                    if (n % 30 == 0) System.out.println("baked " + n + " (" + (System.currentTimeMillis() - t0) + "ms)");
                }
                idx++;
            }
        }
        try (PrintWriter pw = new PrintWriter(new File(outDir, "meta.txt"))) {
            pw.println(n + " " + String.format(java.util.Locale.ROOT, "%.5f", effFps) + " " + OW + " " + OH);
        }
        System.out.println("DONE frames=" + n + " effFps=" + String.format(java.util.Locale.ROOT, "%.3f", effFps)
                + " res=" + OW + "x" + OH + " in " + (System.currentTimeMillis() - t0) + "ms");
    }

    static void writeJpeg(BufferedImage img, File f, float q) throws IOException {
        Iterator<ImageWriter> it = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter w = it.next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(q);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(f)) {
            w.setOutput(ios);
            w.write(null, new IIOImage(img, null, null), p);
        }
        w.dispose();
    }
}
