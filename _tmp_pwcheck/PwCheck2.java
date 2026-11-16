import java.util.Base64;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class PwCheck2 {
    public static void main(String[] a) throws Exception {
        String stored = "pbkdf2$65536$X0U5rmcJRq47Ib9tB5hgzg==$Mvm+MJz7aTSq/7tQFo2XXw2RQA/J4xrrphbSlJsAQ7c=";
        String[] p = stored.split("\\$");
        int it = Integer.parseInt(p[1]);
        byte[] salt = Base64.getDecoder().decode(p[2]);
        byte[] exp = Base64.getDecoder().decode(p[3]);
        for (String cand : new String[] { "Verify@2026Aa", "Verify#2026Aa" }) {
            byte[] act = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(new PBEKeySpec(cand.toCharArray(), salt, it, exp.length * 8)).getEncoded();
            System.out.println(cand + " -> " + Arrays.equals(exp, act));
        }
    }
}
