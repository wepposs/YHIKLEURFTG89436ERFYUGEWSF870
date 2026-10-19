package vesence.utils.render.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.FloatControl.Type;
import javax.sound.sampled.LineEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;

@Environment(EnvType.CLIENT)
public class SoundUtil {
   private static final String[] FORMATS = {".ogg", ".wav"};
   private static final int MAX_CLIPS = 16;

   private static volatile Clip currentClip = null;
   private static final CopyOnWriteArrayList<Clip> CLIPS_LIST = new CopyOnWriteArrayList<>();

   private static final java.util.concurrent.ConcurrentHashMap<String, Long> LAST_PLAY = new java.util.concurrent.ConcurrentHashMap<>();
   private static final java.util.concurrent.ExecutorService AUDIO_EXEC =
         java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "vesence-Audio");
            t.setDaemon(true);
            return t;
         });
   private static final long DEFAULT_MIN_INTERVAL_MS = 55L;

   public static void playUi(String location, float volume, long minIntervalMs) {
      long now = System.currentTimeMillis();
      Long last = LAST_PLAY.get(location);
      if (last != null && now - last < minIntervalMs) {
         return;
      }
      LAST_PLAY.put(location, now);
      try {
         AUDIO_EXEC.execute(() -> playSound_wav(location, volume));
      } catch (Exception ignored) {
      }
   }

   public static void playUi(String location, float volume) {
      playUi(location, volume, DEFAULT_MIN_INTERVAL_MS);
   }

   public static void playSound_mp3(String sound, float value, boolean nonstop) {
      stopCurrent();

      float normalized = value > 1.0F ? value / 100.0F : value;
      Clip clip = loadClip(stripExtension(sound));
      if (clip == null) {
         return;
      }

      try {
         applyVolume(clip, normalized);
         if (nonstop) {
            clip.addLineListener(event -> {
               if (event.getType() == LineEvent.Type.STOP && clip == currentClip) {
                  clip.setFramePosition(0);
                  clip.start();
               }
            });
            clip.loop(Clip.LOOP_CONTINUOUSLY);
         } else {
            clip.start();
         }
         currentClip = clip;
      } catch (Exception e) {
         safeClose(clip);
      }
   }

   public static void playSound_wav(String location, float volume) {
      pruneFinishedClips();

      Clip clip = loadClip(location);
      if (clip == null) {
         return;
      }

      try {
         applyVolume(clip, volume);
         clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP) {
               safeClose(clip);
               CLIPS_LIST.remove(clip);
            }
         });
         clip.start();
         CLIPS_LIST.add(clip);
      } catch (Exception e) {
         safeClose(clip);
      }
   }

   /** Регулирует громкость текущего (зацикленного) трека в рантайме, 0..1. */
   public static void setCurrentVolume(float volume) {
      Clip clip = currentClip;
      if (clip != null) {
         try {
            applyVolume(clip, volume);
         } catch (Exception ignored) {
         }
      }
   }

   public static void stopCurrent() {
      Clip clip = currentClip;
      currentClip = null;
      if (clip != null) {
         try {
            clip.stop();
         } catch (Exception ignored) {
         }
         safeClose(clip);
      }
   }

   public static void stopAll() {
      stopCurrent();
      for (Clip clip : CLIPS_LIST) {
         try {
            clip.stop();
         } catch (Exception ignored) {
         }
         safeClose(clip);
      }
      CLIPS_LIST.clear();
   }

   private static Clip loadClip(String location) {
      for (String format : FORMATS) {
         String path = "/assets/vesence/sounds/" + location + format;
         InputStream stream = SoundUtil.class.getResourceAsStream(path);
         if (stream == null) {
            continue;
         }
         try (InputStream in = stream) {
            if (".ogg".equals(format)) {
               return openOggClip(in);
            }
            return openPcmClip(in);
         } catch (Exception e) {

         }
      }
      return null;
   }

   private static Clip openPcmClip(InputStream rawStream) throws Exception {
      try (BufferedInputStream bis = new BufferedInputStream(rawStream);
           AudioInputStream audioStream = AudioSystem.getAudioInputStream(bis)) {
         Clip clip = AudioSystem.getClip();
         clip.open(audioStream);
         return clip;
      }
   }

   private static Clip openOggClip(InputStream rawStream) throws Exception {
      byte[] fileBytes = readAll(rawStream);
      ByteBuffer encoded = MemoryUtil.memAlloc(fileBytes.length);
      ShortBuffer pcm = null;
      try {
         encoded.put(fileBytes);
         encoded.flip();

         try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channelsBuf = stack.mallocInt(1);
            IntBuffer sampleRateBuf = stack.mallocInt(1);

            pcm = STBVorbis.stb_vorbis_decode_memory(encoded, channelsBuf, sampleRateBuf);
            if (pcm == null) {
               return null;
            }

            int channels = channelsBuf.get(0);
            int sampleRate = sampleRateBuf.get(0);
            if (channels <= 0 || sampleRate <= 0) {
               return null;
            }

            int sampleCount = pcm.remaining();
            byte[] pcmBytes = new byte[sampleCount * 2];
            ByteBuffer wrapper = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < sampleCount; i++) {
               wrapper.putShort(pcm.get(i));
            }

            AudioFormat audioFormat = new AudioFormat(
                  sampleRate,
                  16,
                  channels,
                  true,
                  false
            );

            Clip clip = AudioSystem.getClip();
            clip.open(audioFormat, pcmBytes, 0, pcmBytes.length);
            return clip;
         }
      } finally {
         if (pcm != null) {

            LibCStdlib.free(pcm);
         }
         MemoryUtil.memFree(encoded);
      }
   }

   private static void applyVolume(Clip clip, float volume) {
      float clamped = volume < 0.0F ? 0.0F : (volume > 1.0F ? 1.0F : volume);
      if (!clip.isControlSupported(Type.MASTER_GAIN)) {
         return;
      }
      FloatControl control = (FloatControl) clip.getControl(Type.MASTER_GAIN);
      float gain;
      if (clamped <= 0.0001F) {
         gain = control.getMinimum();
      } else {
         gain = (float) (Math.log10(clamped) * 20.0);
         gain = Math.max(control.getMinimum(), Math.min(control.getMaximum(), gain));
      }
      control.setValue(gain);
   }

   private static void pruneFinishedClips() {
      for (Clip clip : CLIPS_LIST) {
         if (clip != null && !clip.isRunning()) {
            safeClose(clip);
            CLIPS_LIST.remove(clip);
         }
      }
      while (CLIPS_LIST.size() > MAX_CLIPS) {
         Clip oldest = CLIPS_LIST.remove(0);
         safeClose(oldest);
      }
   }

   private static void safeClose(Clip clip) {
      if (clip != null) {
         try {
            if (clip.isOpen()) {
               clip.close();
            }
         } catch (Exception ignored) {
         }
      }
   }

   private static byte[] readAll(InputStream in) throws Exception {
      ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(16, in.available()));
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) {
         out.write(buffer, 0, read);
      }
      return out.toByteArray();
   }

   private static String stripExtension(String name) {
      int dot = name.lastIndexOf('.');
      return dot > 0 ? name.substring(0, dot) : name;
   }
}
