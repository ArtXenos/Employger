import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.security.crypto.bcrypt.BCrypt;

/**
 * Reads one password from standard input, as Base64 of its UTF-8 bytes, and
 * prints its BCrypt hash in the format the app's BCryptPasswordEncoder
 * stores ($2a$, strength 10). Used by create-first-admin.ps1; the password
 * never appears on the command line. Base64 keeps it plain ASCII, so the
 * console's encoding can't change letters such as ñ on the way in. Run
 * with spring-security-crypto on the class path.
 */
public class HashPassword {

    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.US_ASCII));
        String line = in.readLine();
        // Drop anything that isn't Base64, such as a byte-order mark
        // PowerShell can put before piped text.
        String encoded = line == null ? "" : line.replaceAll("[^A-Za-z0-9+/=]", "");
        if (encoded.isEmpty()) {
            System.err.println("No password on standard input.");
            System.exit(1);
        }
        String password = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        System.out.println(BCrypt.hashpw(password, BCrypt.gensalt(10)));
    }
}
