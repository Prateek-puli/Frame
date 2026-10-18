package movierecommender;

public class Rating {
    private Movie movie;
    private int stars;

    public Rating(Movie movie, int stars) {
        this.movie = movie;
        setStars(stars);
    }

    public Movie getMovie() {
        return movie;
    }

    public int getStars() {
        return stars;
    }

    public void setStars(int stars) {
        if (stars < 1 || stars > 5) {
            throw new IllegalArgumentException("Ratings must be from 1 to 5.");
        }
        this.stars = stars;
    }
}
