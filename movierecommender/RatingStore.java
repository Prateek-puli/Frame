package movierecommender;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class RatingStore {
    public static UserRatings load(String filename, ArrayList<Movie> movies) throws IOException {
        UserRatings ratings = new UserRatings();
        Path path = Paths.get(filename);
        if (!Files.exists(path)) { return ratings; }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.isEmpty() || !lines.get(0).equals("movieId,stars")) {
            throw new IOException("Expected ratings header: movieId,stars");
        }
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).trim().isEmpty()) { continue; }
            try {
                String[] fields = lines.get(i).split(",", -1);
                if (fields.length != 2) { throw new IllegalArgumentException("Expected two fields"); }
                int id = Integer.parseInt(fields[0].trim());
                int stars = Integer.parseInt(fields[1].trim());
                Movie found = null;
                for (Movie movie : movies) {
                    if (movie.getId() == id) { found = movie; break; }
                }
                if (found == null || ratings.hasRated(id)) {
                    throw new IllegalArgumentException("Unknown or duplicate movie ID: " + id);
                }
                ratings.rate(found, stars);
            } catch (IllegalArgumentException exception) {
                throw new IOException("Ratings line " + (i + 1) + ": " + exception.getMessage(), exception);
            }
        }
        return ratings;
    }

    public static void save(String filename, UserRatings ratings) throws IOException {
        Path target = Paths.get(filename).toAbsolutePath();
        ArrayList<String> lines = new ArrayList<>();
        lines.add("movieId,stars");
        for (Rating rating : ratings.getRatings()) {
            lines.add(rating.getMovie().getId() + "," + rating.getStars());
        }
        Path temporary = Files.createTempFile(target.getParent(), "ratings-", ".tmp");
        try {
            Files.write(temporary, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
