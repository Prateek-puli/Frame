package movierecommender;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        RecommendationEngine engine = new RecommendationEngine();
        UserRatings ratings = new UserRatings();
        String defaultMovies = java.nio.file.Files.exists(java.nio.file.Path.of("movies.csv"))
                ? "movies.csv" : "sample-movies.csv";
        String filename = args.length > 0 ? args[0] : defaultMovies;
        ArrayList<Movie> movies;
        try {
            movies = MovieLoader.load(filename);
        } catch (IOException exception) {
            System.out.println("Could not load " + filename + ": " + exception.getMessage());
            System.out.println("Check the file path and CSV format, then run again.");
            return;
        }
        for (Movie movie : movies) {
            engine.addMovie(movie);
        }
        String ratingsFile = args.length > 1 ? args[1] : "ratings.csv";
        try {
            ratings = RatingStore.load(ratingsFile, movies);
        } catch (IOException exception) {
            System.out.println("Could not load ratings: " + exception.getMessage());
            System.out.println("Fix the ratings file and restart. Your saved file has been preserved.");
            return;
        }

        Scanner scanner = new Scanner(System.in);
        System.out.println("Movie Recommendation Engine");
        System.out.println("Information courtesy of IMDb (https://www.imdb.com). Used with permission.");
        System.out.println("Loaded " + movies.size() + " movies from " + filename);
        printHelp();
        System.out.println("Loaded " + ratings.getRatings().size() + " saved ratings.");
        while (true) {
            System.out.print("\nEnter a title, movie ID, or command: ");
            if (!scanner.hasNextLine()) { break; }
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("quit")) {
                break;
            }
            if (input.isEmpty()) {
                System.out.println("Please enter a movie title or command.");
            } else if (input.equalsIgnoreCase("help")) {
                printHelp();
            } else if (input.equalsIgnoreCase("list")) {
                for (Movie movie : movies) {
                    System.out.println(movie.getDetails());
                }
            } else if (input.equalsIgnoreCase("ratings")) {
                if (ratings.getRatings().isEmpty()) {
                    System.out.println("You have not rated any movies yet.");
                }
                for (Rating rating : ratings.getRatings()) {
                    System.out.println(rating.getMovie().getDisplayTitle()
                            + " - " + rating.getStars() + "/5 stars");
                }
            } else if (input.equalsIgnoreCase("rate")) {
                System.out.print("Movie to rate (title or ID, or cancel): ");
                if (!scanner.hasNextLine()) { break; }
                String query = scanner.nextLine().trim();
                if (query.equalsIgnoreCase("cancel")) { continue; }
                Movie selected = chooseMovie(engine, query, scanner);
                if (selected != null) {
                    rateMovie(selected, ratings, scanner, ratingsFile);
                }
            } else if (input.equalsIgnoreCase("recommend")) {
                ArrayList<Recommendation> results = engine.getPersonalizedRecommendations(ratings);
                if (results.isEmpty()) {
                    System.out.println("No personalized matches. Rate some movies 4 or 5 stars to get suggestions.");
                } else {
                    System.out.println("Personalized recommendations:");
                    displayResults(results);
                }
            } else {
                Movie selected = chooseMovie(engine, input, scanner);
                if (selected != null) {
                    showRecommendations(engine, selected);
                }
            }
        }
        System.out.println("Goodbye!");
        scanner.close();
    }

    private static void printHelp() {
        System.out.println("Search by full/partial title, ID, or title (year).");
        System.out.println("Commands: list, rate, ratings, recommend, help, quit");
        System.out.println("Ratings save automatically. Use recommend for suggestions from your 4-5 star movies.");
    }

    private static Movie chooseMovie(RecommendationEngine engine, String query, Scanner scanner) {
        ArrayList<Movie> matches = engine.search(query);
        if (matches.isEmpty()) {
            System.out.println(query.isEmpty() ? "Please enter a movie title." : "Movie not found.");
            return null;
        }
        if (matches.size() == 1) {
            System.out.println("Selected: " + matches.get(0).getDetails());
            return matches.get(0);
        }
        System.out.println("Matching movies:");
        for (int i = 0; i < matches.size(); i++) {
            System.out.println((i + 1) + ". " + matches.get(i).getDetails());
        }
        while (true) {
            System.out.print("Choose a result number from 1 to " + matches.size() + " (0 to cancel): ");
            if (!scanner.hasNextLine()) { return null; }
            String choice = scanner.nextLine().trim();
            if (choice.equalsIgnoreCase("cancel") || choice.equals("0")) { return null; }
            try {
                int number = Integer.parseInt(choice);
                if (number >= 1 && number <= matches.size()) {
                    Movie selected = matches.get(number - 1);
                    System.out.println("Selected: " + selected.getDetails());
                    return selected;
                }
            } catch (NumberFormatException exception) {
                // Show the same helpful prompt for text and out-of-range numbers.
            }
            System.out.println("Enter a displayed result number, not a movie ID.");
        }
    }

    private static void rateMovie(Movie movie, UserRatings ratings, Scanner scanner, String filename) {
        while (true) {
            System.out.print("Your rating for " + movie.getTitle() + " (1-5, or cancel): ");
            if (!scanner.hasNextLine()) { return; }
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("cancel")) { return; }
            try {
                int stars = Integer.parseInt(input);
                ratings.rate(movie, stars);
                try {
                    RatingStore.save(filename, ratings);
                    System.out.println("Rating saved: " + movie.getDisplayTitle() + " - " + stars + "/5 stars");
                } catch (IOException exception) {
                    System.out.println("Rating is in this session, but saving failed: " + exception.getMessage());
                }
                return;
            } catch (IllegalArgumentException exception) {
                System.out.println("Please enter a whole number from 1 to 5.");
            }
        }
    }

    private static void showRecommendations(RecommendationEngine engine, Movie selected) {
        ArrayList<Recommendation> results = engine.getRecommendations(selected);
        if (results.isEmpty()) {
            System.out.println("No matching movies found.");
            return;
        }
        System.out.println("Recommendations:");
        displayResults(results);
    }

    private static void displayResults(ArrayList<Recommendation> results) {
        for (Recommendation result : results) {
            System.out.println(result.getMovie().getDetails()
                    + " (score: " + result.getScore() + " points)");
            System.out.println("  Why: " + result.getExplanation());
        }
    }
}
// TMDB credentials are loaded from Windows-encrypted local storage.
