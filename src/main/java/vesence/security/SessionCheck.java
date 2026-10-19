package vesence.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * ВРЕМЕННО: проверка отключена, чтобы Minecraft запускался.
 * TODO: включить после того, как починим передачу proof из лаунчера.
 */
public final class SessionCheck {

    private static final String PUBLIC_KEY_B64 = "NdAh7YThIqcjXkfDMlUvcUN1YV5yBDXpWQLkFK8h6k8=";

    private static final boolean ENFORCE = false; // ← true = проверять по-настоящему

    private SessionCheck() {}

    public static boolean verify() {
        if (!ENFORCE) {
            System.err.println("[weppos] SessionCheck: protection disabled (ENFORCE=false)");
            return true;
        }

        byte[] raw = readRaw();
        if (raw == null || raw.length < 80) {
            System.err.println("[weppos] SessionCheck: no proof (raw=" + (raw == null ? "null" : raw.length) + ")");
            return false;
        }

        byte[] payload   = new byte[16];
        byte[] signature = new byte[64];
        System.arraycopy(raw, 0,  payload,    0, 16);
        System.arraycopy(raw, 16, signature,  0, 64);

        try {
            byte[] rawPub = Base64.getDecoder().decode(PUBLIC_KEY_B64);

            byte[] spki = new byte[44];
            spki[0]  = 0x30; spki[1]  = 0x2a;
            spki[2]  = 0x30; spki[3]  = 0x05;
            spki[4]  = 0x06; spki[5]  = 0x03;
            spki[6]  = 0x2b; spki[7]  = 0x65;
            spki[8]  = 0x70;
            spki[9]  = 0x03; spki[10] = 0x21;
            spki[11] = 0x00;
            System.arraycopy(rawPub, 0, spki, 12, 32);

            PublicKey pk = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(spki));

            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(pk);
            sig.update(payload);

            boolean ok = sig.verify(signature) && payload[0] == 1;
            System.err.println("[weppos] SessionCheck: verify=" + ok);
            return ok;
        } catch (Throwable t) {
            System.err.println("[weppos] SessionCheck: exception " + t);
            return false;
        }
    }

    private static byte[] readRaw() {
        String prop = System.getProperty("weppos.proof");
        System.err.println("[weppos-debug] -Dweppos.proof = " +
                (prop == null ? "NULL" : prop.substring(0, Math.min(20, prop.length())) + "..."));
        if (prop != null && !prop.isEmpty()) {
            try {
                return hexToBytes(prop);
            } catch (Throwable t) {
                System.err.println("[weppos-debug] prop hex invalid: " + t);
            }
        }

        try {
            String appdata = System.getenv("APPDATA");
            if (appdata != null) {
                Path p = Paths.get(appdata, "weppos", ".session");
                if (Files.exists(p)) {
                    String content = Files.readString(p).trim();
                    System.err.println("[weppos-debug] .session len=" + content.length());
                    return hexToBytes(content);
                } else {
                    System.err.println("[weppos-debug] .session not found at " + p);
                }
            }
        } catch (Throwable t) {
            System.err.println("[weppos-debug] .session read failed: " + t);
        }

        return null;
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        if ((len & 1) != 0) throw new IllegalArgumentException("odd hex length");
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("bad hex char");
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}