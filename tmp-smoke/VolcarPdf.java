import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
public class VolcarPdf {
    public static void main(String[] args) throws Exception {
        String t = new String(Files.readAllBytes(Path.of(args[0])), StandardCharsets.ISO_8859_1);
        System.out.println("LONGITUD: " + t.length());
        System.out.println("=== ÚLTIMOS 500 CARACTERES ===");
        System.out.println(t.substring(Math.max(0, t.length() - 500)).replace("\n", "\n\n"));
    }
}
