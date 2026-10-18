package movierecommender;

public record MovieMedia(String overview, String posterUrl, String message, int runtime) {
    public MovieMedia(String overview, String posterUrl, String message) { this(overview, posterUrl, message, 0); }
    public static MovieMedia unavailable(String message) { return new MovieMedia("", "", message); }
}
