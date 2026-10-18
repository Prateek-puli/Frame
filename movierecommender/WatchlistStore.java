package movierecommender;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class WatchlistStore {
    public static Watchlist load(String filename, ArrayList<Movie> movies) throws IOException {
        Watchlist watchlist = new Watchlist();
        Path file = Path.of(filename);
        if (!Files.exists(file)) { return watchlist; }
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals("movieId")) {
            throw new IOException("Expected watchlist header: movieId");
        }
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).trim().isEmpty()) { continue; }
            try {
                int id = Integer.parseInt(lines.get(i).trim());
                boolean found = false;
                for (Movie movie : movies) { if (movie.getId() == id) { found = true; break; } }
                if (!found || watchlist.contains(id)) {
                    throw new IllegalArgumentException("Unknown or duplicate movie ID: " + id);
                }
                watchlist.add(id);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Watchlist line " + (i + 1) + ": " + exception.getMessage(), exception);
            }
        }
        return watchlist;
    }

    public static void save(String filename, Watchlist watchlist) throws IOException {
        Path target = Path.of(filename).toAbsolutePath();
        ArrayList<String> lines = new ArrayList<>();
        lines.add("movieId");
        for (int id : watchlist.getMovieIds()) { lines.add(String.valueOf(id)); }
        Path temporary = Files.createTempFile(target.getParent(), "watchlist-", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temporary); }
    }
}
