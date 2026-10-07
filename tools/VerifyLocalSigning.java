import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.*;

/** Read-only configuration check. Never prints passwords, aliases or key material. */
class VerifyLocalSigning {
    public static void main(String[] args) throws Exception {
        Properties p = new Properties();
        try (var in = Files.newInputStream(Path.of("keystore.properties"))) { p.load(in); }
        for (String key : List.of("storeFile", "storePassword", "keyAlias", "keyPassword")) {
            if (p.getProperty(key, "").isBlank()) throw new IllegalStateException("Signing configuration incomplete");
        }
        Path path = Path.of(p.getProperty("storeFile"));
        byte[] before = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        KeyStore store = KeyStore.getInstance(path.toFile(), p.getProperty("storePassword").toCharArray());
        String alias = p.getProperty("keyAlias");
        if (!(store.getKey(alias, p.getProperty("keyPassword").toCharArray()) instanceof PrivateKey)) {
            throw new IllegalStateException("Signing entry is not a private key");
        }
        ((X509Certificate) store.getCertificate(alias)).checkValidity();
        byte[] after = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        if (!Arrays.equals(before, after)) throw new IllegalStateException("Keystore changed");
        System.out.println("Release signing configuration: valid private key and current certificate; keystore unchanged.");
        System.out.println("Keystore SHA-256: " + HexFormat.of().formatHex(after));
    }
}
