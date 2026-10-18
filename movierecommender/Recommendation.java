package movierecommender;

import java.util.ArrayList;

// Keeps a movie together with its similarity score and explanation.
public class Recommendation {
    private Movie movie;
    private int score;
    private String explanation;
    private int possibleScore;
    private double matchFraction;
    private String summary;
    private ArrayList<String> details = new ArrayList<>();

    public Recommendation(Movie movie, int score, String explanation, int possibleScore) {
        if (possibleScore <= 0 || score < 0 || score > possibleScore) {
            throw new IllegalArgumentException("Score must be between zero and the possible maximum.");
        }
        this.movie = movie;
        this.score = score;
        this.explanation = explanation;
        this.possibleScore = possibleScore;
        this.matchFraction = (double) score / possibleScore;
        this.summary = "Shared movie attributes";
        details.add(explanation);
    }

    public Movie getMovie() {
        return movie;
    }

    public int getScore() {
        return score;
    }

    public String getExplanation() {
        return explanation;
    }

    public int getMatchPercentage() {
        return (int) Math.round(100.0 * matchFraction);
    }

    public double getMatchFraction() { return matchFraction; }

    public String getSummary() { return summary; }

    public ArrayList<String> getDetails() { return new ArrayList<>(details); }

    public void setPresentation(double fraction, String summary, ArrayList<String> details) {
        if (!Double.isFinite(fraction) || fraction < 0 || fraction > 1) {
            throw new IllegalArgumentException("Match fraction must be between 0 and 1.");
        }
        this.matchFraction = fraction;
        this.summary = summary;
        this.details = new ArrayList<>(details);
    }
}
