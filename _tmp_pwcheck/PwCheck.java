import java.util.Base64;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PwCheck {
    public static void main(String[] a) throws Exception {
        String stored = "pbkdf2$65536$/w1hEvkXgtGrrB/tJ59Pow==$P0qypwTMntvByZtVS21CL7HyYN2lsJPWmsAnr6m1S6s=";
        String[] p = stored.split("\\$");
        System.out.println("parts=" + p.length + " iter=" + p[1]);
        int it = Integer.parseInt(p[1]);
        byte[] salt = Base64.getDecoder().decode(p[2]);
        byte[] exp = Base64.getDecoder().decode(p[3]);
        for (String cand : new String[] { "Verify@2026Aa", "Verify#2026Aa", "admin123" }) {
            byte[] act = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(cand.toCharArray(), salt, it, exp.length * 8)).getEncoded();
            System.out.println(cand + " -> " + Arrays.equals(exp, act));
        }
    }
}
