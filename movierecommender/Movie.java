package movierecommender;

import java.util.ArrayList;

public class Movie {
    private String title;
    private int id;
    private int year;
    private String director;
    private ArrayList<String> genres;

    public Movie(int id, String title, String director, int year, String genre) {
        if (id <= 0 || year < 1888 || year > 2100) {
            throw new IllegalArgumentException("ID must be positive; year must be between 1888 and 2100.");
        }
        this.id = id;
        this.year = year;
        this.title = title;
        this.director = director;
        genres = new ArrayList<>();
        for (String value : genre.split("\\|", -1)) {
            String name = value.trim();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Each genre must have a name.");
            }
            boolean duplicate = false;
            for (String existing : genres) {
                if (existing.equalsIgnoreCase(name)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                genres.add(name);
            }
        }
    }

    public String getTitle() {
        return title;
    }

    public String getDirector() {
        return director;
    }

    public int getId() { return id; }

    public int getYear() { return year; }

    public String getDisplayTitle() {
        return title + " (" + year + ") [ID: " + id + "]";
    }

    public String getDetails() {
        return getDisplayTitle() + " | Director: " + director
                + " | Genres: " + String.join(", ", genres);
    }

    public String getGenre() {
        return String.join("|", genres);
    }

    public ArrayList<String> getGenres() {
        return new ArrayList<>(genres);
    }
}
