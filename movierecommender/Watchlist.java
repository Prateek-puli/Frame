package movierecommender;

import java.util.ArrayList;

public class Watchlist {
    private ArrayList<Integer> movieIds = new ArrayList<>();

    public boolean contains(int movieId) { return movieIds.contains(movieId); }

    public void add(int movieId) {
        if (movieId <= 0) { throw new IllegalArgumentException("Movie ID must be positive."); }
        if (!contains(movieId)) { movieIds.add(movieId); }
    }

    public void remove(int movieId) { movieIds.remove(Integer.valueOf(movieId)); }

    public ArrayList<Integer> getMovieIds() { return new ArrayList<>(movieIds); }
}
