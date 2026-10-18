package movierecommender;

import java.util.ArrayList;

// One rating per movie ID.
public class UserRatings {
    private ArrayList<Rating> ratings = new ArrayList<>();

    public void rate(Movie movie, int stars) {
        for (Rating rating : ratings) {
            if (rating.getMovie().getId() == movie.getId()) {
                rating.setStars(stars);
                return;
            }
        }
        ratings.add(new Rating(movie, stars));
    }

    public ArrayList<Rating> getRatings() {
        return new ArrayList<>(ratings);
    }

    public boolean hasRated(int movieId) {
        for (Rating rating : ratings) {
            if (rating.getMovie().getId() == movieId) { return true; }
        }
        return false;
    }
}
