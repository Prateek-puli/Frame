package movierecommender;

import java.util.ArrayList;
import java.util.Locale;

public class RecommendationEngine {
    // Director and genre are stronger signals; nearby years add only a small bonus.
    public static final int DIRECTOR_POINTS = 3;
    public static final int GENRE_POINTS = 2;
    public static final int YEAR_POINTS = 1;
    public static final int YEAR_WINDOW = 5;
    public static final int MAX_RESULTS = 5;

    private ArrayList<Movie> movies = new ArrayList<>();

    public void addMovie(Movie movie) {
        movies.add(movie);
    }

    public ArrayList<Movie> search(String query) {
        query = query.trim();
        ArrayList<Movie> exactMatches = new ArrayList<>();
        ArrayList<Movie> partialMatches = new ArrayList<>();
        if (query.isEmpty()) {
            return exactMatches;
        }
        String search = query.toLowerCase(Locale.ROOT);
        for (Movie movie : movies) {
            if (String.valueOf(movie.getId()).equals(query)) {
                ArrayList<Movie> idMatch = new ArrayList<>();
                idMatch.add(movie);
                return idMatch;
            }
            if (movie.getTitle().equalsIgnoreCase(query)
                    || (movie.getTitle() + " (" + movie.getYear() + ")").equalsIgnoreCase(query)) {
                exactMatches.add(movie);
            }
            if (movie.getTitle().toLowerCase(Locale.ROOT).contains(search)) {
                partialMatches.add(movie);
            }
        }
        return exactMatches.isEmpty() ? partialMatches : exactMatches;
    }

    public ArrayList<Recommendation> getRecommendations(Movie selected) {
        return topResults(getAllRecommendations(selected));
    }

    private ArrayList<Recommendation> getAllRecommendations(Movie selected) {
        ArrayList<Recommendation> recommendations = new ArrayList<>();
        for (Movie movie : movies) {
            if (movie.getId() == selected.getId()) {
                continue;
            }
            Recommendation recommendation = scorePair(selected, movie);
            if (recommendation != null) { recommendations.add(recommendation); }
        }
        return recommendations;
    }

    private Recommendation scorePair(Movie selected, Movie movie) {
            int score = 0;
            ArrayList<String> reasons = new ArrayList<>();
            if (movie.getDirector().equalsIgnoreCase(selected.getDirector())) {
                score += DIRECTOR_POINTS;
                reasons.add("Same director: " + selected.getDirector()
                        + " (+" + DIRECTOR_POINTS + ")");
            }
            for (String genre : selected.getGenres()) {
                for (String candidateGenre : movie.getGenres()) {
                    if (genre.equalsIgnoreCase(candidateGenre)) {
                        score += GENRE_POINTS;
                        reasons.add("Shared genre: " + genre + " (+" + GENRE_POINTS + ")");
                        break;
                    }
                }
            }
            // A nearby year alone is not enough to recommend an unrelated movie.
            if (score == 0) {
                return null;
            }
            int yearDifference = Math.abs(movie.getYear() - selected.getYear());
            if (yearDifference <= YEAR_WINDOW) {
                score += YEAR_POINTS;
                reasons.add("Release years " + yearDifference + " apart (within "
                        + YEAR_WINDOW + " years, +" + YEAR_POINTS + ")");
            }
            Recommendation result = new Recommendation(
                    movie, score, String.join("; ", reasons), maximumScore(selected));
            ArrayList<String> shortReasons = new ArrayList<>();
            if (movie.getDirector().equalsIgnoreCase(selected.getDirector())) { shortReasons.add("Same director"); }
            for (String genre : selected.getGenres()) {
                for (String other : movie.getGenres()) {
                    if (genre.equalsIgnoreCase(other)) { shortReasons.add(genre); break; }
                }
            }
            if (yearDifference <= YEAR_WINDOW) { shortReasons.add("Nearby release year"); }
            result.setPresentation(result.getMatchFraction(), String.join(" · ", shortReasons), reasons);
            return result;
    }

    private int maximumScore(Movie movie) {
        return DIRECTOR_POINTS + GENRE_POINTS * movie.getGenres().size() + YEAR_POINTS;
    }

