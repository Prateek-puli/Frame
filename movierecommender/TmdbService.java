package movierecommender;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.Normalizer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

// Background lookups and bounded session-only metadata caching. Never stores a token.
public class TmdbService implements AutoCloseable {
    private final String token;
    private final URI api;
    private final Map<Integer, String> imdbIds;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(48), task -> { Thread thread = new Thread(task, "movie-media"); thread.setDaemon(true); return thread; });
    private final LinkedHashMap<Integer, CompletableFuture<MovieMedia>> cache = new LinkedHashMap<>(128, 0.75f, true);
    private volatile JsonObject imageConfiguration;
    private final Object configurationLock = new Object();

    public TmdbService(String token, Map<Integer, String> imdbIds) {
        this(token, imdbIds, URI.create("https://api.themoviedb.org/3/"));
    }

    // Package-private injection for loopback fixture tests, not an app setting.
    TmdbService(String token, Map<Integer, String> imdbIds, URI api) {
        if (!api.toString().equals("https://api.themoviedb.org/3/")
                && !(api.getScheme().equals("http") && api.getHost().equals("127.0.0.1"))) {
            throw new IllegalArgumentException("Unsupported API endpoint.");
        }
        this.token = token == null ? "" : token.trim();
        this.imdbIds = new HashMap<>(imdbIds);
        this.api = api;
    }

    public boolean isConfigured() { return !token.isEmpty(); }

    public synchronized CompletableFuture<MovieMedia> lookup(Movie movie) {
        if (!isConfigured()) {
            return CompletableFuture.completedFuture(MovieMedia.unavailable("Connect TMDB for posters and descriptions."));
        }
        if (cache.containsKey(movie.getId())) { return cache.get(movie.getId()); }
        CompletableFuture<MovieMedia> future;
        try {
            future = CompletableFuture.supplyAsync(() -> {
                try { return resolve(movie); }
                catch (ApiFailure failure) { return MovieMedia.unavailable(failure.getMessage()); }
                catch (Exception exception) { return MovieMedia.unavailable("Movie info is unavailable right now. Check your connection or reconnect TMDB."); }
            }, workers);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.completedFuture(MovieMedia.unavailable("Movie info is busy. Select this film again in a moment."));
        }
        cache.put(movie.getId(), future);
        while (cache.size() > 128) { cache.remove(cache.keySet().iterator().next()); }
        return future;
    }

    private MovieMedia resolve(Movie movie) throws Exception {
        JsonObject details = null;
        String imdb = imdbIds.get(movie.getId());
        if (imdb != null) {
            JsonArray found = array(get("find/" + imdb + "?external_source=imdb_id&language=en-US"), "movie_results");
            if (found.size() == 1) { details = get("movie/" + found.get(0).getAsJsonObject().get("id").getAsInt() + "?language=en-US"); }
        }
        if (details == null) {
            JsonArray found = array(get("search/movie?query=" + URLEncoder.encode(movie.getTitle(), StandardCharsets.UTF_8)
                    + "&include_adult=false&language=en-US"), "results");
            ArrayList<JsonObject> confirmed = new ArrayList<>();
            int checked = 0;
            for (JsonElement item : found) {
                JsonObject result = item.getAsJsonObject();
                String date = text(result, "release_date");
                if (date.length() < 4 || (!normalize(text(result, "title")).equals(normalize(movie.getTitle()))
                        && !normalize(text(result, "original_title")).equals(normalize(movie.getTitle())))) { continue; }
                int year;
                try { year = Integer.parseInt(date.substring(0, 4)); }
                catch (NumberFormatException exception) { continue; }
                if (Math.abs(year - movie.getYear()) > 1) { continue; }
                if (++checked > 4) { return MovieMedia.unavailable("Several films match this title. Poster withheld to avoid a wrong match."); }
                JsonObject candidate = get("movie/" + result.get("id").getAsInt() + "?append_to_response=credits&language=en-US");
                for (JsonElement credit : array(candidate.getAsJsonObject("credits"), "crew")) {
                    JsonObject person = credit.getAsJsonObject();
                    if (text(person, "job").equals("Director") && normalize(text(person, "name")).equals(normalize(movie.getDirector()))) {
                        confirmed.add(candidate); break;
                    }
                }
            }
            if (confirmed.size() != 1) {
                return MovieMedia.unavailable(confirmed.isEmpty() ? "No verified TMDB match for this film." : "Ambiguous film match. Poster withheld.");
            }
            details = confirmed.get(0);
        }
        int runtime = details.has("runtime") && !details.get("runtime").isJsonNull() ? details.get("runtime").getAsInt() : 0;
        return new MovieMedia(text(details, "overview"), posterUrl(text(details, "poster_path")), "", Math.max(0, runtime));
    }

    private String posterUrl(String posterPath) throws Exception {
        if (posterPath.isEmpty()) { return ""; }
        if (!posterPath.matches("/[A-Za-z0-9_-]+\\.(jpg|png|webp)")) { return ""; }
        if (imageConfiguration == null) {
            synchronized (configurationLock) {
                if (imageConfiguration == null) { imageConfiguration = get("configuration").getAsJsonObject("images"); }
            }
        }
        String base = text(imageConfiguration, "secure_base_url");
        if (!base.equals("https://image.tmdb.org/t/p/")) { throw new IOException("Unexpected image host"); }
        ArrayList<String> sizes = new ArrayList<>();
        for (JsonElement size : array(imageConfiguration, "poster_sizes")) { sizes.add(size.getAsString()); }
        String selected = sizes.contains("w342") ? "w342" : sizes.contains("w185") ? "w185" : "";
        return selected.isEmpty() ? "" : base + selected + posterPath;
    }

    private JsonObject get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(api.resolve(path)).timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token).header("Accept", "application/json").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 401 || response.statusCode() == 403) { throw new ApiFailure("TMDB token was rejected. Reconnect with your API Read Access Token."); }
        if (response.statusCode() == 429) { throw new ApiFailure("TMDB is limiting requests. Wait a moment, then reconnect."); }
        if (response.statusCode() != 200) { throw new ApiFailure("TMDB is unavailable right now. Try again later."); }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    public CompletableFuture<String> verifyConnection() {
        return CompletableFuture.supplyAsync(() -> {
            try { get("configuration"); return ""; }
            catch (ApiFailure failure) { return failure.getMessage(); }
            catch (Exception exception) { return "Could not connect to TMDB. Check your internet connection."; }
        }, workers);
    }

    static JsonArray array(JsonObject object, String key) {
        return object != null && object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : new JsonArray();
    }

    static String text(JsonObject object, String key) {
        return object != null && object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public static Map<Integer, String> loadImdbIds(Path path) {
        HashMap<Integer, String> ids = new HashMap<>();
        if (!Files.exists(path)) { return ids; }
        try {
            JsonObject mapping = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : mapping.entrySet()) {
                if (entry.getKey().matches("tt[0-9]+")) { ids.put(entry.getValue().getAsInt(), entry.getKey()); }
            }
        } catch (Exception exception) { ids.clear(); }
        return ids;
    }

    @Override public void close() { workers.shutdownNow(); client.shutdownNow(); }

    private static class ApiFailure extends IOException { ApiFailure(String message) { super(message); } }
}
