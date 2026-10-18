package movierecommender;

import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public class TasteProfileStore {
    public static TasteProfile load(String filename, ArrayList<Movie> movies) throws IOException {
        if (!Files.exists(Path.of(filename))) { return new TasteProfile(); }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(Path.of(filename))).getAsJsonObject();
            TasteProfile profile = new TasteProfile();
            profile.setDismissed(json.get("dismissed").getAsBoolean());
            JsonObject choices = json.getAsJsonObject("favorites");
            if (choices.size() != 0 && choices.size() != 3) { throw new IllegalArgumentException(); }
            for (var entry : choices.entrySet()) {
                ArrayList<Integer> ids = new ArrayList<>();
                for (JsonElement value : entry.getValue().getAsJsonArray()) {
                    int id = value.getAsInt();
                    boolean valid = movies.stream().anyMatch(movie -> movie.getId() == id
                            && movie.getGenres().stream().anyMatch(genre -> genre.equals(entry.getKey())));
                    if (!valid || ids.contains(id)) { throw new IllegalArgumentException(); }
                    ids.add(id);
                }
                if (ids.size() != 3) { throw new IllegalArgumentException(); }
                profile.put(entry.getKey(), ids);
            }
            return profile;
        } catch (Exception exception) { throw new IOException("Taste profile is invalid. Your file has been preserved.", exception); }
    }
    public static void save(String filename, TasteProfile profile) throws IOException {
        Path target = Path.of(filename).toAbsolutePath();
        JsonObject json = new JsonObject(); json.addProperty("dismissed", profile.isDismissed());
        JsonObject favorites = new JsonObject();
        profile.getFavorites().forEach((genre, ids) -> { JsonArray values = new JsonArray(); ids.forEach(values::add); favorites.add(genre, values); });
        json.add("favorites", favorites);
        Path temporary = Files.createTempFile(target.getParent(), "taste-", ".tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(json));
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
