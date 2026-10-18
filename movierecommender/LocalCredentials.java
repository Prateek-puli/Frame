package movierecommender;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

// Windows DPAPI: the encrypted credential is bound to this Windows user.
public class LocalCredentials {
    static String lastError = "";
    public static String load() {
        try { return invoke("Read", "").trim(); }
        catch (Exception exception) { lastError = exception.getClass().getSimpleName() + ": " + exception.getMessage(); return ""; }
    }

    public static void save(String token) throws IOException {
        try { invoke("Save", token); }
        catch (Exception exception) { throw new IOException("Could not remember the TMDB token on this computer."); }
    }

    private static String invoke(String mode, String input) throws Exception {
        java.util.ArrayList<String> command = new java.util.ArrayList<>(java.util.List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                "-ExecutionPolicy", "Bypass", "-File", Path.of(System.getProperty("frame.appDir", "."), "tools/tmdb-credential.ps1").toAbsolutePath().toString(), "-Mode", mode));
        if (System.getProperty("frame.credentialDir") != null) { command.add("-CredentialDirectory"); command.add(System.getProperty("frame.credentialDir")); }
        Process process = new ProcessBuilder(command).start();
        try {
            try (var stdin = process.getOutputStream()) { stdin.write(input.getBytes(StandardCharsets.UTF_8)); }
            if (!process.waitFor(10, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IOException("Credential helper timed out."); }
            if (process.exitValue() != 0) {
                String diagnostic = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                throw new IOException("Credential helper failed with exit code " + process.exitValue() + ". " + diagnostic);
            }
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } finally { if (process.isAlive()) { process.destroyForcibly(); } }
    }
}
