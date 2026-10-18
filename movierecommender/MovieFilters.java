package movierecommender;

public class MovieFilters {
    public static Integer parseYear(String value) {
        if (value.trim().isEmpty()) { return null; }
        try {
            int year = Integer.parseInt(value.trim());
            if (year < 1888 || year > 2100) { throw new NumberFormatException(); }
            return year;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Use whole years from 1888 to 2100, or leave blank.");
        }
    }

    public static boolean matches(Movie movie, String genre, Integer fromYear, Integer toYear) {
        if (fromYear != null && toYear != null && fromYear > toYear) {
            throw new IllegalArgumentException("From year must be no later than To year.");
        }
        if (fromYear != null && movie.getYear() < fromYear) { return false; }
        if (toYear != null && movie.getYear() > toYear) { return false; }
        if (genre == null || genre.equals("All genres")) { return true; }
        for (String value : movie.getGenres()) { if (value.equalsIgnoreCase(genre)) { return true; } }
        return false;
    }
}
