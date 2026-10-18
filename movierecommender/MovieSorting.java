package movierecommender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

public class MovieSorting {
    public static final String TITLE = "Title: A–Z";
    public static final String NEWEST = "Year: newest first";
    public static final String OLDEST = "Year: oldest first";
    public static final String RATING = "Your rating: highest first";

    public static void sort(ArrayList<Movie> movies, String order, UserRatings ratings) {
        Comparator<Movie> titles = Comparator.comparing(Movie::getTitle, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(Movie::getYear).thenComparingInt(Movie::getId);
        Comparator<Movie> comparator = titles;
        if (NEWEST.equals(order)) { comparator = Comparator.comparingInt(Movie::getYear).reversed().thenComparing(titles); }
        if (OLDEST.equals(order)) { comparator = Comparator.comparingInt(Movie::getYear).thenComparing(titles); }
        if (RATING.equals(order)) {
            HashMap<Integer, Integer> stars = new HashMap<>();
            for (Rating rating : ratings.getRatings()) { stars.put(rating.getMovie().getId(), rating.getStars()); }
            comparator = Comparator.comparingInt((Movie movie) -> stars.getOrDefault(movie.getId(), 0)).reversed().thenComparing(titles);
        }
        movies.sort(comparator);
    }
}
