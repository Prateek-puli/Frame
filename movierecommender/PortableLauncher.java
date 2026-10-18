package movierecommender;

import java.nio.file.*;
import java.util.*;

/** Native launcher's entry point. All packaged paths are independent of working directory. */
public class PortableLauncher {
    public static void main(String[] args) throws Exception {
        Path app = Path.of(PortableLauncher.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        Path data = Path.of(System.getenv("LOCALAPPDATA"), "Frame");
        for (String arg : args) { if (arg.startsWith("--data-directory=")) { data = Path.of(arg.substring(17)); } }
        Files.createDirectories(data);
        System.setProperty("frame.appDir", app.toString());
        System.setProperty("frame.credentialDir", data.resolve("credentials").toString());
        ArrayList<String> options = new ArrayList<>();
        if (Arrays.stream(args).noneMatch(arg -> arg.startsWith("--movies="))) { options.add("--movies=" + app.resolve("movies.csv")); }
        String[][] files = {{"ratings", "ratings.csv"}, {"watchlist", "watchlist.csv"}, {"watched", "watched.csv"},
                {"taste", "taste-profile.json"}, {"dismissed", "not-interested.csv"}, {"feedback", "recommendation-feedback.json"}};
        for (String[] file : files) {
            Path target = data.resolve(file[1]), seed = app.resolve("seed").resolve(file[1]);
            if (!Files.exists(target) && Files.exists(seed)) { Files.copy(seed, target); }
            if (Arrays.stream(args).noneMatch(arg -> arg.startsWith("--" + file[0] + "="))) { options.add("--" + file[0] + "=" + target); }
        }
        Path credential = data.resolve("credentials/tmdb-token.dpapi"), seedToken = app.resolve("seed/tmdb-token.dpapi");
        if (!Files.exists(credential) && Files.exists(seedToken)) {
            Files.createDirectories(credential.getParent()); Files.copy(seedToken, credential);
        }
        options.addAll(Arrays.asList(args));
        DesktopLauncher.main(options.toArray(String[]::new));
    }
}