    private ArrayList<Recommendation> topResults(ArrayList<Recommendation> recommendations) {
        recommendations.sort((first, second) -> Integer.compare(second.getScore(), first.getScore()));
        ArrayList<Recommendation> top = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_RESULTS, recommendations.size()); i++) {
            top.add(recommendations.get(i));
        }
        return top;
    }

    public ArrayList<Recommendation> getPersonalizedRecommendations(UserRatings ratings) {
        return getPersonalizedRecommendations(ratings, java.util.Set.of());
    }

    public ArrayList<Recommendation> getPersonalizedRecommendations(UserRatings ratings, java.util.Set<Integer> favorites) {
        return getPersonalizedRecommendations(ratings, favorites, false);
    }

    public ArrayList<Recommendation> getPersonalizedRecommendations(UserRatings ratings, java.util.Set<Integer> favorites, boolean explore) {
        return personalize(ratings, favorites, explore, java.util.Set.of(), java.util.Set.of(), false);
    }

    public ArrayList<Recommendation> discover(UserRatings ratings, java.util.Set<Integer> favorites, boolean explore,
            java.util.Set<Integer> excluded, java.util.Set<String> mood) {
        return personalize(ratings, favorites, explore, excluded, mood, false);
    }

    public ArrayList<Recommendation> discover(UserRatings ratings, java.util.Set<Integer> favorites, boolean explore,
            java.util.Set<Integer> excluded, java.util.Set<String> mood, java.util.Set<Integer> disliked) {
        negativeFeedback = disliked;
        try { return personalize(ratings, favorites, explore, excluded, mood, false); }
        finally { negativeFeedback = java.util.Set.of(); }
    }

    private java.util.Set<Integer> negativeFeedback = java.util.Set.of();
    private double discoveryRank(Recommendation result) {
        double penalty = 0;
        for (Movie movie : movies) { if (negativeFeedback.contains(movie.getId())) {
            Recommendation similarity = scorePair(movie, result.getMovie());
            if (similarity != null) { penalty = Math.max(penalty, similarity.getMatchFraction() * 0.2); }
        } }
        return result.getMatchFraction() - penalty;
    }

    public java.util.Map<Integer, Recommendation> matchScores(UserRatings ratings, java.util.Set<Integer> favorites) {
        java.util.Map<Integer, Recommendation> scores = new java.util.HashMap<>();
        for (Recommendation result : personalize(ratings, favorites, false, java.util.Set.of(), java.util.Set.of(), true)) {
            scores.put(result.getMovie().getId(), result);
        }
        return scores;
    }

    private ArrayList<Recommendation> personalize(UserRatings ratings, java.util.Set<Integer> favorites, boolean explore,
            java.util.Set<Integer> excluded, java.util.Set<String> mood, boolean all) {
        UserRatings preferences = new UserRatings();
        for (Rating rating : ratings.getRatings()) { preferences.rate(rating.getMovie(), rating.getStars()); }
        for (Movie movie : movies) { if (favorites.contains(movie.getId()) && !preferences.hasRated(movie.getId())) { preferences.rate(movie, 4); } }
        ArrayList<Recommendation> results = new ArrayList<>();
        for (Movie candidate : movies) {
            if ((!all && preferences.hasRated(candidate.getId())) || excluded.contains(candidate.getId())) { continue; }
            if (!mood.isEmpty() && candidate.getGenres().stream().noneMatch(mood::contains)) { continue; }
            ArrayList<FavoriteMatch> matches = new ArrayList<>();
            for (Rating rating : preferences.getRatings()) {
                if (rating.getStars() < 4) { continue; }
                if (rating.getMovie().getId() == candidate.getId()) { continue; }
                Recommendation match = scorePair(rating.getMovie(), candidate);
                if (match != null) { matches.add(new FavoriteMatch(rating, match)); }
            }
            matches.sort((first, second) -> Double.compare(second.match.getMatchFraction(), first.match.getMatchFraction()));
            int total = 0;
            int possibleTotal = 0;
            int totalWeight = 0;
            double weightedSimilarity = 0;
            ArrayList<String> reasons = new ArrayList<>();
            ArrayList<String> sources = new ArrayList<>();
            for (int i = 0; i < Math.min(3, matches.size()); i++) {
                FavoriteMatch favorite = matches.get(i);
                int weight = favorite.rating.getStars() - 3;
                total += favorite.match.getScore() * weight;
                possibleTotal += maximumScore(favorite.rating.getMovie()) * weight;
                totalWeight += weight;
                weightedSimilarity += favorite.match.getMatchFraction() * weight;
                sources.add(favorite.rating.getMovie().getTitle());
                reasons.add(favorite.rating.getMovie().getTitle() + " (" + favorite.rating.getMovie().getYear()
                        + ") — " + favorite.match.getMatchPercentage() + "% similarity\n"
                        + favorite.match.getSummary() + (ratings.hasRated(favorite.rating.getMovie().getId())
                                ? ". Your rating: " + favorite.rating.getStars() + "/5." : ". Favorite from taste setup or recommendation feedback."));
            }
            if (total > 0) {
                Recommendation result = new Recommendation(candidate, total, String.join(" | ", reasons), possibleTotal);
                ArrayList<String> highlights = new ArrayList<>();
                for (FavoriteMatch favorite : matches) {
                    Movie seed = favorite.rating.getMovie();
                    if (candidate.getDirector().equalsIgnoreCase(seed.getDirector())) {
                        String line = "Same director as " + seed.getTitle();
                        if (!highlights.contains(line)) { highlights.add(line); }
                    }
                    for (String genre : candidate.getGenres()) {
                        if (seed.getGenres().contains(genre)) {
                            String line = "Matches your " + genre + " favorites";
                            if (!highlights.contains(line)) { highlights.add(line); }
                        }
                    }
                    if (highlights.size() >= 2) { break; }
                }
                result.setPresentation(weightedSimilarity / totalWeight,
                        String.join("\n", highlights.subList(0, Math.min(2, highlights.size()))), reasons);
                results.add(result);
            }
        }
        results.sort((first, second) -> Double.compare(discoveryRank(second), discoveryRank(first)));
        if (all) { return results; }
        if (explore && results.size() > MAX_RESULTS) {
            // Explore beyond the top five while keeping the actual similarity percentage.
            java.util.Set<String> familiar = new java.util.HashSet<>();
            for (Rating rating : preferences.getRatings()) {
                if (rating.getStars() >= 4) { familiar.addAll(rating.getMovie().getGenres()); }
            }
            ArrayList<Recommendation> pool = new ArrayList<>(results.subList(Math.min(MAX_RESULTS, results.size() - 1), results.size()));
            pool.sort((first, second) -> {
                long a = first.getMovie().getGenres().stream().filter(g -> !familiar.contains(g)).count();
                long b = second.getMovie().getGenres().stream().filter(g -> !familiar.contains(g)).count();
                int novelty = Long.compare(b, a);
                return novelty != 0 ? novelty : Double.compare(discoveryRank(second), discoveryRank(first));
            });
            results = pool;
        }
        ArrayList<Recommendation> varied = new ArrayList<>();
        java.util.Set<String> directors = new java.util.HashSet<>();
        java.util.Set<String> series = new java.util.HashSet<>();
        for (Recommendation result : results) {
            String director = result.getMovie().getDirector().toLowerCase(Locale.ROOT);
            String family = family(result.getMovie().getTitle());
            if (directors.contains(director) || series.contains(family)) { continue; }
            varied.add(result); directors.add(director); series.add(family);
            if (varied.size() == MAX_RESULTS) { break; }
        }
        // Fill a small collection even when every available film shares a director.
        for (Recommendation result : results) {
            if (varied.size() == MAX_RESULTS) { break; }
            if (!varied.contains(result)) { varied.add(result); }
        }
        return varied;
    }

    private String family(String title) {
        return title.toLowerCase(Locale.ROOT).split(":", 2)[0].replaceAll("\\s+\\d+.*$", "").trim();
    }

    private static class FavoriteMatch {
        private Rating rating;
        private Recommendation match;
        FavoriteMatch(Rating rating, Recommendation match) { this.rating = rating; this.match = match; }
    }
}
