package movierecommender;

import com.google.gson.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public class DiscoveryPreferences {
    public final Map<Integer, Integer> feedback = new HashMap<>();
    public static DiscoveryPreferences load(String name, ArrayList<Movie> movies) throws IOException {
        DiscoveryPreferences result = new DiscoveryPreferences();
        if (!Files.exists(Path.of(name))) { return result; }
        try {
            JsonObject data = JsonParser.parseString(Files.readString(Path.of(name))).getAsJsonObject();
            for (var item : data.entrySet()) {
                int id = Integer.parseInt(item.getKey()), value = item.getValue().getAsInt();
                if ((value != -1 && value != 1) || movies.stream().noneMatch(m -> m.getId() == id)) { throw new IllegalArgumentException(); }
                result.feedback.put(id, value);
            }
        } catch (RuntimeException error) { throw new IOException("Invalid recommendation feedback file.", error); }
        return result;
    }
    public void save(String name) throws IOException {
        Path file = Path.of(name).toAbsolutePath(), temporary = Files.createTempFile(file.getParent(), "feedback-", ".tmp");
        try {
            Files.writeString(temporary, new Gson().toJson(feedback));
            try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
