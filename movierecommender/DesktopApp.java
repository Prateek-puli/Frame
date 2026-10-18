package movierecommender;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

public class DesktopApp extends Application {
    private final RecommendationEngine engine = new RecommendationEngine();
    private ArrayList<Movie> movies;
    private UserRatings ratings;
    private String ratingsFile;
    private Watchlist watchlist;
    private String watchlistFile;
    private String libraryView = "browse";
    private Watchlist watched;
    private String watchedFile;
    private TmdbService media;
    private java.util.Map<Integer, String> imdbIds;
    private final java.util.LinkedHashMap<String, Image> posterCache = new java.util.LinkedHashMap<>(64, 0.75f, true);
    private Movie selected;
    private final BorderPane root = new BorderPane();
    private final TextField search = new TextField();
    private final ListView<Movie> movieList = new ListView<>();
    private final VBox cards = new VBox(14);
    private final VBox detail = new VBox(10);
    private final Label section = new Label("YOUR COLLECTION");
    private final Label resultCount = new Label();
    private final Label heading = new Label("Discover your next film");
    private final Label selectedTitle = new Label();
    private final Label selectedMeta = new Label();
    private final Label selectedMatch = new Label();
    private final Label selectedConnection = new Label();
    private final Label selectedState = new Label();
    private final Label ratingLabel = new Label();
    private final Label status = new Label("Ready");
    private final HBox stars = new HBox(4);
    private final ArrayList<Button> starButtons = new ArrayList<>();
    private int selectedStars;
    private final ComboBox<String> genreFilter = new ComboBox<>();
    private final ComboBox<String> sortOrder = new ComboBox<>();
    private final TextField fromYear = new TextField();
    private final TextField toYear = new TextField();
    private final Label filterError = new Label();
    private final Button clearFilters = new Button("Clear filters");
    private final Button watchlistTab = new Button("Watchlist");
    private final Button watchlistToggle = new Button("Add to watchlist");
    private final Button watchedToggle = new Button("Mark as watched");
    private final Button watchedTab = new Button("Watched");
    private final Label synopsis = new Label();
    private final StackPane selectedPoster = new StackPane();
    private final Button connectTmdb = new Button("Connect TMDB");
    private boolean smokeMode;
    private final Button save = new Button("Save rating");
    private final Button forYou = new Button("Best matches");
    private final Button myRatings = new Button("My ratings");
    private final Button browseTab = new Button("Browse films");
    private final java.util.Map<String, Button> navigation = new java.util.LinkedHashMap<>();
    private final TitledPane filtersPane = new TitledPane();
    private final FlowPane filterChips = new FlowPane(6, 6);
    private TilePane collectionGrid;
    private TasteProfile taste;
    private String tasteFile;
    private final Button shapeTaste = new Button("Shape your taste");
    private final ComboBox<String> discoveryMode = new ComboBox<>();
    private final ComboBox<String> mood = new ComboBox<>();
    private final FlowPane moodControls = new FlowPane(12, 8);
    private final ComboBox<String> runtimeFilter = new ComboBox<>();
    private final Button showMore = new Button("Show more");
    private final java.util.Set<Integer> sessionShown = new java.util.HashSet<>();
    private final java.util.Map<Integer, Integer> runtimes = new java.util.HashMap<>();
    private DiscoveryPreferences discoveryFeedback;
    private String feedbackFile;
    private final Button profileTab = new Button("My taste");
    private final Button undoDismiss = new Button("Undo not interested");
    private Watchlist dismissed;
    private String dismissedFile;
    private Integer lastDismissed;
    private java.util.Map<Integer, Recommendation> personalScores = java.util.Map.of();
    private static final String MATCH_SORT = "Your match: highest first";
    private TasteSetupDialog testTasteWizard;
    private Stage window;
    private SplitPane workspaceSplit;
    private VBox libraryPane;
    private TitledPane hiddenTaste;
    private ArrayList<Recommendation> displayed = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        window = stage;
        String defaultMovies = Files.exists(Path.of("movies.csv")) ? "movies.csv" : "sample-movies.csv";
        String movieFile = getParameters().getNamed().getOrDefault("movies", defaultMovies);
        ratingsFile = getParameters().getNamed().getOrDefault("ratings", "ratings.csv");
        boolean smoke = getParameters().getNamed().containsKey("smoke-output");
        smokeMode = smoke;
        if (smoke && (!getParameters().getNamed().containsKey("watchlist")
                || !getParameters().getNamed().containsKey("ratings")
                || !getParameters().getNamed().containsKey("watched")
                || !getParameters().getNamed().containsKey("taste"))) {
            throw new IllegalArgumentException("Desktop checks require isolated --ratings, --watchlist, --watched and --taste paths.");
        }
        watchlistFile = getParameters().getNamed().getOrDefault("watchlist", "watchlist.csv");
        watchedFile = getParameters().getNamed().getOrDefault("watched", "watched.csv");
        tasteFile = getParameters().getNamed().getOrDefault("taste", "taste-profile.json");
        dismissedFile = getParameters().getNamed().getOrDefault("dismissed", smoke ? ".verification/desktop-dismissed.csv" : "not-interested.csv");
        feedbackFile = getParameters().getNamed().getOrDefault("feedback", smoke ? ".verification/desktop-feedback.json" : "recommendation-feedback.json");
        try {
            movies = MovieLoader.load(movieFile);
            ratings = RatingStore.load(ratingsFile, movies);
            watchlist = WatchlistStore.load(watchlistFile, movies);
            watched = WatchlistStore.load(watchedFile, movies);
            taste = TasteProfileStore.load(tasteFile, movies);
            dismissed = WatchlistStore.load(dismissedFile, movies);
            discoveryFeedback = DiscoveryPreferences.load(feedbackFile, movies);
            Path runtimeFile = Path.of(System.getProperty("frame.appDir", "."), "data/runtimes.json");
            if (Files.exists(runtimeFile)) {
                var data = com.google.gson.JsonParser.parseString(Files.readString(runtimeFile)).getAsJsonObject();
                for (var entry : data.entrySet()) { int minutes = entry.getValue().getAsInt(); if (minutes > 0) { runtimes.put(Integer.parseInt(entry.getKey()), minutes); } }
            }
        } catch (IOException exception) {
            if (smoke) { throw new IllegalStateException(exception); }
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Movie library could not open");
            error.setHeaderText("Check movie, ratings, watchlist and watched-history files");
            error.setContentText(exception.getMessage() + "\nYour saved files have not been changed.");
            error.showAndWait();
            Platform.exit();
            return;
        }
        for (Movie movie : movies) { engine.addMovie(movie); }
        imdbIds = TmdbService.loadImdbIds(Path.of(System.getProperty("frame.appDir", "."), "data/imdb-mapping.json"));
        String savedToken = smoke ? "" : LocalCredentials.load();
        String configuredToken = !savedToken.isEmpty() ? savedToken : System.getenv("TMDB_READ_ACCESS_TOKEN");
        media = new TmdbService(smoke ? "" : configuredToken, imdbIds);
        buildInterface();
        Scene scene = new Scene(root, 1180, 800);
        scene.getStylesheets().add(getClass().getResource("desktop.css").toExternalForm());
        stage.setTitle("Frame — Movie Recommendation Engine");
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(680);
        showPersonalized();
        status.setText(movies.size() + " movies • " + ratings.getRatings().size() + " saved ratings");
        stage.show();
        forYou.requestFocus();
        if (!smoke && !getParameters().getNamed().containsKey("preview-output")
                && !taste.isDismissed() && ratings.getRatings().isEmpty()) {
            Platform.runLater(this::showTasteSetup);
        }
        if (getParameters().getNamed().containsKey("preview-output")) {
            if (!media.isConfigured()) { throw new IllegalStateException("Live preview requires a configured TMDB token."); }
            runReadOnlyPreview(Path.of(getParameters().getNamed().get("preview-output")));
        }
        if (smoke) {
            Platform.runLater(() -> {
                try {
                    runSmoke(Path.of(getParameters().getNamed().get("smoke-output")));
                } catch (Exception exception) {
                    exception.printStackTrace();
                    System.exit(1);
                }
            });
        }
    }

    private void buildInterface() {
        Label brand = new Label("FRAME");
        brand.getStyleClass().add("brand");
        Label subtitle = new Label("Find films worth your time.");
        subtitle.getStyleClass().add("muted");
        VBox identity = new VBox(4, brand, subtitle);
        browseTab.setOnAction(event -> showBrowse());
        forYou.setOnAction(event -> showPersonalized());
        myRatings.setOnAction(event -> showRatings());
        watchlistTab.setOnAction(event -> showWatchlist());
        watchedTab.setOnAction(event -> showWatched());
        profileTab.setOnAction(event -> showTasteProfile());
        subtitle.setText("Your film companion.");
        subtitle.setWrapText(true);
        navigation.put("browse", browseTab); navigation.put("forYou", forYou);
        navigation.put("ratings", myRatings); navigation.put("watchlist", watchlistTab); navigation.put("watched", watchedTab);
        navigation.put("taste", profileTab);
        for (Button button : navigation.values()) {
            button.getStyleClass().add("nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
        }
        Label discover = new Label("DISCOVER"); discover.getStyleClass().add("nav-section");
        Label personal = new Label("YOUR LIBRARY"); personal.getStyleClass().add("nav-section");
        Region railSpacer = new Region(); VBox.setVgrow(railSpacer, Priority.ALWAYS);
        connectTmdb.setText(media.isConfigured() ? "TMDB settings" : "Connect TMDB");
        connectTmdb.setOnAction(event -> showTmdbSetup());
        connectTmdb.getStyleClass().add("nav-utility"); connectTmdb.setMaxWidth(Double.MAX_VALUE);
        Button credits = new Button("About & credits"); credits.setOnAction(event -> showCredits());
        credits.getStyleClass().add("nav-utility"); credits.setMaxWidth(Double.MAX_VALUE);
        VBox rail = new VBox(10, identity, discover, browseTab, forYou, personal, myRatings, watchlistTab, watchedTab, profileTab,
                railSpacer, connectTmdb, credits);
        rail.getStyleClass().add("navigation-rail");
        rail.setMinWidth(160); rail.setPrefWidth(160); rail.setMaxWidth(160);
        root.setLeft(rail);

        search.setPromptText("Search title, movie ID or Title (year)");
        search.textProperty().addListener((observable, oldValue, newValue) -> {
            filterMovies();
        });
        movieList.setCellFactory(list -> new ListCell<Movie>() {
            @Override protected void updateItem(Movie movie, boolean empty) {
                super.updateItem(movie, empty);
                setText(null);
                if (empty || movie == null) { setGraphic(null); return; }
                Label title = new Label(movie.getTitle());
                title.getStyleClass().add("movie-title");
                title.setWrapText(true);
                title.prefWidthProperty().bind(movieList.widthProperty().subtract(42));
                title.setMaxWidth(Double.MAX_VALUE);
                Label metadata = new Label(movie.getYear() + " • " + movie.getDirector());
                metadata.getStyleClass().add("muted");
                metadata.setWrapText(true);
                metadata.prefWidthProperty().bind(movieList.widthProperty().subtract(42));
                metadata.setMaxWidth(Double.MAX_VALUE);
                Label genres = new Label(movie.getGenre().replace("|", " · "));
                genres.getStyleClass().add("genre");
                genres.setWrapText(true);
                genres.prefWidthProperty().bind(movieList.widthProperty().subtract(42));
                genres.setMaxWidth(Double.MAX_VALUE);
                VBox cell = new VBox(5, title, metadata, genres);
                cell.setPadding(new Insets(10, 6, 10, 6));
                setGraphic(cell);
            }
        });
        movieList.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, movie) -> {
            if (movie != null) { selectMovie(movie); }
        });
        section.getStyleClass().add("eyebrow");
        resultCount.getStyleClass().add("muted");
        resultCount.setWrapText(true);
        java.util.TreeSet<String> genres = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Movie movie : movies) { genres.addAll(movie.getGenres()); }
        genreFilter.getItems().add("All genres");
        genreFilter.getItems().addAll(genres);
        genreFilter.setValue("All genres");
        genreFilter.setMaxWidth(Double.MAX_VALUE);
        genreFilter.setAccessibleText("Filter by genre");
        genreFilter.valueProperty().addListener((observable, oldValue, value) -> filterMovies());
        fromYear.setPromptText("From year");
        toYear.setPromptText("To year");
        fromYear.setAccessibleText("Earliest release year, inclusive");
        toYear.setAccessibleText("Latest release year, inclusive");
        fromYear.setPrefWidth(140); toYear.setPrefWidth(140);
        fromYear.textProperty().addListener((observable, oldValue, value) -> filterMovies());
        toYear.textProperty().addListener((observable, oldValue, value) -> filterMovies());
        clearFilters.setOnAction(event -> {
            genreFilter.setValue("All genres"); fromYear.clear(); toYear.clear();
        });
        filterError.getStyleClass().add("filter-error");
        filterError.setWrapText(true);
        sortOrder.getItems().addAll(MovieSorting.TITLE, MovieSorting.NEWEST, MovieSorting.OLDEST, MovieSorting.RATING);
        sortOrder.setValue(MovieSorting.TITLE);
        sortOrder.setMaxWidth(Double.MAX_VALUE);
        sortOrder.setAccessibleText("Sort collection");
        sortOrder.valueProperty().addListener((observable, oldValue, value) -> filterMovies());
        VBox filters = new VBox(10, genreFilter, new HBox(8, fromYear, toYear), clearFilters);
        filtersPane.setText("Filters"); filtersPane.setContent(filters); filtersPane.setExpanded(false); filtersPane.setAnimated(false);
        filtersPane.getStyleClass().add("filters-pane");
        filterChips.getStyleClass().add("filter-chips");
        VBox library = new VBox(10, section, search, filtersPane, filterChips, sortOrder, filterError, resultCount, movieList);
        library.getStyleClass().add("library");
        library.setPrefWidth(290);
        library.setMinWidth(250);
        library.setMaxWidth(340);
        VBox.setVgrow(movieList, Priority.ALWAYS);

        heading.getStyleClass().add("heading");
        selectedTitle.getStyleClass().add("selected-title");
        selectedTitle.setWrapText(true);
        selectedMeta.setWrapText(true);
        selectedMeta.getStyleClass().add("muted");
        for (int number = 1; number <= 5; number++) {
            final int value = number;
            Button star = new Button("☆");
            star.getStyleClass().add("star-button");
            star.setAccessibleText("Rate " + value + " out of 5 stars");
            star.setTooltip(new Tooltip(value + (value == 1 ? " star" : " stars")));
            star.setOnAction(event -> setStars(value));
            star.setOnMouseEntered(event -> paintStars(value));
            star.setOnMouseExited(event -> paintStars(selectedStars));
            starButtons.add(star);
            stars.getChildren().add(star);
        }
        save.getStyleClass().add("primary");
        save.setOnAction(event -> saveRating());
        watchlistToggle.setOnAction(event -> toggleWatchlist());
        watchedToggle.setOnAction(event -> toggleWatched());
        synopsis.setWrapText(true);
        synopsis.getStyleClass().add("synopsis");
        Label synopsisHeading = new Label("THE STORY"); synopsisHeading.getStyleClass().add("eyebrow");
        selectedMatch.getStyleClass().add("detail-match"); selectedMatch.setWrapText(true);
        selectedConnection.getStyleClass().add("muted"); selectedConnection.setWrapText(true);
        selectedState.getStyleClass().add("genre"); selectedState.setWrapText(true);
        VBox selectedInfo = new VBox(12, selectedTitle, selectedMeta, selectedState,
                selectedMatch, selectedConnection);
        HBox.setHgrow(selectedInfo, Priority.ALWAYS);
        selectedInfo.setMinWidth(0);
        selectedPoster.setMinSize(160, 240); selectedPoster.setPrefSize(160, 240); selectedPoster.setMaxSize(160, 240);
        Label rateHeading = new Label("YOUR RATING"); rateHeading.getStyleClass().add("eyebrow");
        VBox ratingSection = new VBox(6, rateHeading, new FlowPane(12, 8, stars, save), ratingLabel);
        ratingSection.getStyleClass().add("rating-section");
        Label listHeading = new Label("YOUR LIBRARY"); listHeading.getStyleClass().add("eyebrow");
        VBox libraryActions = new VBox(10, listHeading, new FlowPane(10, 10, watchlistToggle, watchedToggle));
        libraryActions.getStyleClass().add("rating-section");
        Label related = new Label("More like this"); related.getStyleClass().add("card-title");
        detail.getChildren().addAll(new HBox(22, selectedPoster, selectedInfo),
                new VBox(10, synopsisHeading, synopsis), ratingSection, libraryActions, related);
        detail.getStyleClass().add("detail-card");
        cards.setFillWidth(true);
        shapeTaste.getStyleClass().add("taste-start");
        shapeTaste.setOnAction(event -> showTasteSetup());
        discoveryMode.getItems().addAll("Close to my taste", "Something different");
        discoveryMode.setValue("Close to my taste");
        discoveryMode.setOnAction(event -> { if (heading.getText().equals("Best matches")) { showPersonalized(); } });
        discoveryMode.setTooltip(new Tooltip("Something different explores beyond your top matches and favors genres outside your favorites."));
        sortOrder.getItems().add(MATCH_SORT);
        mood.getItems().addAll("Any mood", "Funny", "Tense", "Thoughtful", "Adventurous", "Romantic");
        mood.setValue("Any mood"); mood.setPromptText("What are you in the mood for?");
        mood.setTooltip(new Tooltip("Mood choices filter by related genres in your collection."));
        mood.setOnAction(event -> { if (heading.getText().equals("Best matches")) { showPersonalized(); } else if (libraryView.equals("browse")) { filterMovies(); } });
        undoDismiss.setOnAction(event -> { if (lastDismissed != null) { changeDismissal(lastDismissed, false); } });
        undoDismiss.setVisible(false); undoDismiss.setManaged(false);
        Label moodTitle = new Label("What are you in the mood for?"); moodTitle.getStyleClass().add("muted");
        moodControls.getChildren().addAll(moodTitle, mood);
        runtimeFilter.getItems().addAll("Any length", "Under 90 minutes", "Up to 2 hours"); runtimeFilter.setValue("Any length");
        runtimeFilter.setTooltip(new Tooltip("Time limits include films with a known runtime only. Any length also includes unknown runtimes."));
        runtimeFilter.setOnAction(event -> { if (heading.getText().equals("Best matches")) { showPersonalized(); } else if (libraryView.equals("browse")) { filterMovies(); } });
        moodControls.getChildren().add(runtimeFilter);
        showMore.setOnAction(event -> showPersonalized(true)); showMore.setVisible(false); showMore.setManaged(false);
        VBox content = new VBox(18, new FlowPane(16, 10, heading, shapeTaste, discoveryMode, undoDismiss),
                moodControls, detail, cards, showMore);
        content.setPadding(new Insets(24));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-scroll");
        scroll.vvalueProperty().addListener((observable, oldValue, value) -> loadVisibleCollectionPosters());
        scroll.viewportBoundsProperty().addListener((observable, oldValue, value) -> Platform.runLater(this::loadVisibleCollectionPosters));
        SplitPane split = new SplitPane(library, scroll);
        workspaceSplit = split; libraryPane = library;
        split.setDividerPositions(0.29);
        BorderPane workspace = new BorderPane(split);
        root.setCenter(workspace);
        Label attribution = new Label("Information courtesy of IMDb (https://www.imdb.com). Used with permission.");
        attribution.getStyleClass().add("attribution");
        status.setWrapText(true); attribution.setWrapText(true);
        VBox footer = new VBox(5, status, attribution);
        footer.getStyleClass().add("footer");
        workspace.setBottom(footer);
        hideDetail();
    }

    private void showBrowse() {
        libraryView = "browse";
        section.setText("YOUR COLLECTION");
        filterMovies();
    }

    private void filterMovies() {
        filterMovies(true);
    }

    private void filterMovies(boolean spotlights) {
        showMore.setVisible(false); showMore.setManaged(false);
        if (libraryView.equals("taste")) { showTasteProfile(); return; }
        if (!workspaceSplit.getItems().contains(libraryPane)) { workspaceSplit.getItems().add(0, libraryPane); workspaceSplit.setDividerPositions(0.29); }
        shapeTaste.setText("Shape your taste");
        search.setDisable(false); filtersPane.setDisable(false); sortOrder.setDisable(false);
        filterChips.setVisible(!filterChips.getChildren().isEmpty()); filterChips.setManaged(!filterChips.getChildren().isEmpty());
        moodControls.setVisible(libraryView.equals("browse")); moodControls.setManaged(libraryView.equals("browse"));
        shapeTaste.setVisible(false); shapeTaste.setManaged(false);
        discoveryMode.setVisible(false); discoveryMode.setManaged(false);
        activateNavigation(libraryView);
        updateFilterChips();
        ArrayList<Movie> source = search.getText().trim().isEmpty() ? movies : engine.search(search.getText());
        ArrayList<Movie> matches = new ArrayList<>();
        personalScores = engine.matchScores(ratings, discoveryFavorites());
        try {
            Integer first = MovieFilters.parseYear(fromYear.getText());
            Integer last = MovieFilters.parseYear(toYear.getText());
            if (first != null && last != null && first > last) {
                throw new IllegalArgumentException("From year must be no later than To year.");
            }
            for (Movie movie : source) {
                if (libraryView.equals("browse") && !withinRuntime(movie)) { continue; }
                if (libraryView.equals("browse") && !moodGenres().isEmpty() && movie.getGenres().stream().noneMatch(moodGenres()::contains)) { continue; }
                if (libraryView.equals("ratings") && !ratings.hasRated(movie.getId())) { continue; }
                if (libraryView.equals("watchlist") && (!watchlist.contains(movie.getId()) || watched.contains(movie.getId()))) { continue; }
                if (libraryView.equals("watched") && !watched.contains(movie.getId())) { continue; }
                if (MovieFilters.matches(movie, genreFilter.getValue(), first, last)) { matches.add(movie); }
            }
            filterError.setText("");
            filterError.setManaged(false);
        } catch (IllegalArgumentException exception) {
            filterError.setText(exception.getMessage());
            filterError.setManaged(true);
        }
        selected = null;
        hideDetail();
        movieList.getSelectionModel().clearSelection();
        MovieSorting.sort(matches, sortOrder.getValue(), ratings);
        if (MATCH_SORT.equals(sortOrder.getValue())) {
            matches.sort((a, b) -> Double.compare(matchFraction(b), matchFraction(a)));
        }
        movieList.setItems(FXCollections.observableArrayList(matches));
        resultCount.setText(matches.size() + " films");
        if (!libraryView.equals("browse")) {
            heading.setText(libraryView.equals("ratings") ? "My ratings" : libraryView.equals("watchlist") ? "Your watchlist" : "Watched films");
            showCollectionCards(matches);
            return;
        }
        heading.setText("Discover your next film");
        if (matches.isEmpty()) {
            emptyState("No films found", "Adjust the filters or clear your search. Your ratings and watchlist are unchanged.");
        } else if (spotlights) {
            showGenreSpotlights(matches);
        }
    }

    private void selectMovie(Movie movie) {
        showMore.setVisible(false); showMore.setManaged(false);
        moodControls.setVisible(false); moodControls.setManaged(false);
        shapeTaste.setVisible(false); shapeTaste.setManaged(false);
        discoveryMode.setVisible(false); discoveryMode.setManaged(false);
        javafx.scene.Node scroll = root.lookup(".content-scroll");
        if (scroll instanceof ScrollPane) { ((ScrollPane) scroll).setVvalue(0); }
        selected = movie;
        heading.setText("Film details");
        detail.setVisible(true);
        detail.setManaged(true);
        selectedTitle.setText(movie.getTitle());
        selectedMeta.setText(movie.getYear() + " • " + movie.getDirector()
                + (runtimes.containsKey(movie.getId()) ? " • " + runtimes.get(movie.getId()) + " min" : "")
                + "\n" + movie.getGenre().replace("|", " · "));
        Recommendation connection = engine.matchScores(ratings, discoveryFavorites()).get(movie.getId());
        selectedMatch.setText(connection != null ? "Your match: " + connection.getMatchPercentage() + "%"
                : hasTasteSeeds() ? "Your match: 0%" : "Discover your match");
        selectedConnection.setText(connection != null ? connection.getSummary()
                : hasTasteSeeds() ? "No shared attributes with your other favorites yet."
                : "Choose favorites in My taste to get a personal match percentage.");
        selectedMatch.setTooltip(new Tooltip("Similarity to your other positive favorites. Your rating of this film is excluded from its match calculation."));
        selectedState.setText(watched.contains(movie.getId()) ? "WATCHED" : watchlist.contains(movie.getId()) ? "IN YOUR WATCHLIST" : "READY TO DISCOVER");
        Integer current = null;
        for (Rating rating : ratings.getRatings()) {
            if (rating.getMovie().getId() == movie.getId()) { current = rating.getStars(); }
        }
        setStars(current == null ? 0 : current);
        watchlistToggle.setText(watchlist.contains(movie.getId()) ? "Remove from watchlist" : "Add to watchlist");
        watchlistToggle.setDisable(watched.contains(movie.getId()));
        watchedToggle.setText(watched.contains(movie.getId()) ? "Mark as unwatched" : "Mark as watched");
        loadMedia(movie, selectedPoster, synopsis, true);
        ratingLabel.setText(current == null ? "You haven't rated this film yet." : "Your saved rating: " + current + "/5 stars");
        ArrayList<Recommendation> similar = engine.getRecommendations(movie);
        similar.removeIf(result -> dismissed.contains(result.getMovie().getId()));
        display(similar);
    }

    private void hideDetail() {
        detail.setVisible(false);
        detail.setManaged(false);
        save.setDisable(true);
    }

    private void saveRating() {
        if (selected == null || selectedStars == 0) { return; }
        UserRatings updated = new UserRatings();
        for (Rating rating : ratings.getRatings()) { updated.rate(rating.getMovie(), rating.getStars()); }
        updated.rate(selected, selectedStars);
        try {
            RatingStore.save(ratingsFile, updated);
            ratings = updated;
            ratingLabel.setText("Your saved rating: " + selectedStars + "/5 stars");
            Watchlist updatedWatched = new Watchlist();
            for (int id : watched.getMovieIds()) { updatedWatched.add(id); }
            updatedWatched.add(selected.getId());
            try {
                WatchlistStore.save(watchedFile, updatedWatched);
                watched = updatedWatched;
                selectedState.setText("WATCHED"); watchedToggle.setText("Mark as unwatched"); watchlistToggle.setDisable(true);
                status.setText("Saved " + selected.getTitle() + " • " + selectedStars + "/5 stars • marked as watched");
                if (libraryView.equals("watchlist")) {
                    Movie ratedMovie = selected; filterMovies(); selectMovie(ratedMovie);
                }
            } catch (IOException watchedError) {
                status.setText("Rating saved, but watched status could not be saved. Save the rating again to retry.");
            }
            if (MovieSorting.RATING.equals(sortOrder.getValue())) {
                Movie previous = selected;
                ArrayList<Movie> sorted = new ArrayList<>(movieList.getItems());
                MovieSorting.sort(sorted, sortOrder.getValue(), ratings);
                movieList.setItems(FXCollections.observableArrayList(sorted));
                movieList.getSelectionModel().select(previous);
            }
        } catch (IOException exception) {
            status.setText("Rating could not be saved. Your previous ratings are unchanged.");
            Alert error = new Alert(Alert.AlertType.ERROR, exception.getMessage(), ButtonType.OK);
            error.setHeaderText("Rating could not be saved");
            error.show();
        }
    }

    private void showPersonalized() {
        showPersonalized(false);
    }

    private void showPersonalized(boolean nextBatch) {
        if (!nextBatch) { sessionShown.clear(); }
        else { for (Recommendation result : displayed) { sessionShown.add(result.getMovie().getId()); } }
        libraryView = "browse";
        section.setText("YOUR COLLECTION");
        filterMovies(false);
        activateNavigation("forYou");
        selected = null;
        movieList.getSelectionModel().clearSelection();
        hideDetail();
        heading.setText("Best matches");
        shapeTaste.setVisible(true); shapeTaste.setManaged(true);
        discoveryMode.setVisible(true); discoveryMode.setManaged(true);
        // Ask for enough candidates before excluding watched-but-unrated films.
        UserRatings preferences = new UserRatings();
        for (Rating rating : ratings.getRatings()) { preferences.rate(rating.getMovie(), rating.getStars()); }
        for (Movie movie : movies) { if (watched.contains(movie.getId()) && !preferences.hasRated(movie.getId())
                && !discoveryFavorites().contains(movie.getId())) { preferences.rate(movie, 3); } }
        java.util.Set<Integer> excluded = new java.util.HashSet<>(dismissed.getMovieIds());
        excluded.addAll(watched.getMovieIds());
        excluded.addAll(sessionShown);
        for (Movie movie : movies) { if (!withinRuntime(movie) || discoveryFeedback.feedback.getOrDefault(movie.getId(), 0) < 0) { excluded.add(movie.getId()); } }
        java.util.Set<Integer> disliked = new java.util.HashSet<>();
        discoveryFeedback.feedback.forEach((id, vote) -> { if (vote < 0) { disliked.add(id); } });
        display(engine.discover(preferences, discoveryFavorites(), "Something different".equals(discoveryMode.getValue()), excluded, moodGenres(), disliked));
        showMore.setVisible(true); showMore.setManaged(true); showMore.setDisable(displayed.isEmpty());
        if (displayed.isEmpty()) {
            emptyState(nextBatch ? "You're all caught up" : "No matches yet", "Try Any mood or Any length, add favorites, or reopen Best matches to start a fresh session. Time limits exclude films with unknown runtimes.");
        }
    }

    private void showRatings() {
        libraryView = "ratings";
        selected = null;
        hideDetail();
        section.setText("YOUR RATINGS");
        filterMovies();
    }

    private void setStars(int value) {
        selectedStars = value;
        paintStars(value);
        save.setDisable(selected == null || value == 0);
    }

    private void paintStars(int value) {
        for (int i = 0; i < starButtons.size(); i++) {
            Button star = starButtons.get(i);
            star.setText(i < value ? "★" : "☆");
            star.getStyleClass().remove("star-filled");
            if (i < value) { star.getStyleClass().add("star-filled"); }
        }
    }

    private void toggleWatchlist() {
        if (selected == null) { return; }
        Watchlist updated = new Watchlist();
        for (int id : watchlist.getMovieIds()) { updated.add(id); }
        boolean removing = updated.contains(selected.getId());
        if (removing) { updated.remove(selected.getId()); } else { updated.add(selected.getId()); }
        try {
            WatchlistStore.save(watchlistFile, updated);
            watchlist = updated;
            watchlistToggle.setText(removing ? "Add to watchlist" : "Remove from watchlist");
            selectedState.setText(watched.contains(selected.getId()) ? "WATCHED" : removing ? "READY TO DISCOVER" : "IN YOUR WATCHLIST");
            status.setText(selected.getTitle() + (removing ? " removed from watchlist" : " saved to watchlist"));
            if (libraryView.equals("watchlist") && removing) { showWatchlist(); }
        } catch (IOException exception) {
            status.setText("Watchlist could not be saved. Your previous list is unchanged.");
            Alert error = new Alert(Alert.AlertType.ERROR, exception.getMessage(), ButtonType.OK);
            error.setHeaderText("Watchlist could not be saved"); error.show();
        }
    }

    private void showWatchlist() {
        libraryView = "watchlist";
        sortOrder.setValue(MATCH_SORT);
        section.setText("YOUR WATCHLIST");
        filterMovies();
    }

    private void showWatched() {
        libraryView = "watched";
        section.setText("WATCHED FILMS");
        filterMovies();
    }

    private void toggleWatched() {
        if (selected == null) { return; }
        Movie movie = selected;
        Watchlist updated = new Watchlist();
        for (int id : watched.getMovieIds()) { updated.add(id); }
        boolean marking = !updated.contains(movie.getId());
        if (marking) { updated.add(movie.getId()); } else { updated.remove(movie.getId()); }
        try {
            WatchlistStore.save(watchedFile, updated);
            watched = updated;
            if (libraryView.equals("watchlist") || libraryView.equals("watched")) { filterMovies(); }
            selectMovie(movie);
            status.setText(movie.getTitle() + (marking ? " marked as watched" : " marked as unwatched"));
            if (marking && !ratings.hasRated(movie.getId()) && !smokeMode) {
                ButtonType rateNow = new ButtonType("Rate now");
                Alert prompt = new Alert(Alert.AlertType.CONFIRMATION, "Choose stars and save a rating, or come back later.", rateNow, new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE));
                prompt.setTitle("Film marked as watched"); prompt.setHeaderText("How was " + movie.getTitle() + "?");
                themeDialog(prompt.getDialogPane());
                prompt.showAndWait().ifPresent(choice -> { if (choice == rateNow) { starButtons.get(0).requestFocus(); } });
            }
        } catch (IOException exception) {
            status.setText("Watched status could not be saved. Previous history is unchanged.");
            Alert error = new Alert(Alert.AlertType.ERROR, exception.getMessage(), ButtonType.OK);
            error.setHeaderText("Could not save watched status"); themeDialog(error.getDialogPane()); error.show();
        }
    }

    private void loadMedia(Movie movie, StackPane poster, Label overview, boolean full) {
        poster.getProperties().put("movieId", movie.getId());
        poster.getStyleClass().setAll("poster-placeholder");
        Label placeholder = new Label("FRAME\n" + movie.getYear());
        placeholder.getStyleClass().add("poster-caption");
        placeholder.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        poster.getChildren().setAll(placeholder);
        overview.setText(full ? (media.isConfigured() ? "Loading movie description…" : "Connect TMDB for posters and descriptions.") : "");
        overview.setManaged(full); overview.setVisible(full);
        TmdbService lookupService = media;
        lookupService.lookup(movie).thenAccept(info -> Platform.runLater(() -> {
            if (lookupService != media) { return; }
            if (info.runtime() > 0) {
                runtimes.put(movie.getId(), info.runtime());
                if (full && selected != null && selected.getId() == movie.getId()) { selectedMeta.setText(movie.getYear() + " • " + movie.getDirector() + " • " + info.runtime() + " min\n" + movie.getGenre().replace("|", " · ")); }
            }
            if (!Integer.valueOf(movie.getId()).equals(poster.getProperties().get("movieId"))) { return; }
            String description = info.overview().isBlank() ? (full ? (info.message().isEmpty() ? "No description available." : info.message()) : "") : shorten(info.overview(), full ? 700 : 140);
            overview.setText(description);
            overview.setManaged(!description.isEmpty()); overview.setVisible(!description.isEmpty());
            overview.setAccessibleText(info.overview().isBlank() ? description : info.overview());
            if (!info.overview().isBlank()) { overview.setTooltip(new Tooltip(info.overview())); }
            if (!info.posterUrl().isEmpty()) {
                Image image = posterCache.get(info.posterUrl());
                if (image == null) {
                    image = new Image(info.posterUrl(), 220, 330, true, true, true);
                    posterCache.put(info.posterUrl(), image);
                    while (posterCache.size() > 60) { posterCache.remove(posterCache.keySet().iterator().next()); }
                }
                ImageView view = new ImageView(image);
                view.setFitWidth(poster.getPrefWidth()); view.setFitHeight(poster.getPrefHeight()); view.setPreserveRatio(true);
                Image loaded = image;
                Runnable showImage = () -> {
                    if (!loaded.isError() && loaded.getProgress() == 1 && Integer.valueOf(movie.getId()).equals(poster.getProperties().get("movieId"))) {
                        poster.getChildren().setAll(view);
                    }
                };
                if (image.getProgress() < 1) {
                    image.progressProperty().addListener(new javafx.beans.value.ChangeListener<Number>() {
                        @Override public void changed(javafx.beans.value.ObservableValue<? extends Number> observable, Number oldValue, Number value) {
                            showImage.run();
                            if (value.doubleValue() == 1 || loaded.isError()) { loaded.progressProperty().removeListener(this); }
                        }
                    });
                }
                showImage.run();
            }
        }));
    }

    private String shorten(String text, int limit) {
        if (text.length() <= limit) { return text; }
        int end = text.lastIndexOf(' ', limit);
        return text.substring(0, end > 0 ? end : limit) + "…";
    }

    private void showTmdbSetup() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Posters & movie descriptions");
        dialog.initOwner(window);
        PasswordField token = new PasswordField();
        token.setPromptText("API Read Access Token");
        token.setAccessibleText("TMDB API Read Access Token");
        Label instructions = new Label("Enter your API Read Access Token. Remembering it uses Windows encryption for your local account. Posters are not saved to disk.");
        instructions.setWrapText(true);
        Hyperlink signup = new Hyperlink("Open TMDB API settings");
        signup.setOnAction(event -> getHostServices().showDocument("https://www.themoviedb.org/settings/api"));
        Hyperlink terms = new Hyperlink("TMDB usage terms and attribution");
        terms.setOnAction(event -> getHostServices().showDocument("https://www.themoviedb.org/api-terms-of-use"));
        CheckBox remember = new CheckBox("Remember on this computer (Windows encrypted)");
        remember.setSelected(true);
        Button connect = new Button("Connect");
        connect.getStyleClass().add("primary");
        Label message = new Label(); message.setWrapText(true);
        connect.setOnAction(event -> {
            String value = token.getText().trim();
            if (value.isEmpty() || value.contains("\n") || value.contains("\r")) { message.setText("Enter your API Read Access Token."); return; }
            TmdbService proposed = new TmdbService(value, imdbIds);
            boolean rememberToken = remember.isSelected();
            connect.setDisable(true); message.setText("Checking connection…");
            proposed.verifyConnection().thenApply(error -> {
                if (error.isEmpty() && rememberToken) {
                    try { LocalCredentials.save(value); }
                    catch (IOException exception) { return "Token is valid, but could not be saved. Uncheck Remember to use it this session."; }
                }
                return error;
            }).thenAccept(error -> Platform.runLater(() -> {
                if (!dialog.isShowing()) { proposed.close(); return; }
                connect.setDisable(false);
                if (!error.isEmpty()) { message.setText(error); proposed.close(); return; }
                media.close(); media = proposed; posterCache.clear();
                token.clear(); connectTmdb.setText("TMDB settings");
                status.setText("TMDB connected. Posters load as you browse.");
                if (selected != null) { selectMovie(selected); }
                else if (heading.getText().equals("Best matches")) { showPersonalized(); }
                else if (!libraryView.equals("browse")) { filterMovies(); }
                dialog.close();
            }));
        });
        VBox content = new VBox(12, instructions, signup, terms, token, connect, message);
        content.setPrefWidth(470);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        themeDialog(dialog.getDialogPane()); dialog.show();
    }

    private void showCredits() {
        Dialog<Void> credits = new Dialog<>();
        credits.setTitle("About & credits"); credits.initOwner(window);
        Label brand = new Label("FRAME"); brand.getStyleClass().add("brand");
        Label notice = new Label("This product uses the TMDB API but is not endorsed or certified by TMDB.");
        notice.setWrapText(true);
        VBox content = new VBox(16, brand, tmdbLogo(), notice,
                new Label("Information courtesy of IMDb (https://www.imdb.com). Used with permission."),
                new Label("Posters and descriptions: TMDB. JavaFX: OpenJFX. JSON parsing: Gson."));
        content.setPrefWidth(520);
        Hyperlink link = new Hyperlink("https://www.themoviedb.org");
        link.setOnAction(event -> getHostServices().showDocument("https://www.themoviedb.org")); content.getChildren().add(link);
        credits.getDialogPane().setContent(content); credits.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        themeDialog(credits.getDialogPane()); credits.show();
    }

    private javafx.scene.Node tmdbLogo() {
        try (java.io.InputStream source = getClass().getResourceAsStream("tmdb-logo.svg")) {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            org.w3c.dom.Document svg = factory.newDocumentBuilder().parse(source);
            javafx.scene.shape.SVGPath path = new javafx.scene.shape.SVGPath();
            path.setContent(((org.w3c.dom.Element) svg.getElementsByTagName("path").item(0)).getAttribute("d"));
            path.setFill(new javafx.scene.paint.LinearGradient(0, 17.76, 273.42, 17.76, false,
                    javafx.scene.paint.CycleMethod.NO_CYCLE, new javafx.scene.paint.Stop(0, javafx.scene.paint.Color.web("#90cea1")),
                    new javafx.scene.paint.Stop(0.56, javafx.scene.paint.Color.web("#3cbec9")), new javafx.scene.paint.Stop(1, javafx.scene.paint.Color.web("#00b3e5"))));
            path.getTransforms().add(new javafx.scene.transform.Scale(0.44, 0.44));
            return new javafx.scene.Group(path);
        } catch (Exception exception) { return new Label("TMDB"); }
    }

    private void themeDialog(DialogPane pane) { pane.getStylesheets().add(getClass().getResource("desktop.css").toExternalForm()); }

    @Override public void stop() { if (media != null) { media.close(); } }

    private void display(ArrayList<Recommendation> results) {
        collectionGrid = null;
        cards.getStyleClass().remove("home-results");
        if (heading.getText().equals("Best matches")) { cards.getStyleClass().add("home-results"); }
        displayed = results;
        cards.getChildren().clear();
        if (results.isEmpty()) {
            emptyState("No similar films yet", "Add more movies to the collection to find a match.");
            return;
        }
        if (heading.getText().equals("Best matches")) {
            java.util.Set<Integer> seeds = discoveryFavorites();
            for (Rating rating : ratings.getRatings()) {
                if (rating.getStars() >= 4) { seeds.add(rating.getMovie().getId()); }
                else { seeds.remove(rating.getMovie().getId()); }
            }
            Label guidance = new Label(seeds.size() < 5
                    ? "Early matches · based on limited taste data. Add favorites or ratings to refine them."
                    : "Something different".equals(discoveryMode.getValue())
                            ? "Explore beyond your top matches, with a preference for new genres."
                            : "Your strongest connections, based on your favorites and ratings.");
            guidance.setWrapText(true); guidance.getStyleClass().add("muted"); cards.getChildren().add(guidance);
        }
        for (Recommendation recommendation : results) {
            Movie movie = recommendation.getMovie();
            Label title = new Label(movie.getTitle());
            title.getStyleClass().add("card-title");
            title.setWrapText(true);
            title.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(title, Priority.ALWAYS);
            Label score = new Label("Your match: " + recommendation.getMatchPercentage() + "%");
            score.setTooltip(new Tooltip("Attribute similarity; personalized matches average your three strongest matching favorites.\nFive-star favorites count twice as much as four-star favorites. This is not a probability."));
            score.getStyleClass().add("score");
            Label metadata = new Label(movie.getYear() + " • " + movie.getDirector()
                    + (runtimes.containsKey(movie.getId()) ? " • " + runtimes.get(movie.getId()) + " min" : "")
                    + " • " + movie.getGenre().replace("|", " · "));
            metadata.setWrapText(true);
            metadata.getStyleClass().add("muted");
            Label explanation = new Label(recommendation.getSummary());
            explanation.setWrapText(true);
            explanation.getStyleClass().add("explanation");
            Button open = new Button("Explore & rate");
            open.setOnAction(event -> selectMovie(movie));
            Button uninterested = new Button("Not interested");
            uninterested.setOnAction(event -> changeDismissal(movie.getId(), true));
            FlowPane feedbackActions = new FlowPane(8, 8);
            if (heading.getText().equals("Best matches")) {
                Button good = new Button("Good suggestion"); good.setOnAction(event -> saveFeedback(movie.getId(), 1));
                Button poor = new Button("Not my taste"); poor.setOnAction(event -> saveFeedback(movie.getId(), -1));
                feedbackActions.getChildren().addAll(good, poor);
            }
            VBox reasons = new VBox(12);
            for (String text : recommendation.getDetails()) {
                Label reason = new Label(text);
                reason.setWrapText(true);
                reason.getStyleClass().add("explanation");
                reasons.getChildren().add(reason);
            }
            TitledPane why = new TitledPane("Why this match?", reasons);
            why.setExpanded(false);
            why.setAnimated(false);
            why.getStyleClass().add("match-details");
            Label preview = new Label();
            preview.setWrapText(true); preview.getStyleClass().add("synopsis");
            VBox cardText = new VBox(10, new HBox(12, title, score), metadata, preview, explanation,
                    new FlowPane(8, 8, open, uninterested), feedbackActions, why);
            cardText.setMinWidth(0); HBox.setHgrow(cardText, Priority.ALWAYS);
            StackPane poster = new StackPane();
            poster.setMinSize(80, 120); poster.setPrefSize(80, 120); poster.setMaxSize(80, 120);
            HBox content = new HBox(14, poster, cardText);
            VBox card = new VBox(content);
            card.getStyleClass().add("recommendation-card");
            cards.getChildren().add(card);
            loadMedia(movie, poster, preview, false);
        }
    }

    private void showGenreSpotlights(ArrayList<Movie> available) {
        collectionGrid = null; displayed = new ArrayList<>(); cards.getChildren().clear();
        cards.getStyleClass().remove("home-results");
        Label intro = new Label("Genre spotlight"); intro.getStyleClass().add("card-title");
        Label hint = new Label("Find a new favorite. Pick a poster to explore the film and its connections.");
        hint.setWrapText(true); hint.getStyleClass().add("muted");
        cards.getChildren().addAll(intro, hint);
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        for (Movie movie : available) { for (String genre : movie.getGenres()) { counts.merge(genre, 1, Integer::sum); } }
        ArrayList<String> genres = new ArrayList<>(counts.keySet());
        genres.sort((a, b) -> {
            int favorite = Boolean.compare(taste.getFavorites().containsKey(b), taste.getFavorites().containsKey(a));
            return favorite != 0 ? favorite : Integer.compare(counts.get(b), counts.get(a));
        });
        java.util.Set<Integer> shown = new java.util.HashSet<>();
        for (String genre : genres.subList(0, Math.min(3, genres.size()))) {
            Label name = new Label("Explore " + genre); name.getStyleClass().add("card-title");
            Button view = new Button("View genre");
            view.setOnAction(event -> genreFilter.setValue(genre));
            FlowPane row = new FlowPane(12, 12);
            ArrayList<Movie> candidates = new ArrayList<>();
            for (Movie movie : available) { if (movie.getGenres().contains(genre)) { candidates.add(movie); } }
            candidates.sort((a, b) -> {
                int unseen = Boolean.compare(watched.contains(a.getId()) || ratings.hasRated(a.getId()), watched.contains(b.getId()) || ratings.hasRated(b.getId()));
                return unseen != 0 ? unseen : Boolean.compare(shown.contains(a.getId()), shown.contains(b.getId()));
            });
            for (Movie movie : candidates.subList(0, Math.min(3, candidates.size()))) {
                shown.add(movie.getId());
                StackPane poster = new StackPane(); poster.setMinSize(100, 150); poster.setPrefSize(100, 150); poster.setMaxSize(100, 150);
                Label title = new Label(movie.getTitle()); title.setWrapText(true); title.setMaxWidth(135); title.setPrefHeight(38);
                Label year = new Label(String.valueOf(movie.getYear())); year.getStyleClass().add("muted");
                Button open = new Button("Explore film"); open.setOnAction(event -> selectMovie(movie));
                VBox tile = new VBox(8, poster, title, year, open); tile.setPrefWidth(155); tile.getStyleClass().add("recommendation-card");
                row.getChildren().add(tile); loadMedia(movie, poster, new Label(), false);
            }
            cards.getChildren().addAll(new FlowPane(12, 8, name, view), row);
        }
    }

    private void emptyState(String title, String message) {
        collectionGrid = null;
        displayed = new ArrayList<>();
        Label label = new Label(title);
        label.getStyleClass().add("card-title");
        Label description = new Label(message);
        description.setWrapText(true);
        description.getStyleClass().add("muted");
        VBox empty = new VBox(12, label, description);
        empty.getStyleClass().add("empty-state");
        cards.getChildren().setAll(empty);
    }

    private int savedRating(Movie movie) {
        for (Rating rating : ratings.getRatings()) { if (rating.getMovie().getId() == movie.getId()) { return rating.getStars(); } }
        return 0;
    }

    private java.util.Set<String> moodGenres() {
        return switch (mood.getValue() == null ? "Any mood" : mood.getValue()) {
            case "Funny" -> java.util.Set.of("Comedy");
            case "Tense" -> java.util.Set.of("Thriller", "Horror", "Crime");
            case "Thoughtful" -> java.util.Set.of("Drama", "Documentary");
            case "Adventurous" -> java.util.Set.of("Adventure", "Action", "Fantasy", "Sci-Fi");
            case "Romantic" -> java.util.Set.of("Romance");
            default -> java.util.Set.of();
        };
    }

    private double matchFraction(Movie movie) {
        Recommendation result = personalScores.get(movie.getId());
        return result == null ? 0 : result.getMatchFraction();
    }

    private boolean hasTasteSeeds() {
        return !discoveryFavorites().isEmpty() || ratings.getRatings().stream().anyMatch(r -> r.getStars() >= 4);
    }

    private java.util.Set<Integer> discoveryFavorites() {
        java.util.Set<Integer> favorites = new java.util.HashSet<>(taste.favoriteIds());
        discoveryFeedback.feedback.forEach((id, vote) -> { if (vote > 0) { favorites.add(id); } else { favorites.remove(id); } });
        return favorites;
    }

    private boolean withinRuntime(Movie movie) {
        String limit = runtimeFilter.getValue();
        if (limit == null || limit.equals("Any length")) { return true; }
        int minutes = runtimes.getOrDefault(movie.getId(), 0);
        return minutes > 0 && (limit.equals("Under 90 minutes") ? minutes < 90 : minutes <= 120);
    }

    private void saveFeedback(int id, int vote) {
        DiscoveryPreferences updated = new DiscoveryPreferences(); updated.feedback.putAll(discoveryFeedback.feedback);
        if (vote == 0) { updated.feedback.remove(id); } else { updated.feedback.put(id, vote); }
        try {
            updated.save(feedbackFile); discoveryFeedback = updated;
            if (libraryView.equals("taste")) { showTasteProfile(); } else { showPersonalized(); }
            status.setText(vote == 0 ? "Feedback cleared." : "Feedback saved separately from ratings. Clear it anytime in My taste.");
        } catch (IOException error) { status.setText("Feedback could not be saved. Your previous choices are unchanged."); }
    }

    private void changeDismissal(int id, boolean hide) {
        Watchlist updated = new Watchlist();
        for (int saved : dismissed.getMovieIds()) { updated.add(saved); }
        if (hide) { updated.add(id); } else { updated.remove(id); }
        try {
            WatchlistStore.save(dismissedFile, updated);
            dismissed = updated; lastDismissed = hide ? id : null;
            undoDismiss.setVisible(hide); undoDismiss.setManaged(hide);
            if (libraryView.equals("taste")) { showTasteProfile(); }
            else if (selected != null) { selectMovie(selected); }
            else { showPersonalized(); }
            status.setText(hide ? "Film hidden from recommendations. You can undo or restore it in My taste." : "Film restored to recommendations.");
        } catch (IOException exception) { status.setText("Could not save that choice. Your previous preferences are unchanged."); }
    }

    private void showTasteProfile() {
        showMore.setVisible(false); showMore.setManaged(false);
        workspaceSplit.getItems().remove(libraryPane);
        shapeTaste.setText("Edit my taste");
        moodControls.setVisible(false); moodControls.setManaged(false);
        libraryView = "taste"; activateNavigation("taste"); hideDetail(); selected = null;
        shapeTaste.setVisible(true); shapeTaste.setManaged(true);
        discoveryMode.setVisible(false); discoveryMode.setManaged(false);
        heading.setText("My taste"); collectionGrid = null; displayed = new ArrayList<>();
        cards.getChildren().clear(); cards.getStyleClass().remove("home-results");
        java.util.Set<Integer> seeds = discoveryFavorites();
        for (Rating rating : ratings.getRatings()) {
            if (rating.getStars() >= 4) { seeds.add(rating.getMovie().getId()); } else { seeds.remove(rating.getMovie().getId()); }
        }
        java.util.Map<String, Integer> genreCounts = new java.util.HashMap<>();
        java.util.Map<String, Integer> directorCounts = new java.util.HashMap<>();
        ArrayList<Movie> favorites = new ArrayList<>();
        for (Movie movie : movies) { if (seeds.contains(movie.getId())) {
            favorites.add(movie); directorCounts.merge(movie.getDirector(), 1, Integer::sum);
            for (String genre : movie.getGenres()) { genreCounts.merge(genre, 1, Integer::sum); }
        } }
        section.setText("YOUR FAVORITES"); search.setDisable(true); filtersPane.setDisable(true); sortOrder.setDisable(true);
        filterChips.setVisible(false); filterChips.setManaged(false);
        movieList.setItems(FXCollections.observableArrayList(favorites)); resultCount.setText(favorites.size() + " favorites");
        Label summary = new Label(seeds.size() + " favorites shaping your matches · " + ratings.getRatings().size() + " saved ratings");
        summary.getStyleClass().add("muted"); cards.getChildren().add(summary);
        Label favoriteHeading = new Label("Films you love"); favoriteHeading.getStyleClass().add("card-title"); cards.getChildren().add(favoriteHeading);
        TilePane favoriteLinks = new TilePane(18, 22); favoriteLinks.setPrefTileWidth(166);
        favoriteLinks.setTileAlignment(javafx.geometry.Pos.TOP_LEFT); collectionGrid = favoriteLinks;
        for (Movie movie : favorites) {
            StackPane poster = new StackPane(); poster.setMinSize(146, 219); poster.setPrefSize(146, 219); poster.setMaxSize(146, 219);
            poster.getStyleClass().add("poster-placeholder");
            Label fallback = new Label("FRAME\n" + movie.getYear()); fallback.getStyleClass().add("poster-caption"); poster.getChildren().add(fallback);
            poster.getProperties().put("pendingMovie", movie);
            Label title = new Label(movie.getTitle()); title.setWrapText(true); title.setPrefHeight(38); title.setMaxWidth(146); title.getStyleClass().add("collection-title");
            int rating = savedRating(movie);
            Label stars = new Label(rating > 0 ? "★".repeat(rating) + "☆".repeat(5 - rating)
                    : taste.favoriteIds().contains(movie.getId()) ? "Taste pick" : "Liked suggestion");
            stars.getStyleClass().add(rating > 0 ? "collection-stars" : "taste-chip");
            stars.setAccessibleText(rating > 0 ? "Your rating: " + rating + " out of 5 stars" : "Taste setup favorite");
            Label year = new Label(String.valueOf(movie.getYear())); year.getStyleClass().add("muted");
            Button open = new Button("Review film"); open.getStyleClass().add("collection-action"); open.setOnAction(event -> selectMovie(movie));
            VBox tile = new VBox(8, poster, title, year, stars, open); tile.setPrefWidth(166); tile.getStyleClass().add("taste-poster-tile");
            favoriteLinks.getChildren().add(tile);
        }
        if (favorites.isEmpty()) { Label hint = new Label("Add favorites with Edit my taste, or rate films 4–5 stars to start discovering your taste."); hint.setWrapText(true); cards.getChildren().add(hint); }
        cards.getChildren().add(favoriteLinks);
        favoriteLinks.widthProperty().addListener((observable, before, after) -> Platform.runLater(this::loadVisibleCollectionPosters));
        Label genreHeading = new Label("Your genres"); genreHeading.getStyleClass().add("card-title");
        FlowPane chips = new FlowPane(8, 8);
        for (String genre : sortedTaste(genreCounts)) {
            Button chip = new Button(genre + " · " + genreCounts.get(genre) + " favorites"); chip.getStyleClass().add("taste-chip");
            chip.setOnAction(event -> { showBrowse(); genreFilter.setValue(genre); }); chips.getChildren().add(chip);
        }
        cards.getChildren().addAll(genreHeading, chips);
        Label directorHeading = new Label("Directors you return to"); directorHeading.getStyleClass().add("card-title");
        FlowPane directors = new FlowPane(16, 10); ArrayList<String> names = sortedTaste(directorCounts);
        for (String director : names.subList(0, Math.min(3, names.size()))) {
            Label label = new Label(director + " · " + directorCounts.get(director)); label.getStyleClass().add("taste-director"); directors.getChildren().add(label);
        }
        cards.getChildren().addAll(directorHeading, directors);
        if (names.size() > 3) { Label all = new Label(topTaste(directorCounts)); all.setWrapText(true); TitledPane expand = new TitledPane("More directors", all); expand.setExpanded(false); expand.setAnimated(false); expand.getStyleClass().add("match-details"); cards.getChildren().add(expand); }
        VBox hidden = new VBox(10);
        for (Movie movie : movies) { if (dismissed.contains(movie.getId())) {
            Label title = new Label(movie.getDisplayTitle()); title.setWrapText(true);
            Button restore = new Button("Restore"); restore.setOnAction(event -> changeDismissal(movie.getId(), false));
            hidden.getChildren().add(new FlowPane(12, 8, title, restore));
        } }
        if (hidden.getChildren().isEmpty()) { hidden.getChildren().add(new Label("No hidden films. Your recommendations are open to everything that matches your taste.")); }
        hiddenTaste = new TitledPane("Not interested · " + dismissed.getMovieIds().size() + " films", hidden);
        hiddenTaste.setExpanded(false); hiddenTaste.setAnimated(false); hiddenTaste.getStyleClass().add("match-details"); cards.getChildren().add(hiddenTaste);
        VBox votes = new VBox(10);
        for (Movie movie : movies) { if (discoveryFeedback.feedback.containsKey(movie.getId())) {
            Label label = new Label(movie.getDisplayTitle() + (discoveryFeedback.feedback.get(movie.getId()) > 0 ? " · Good suggestion" : " · Not my taste")); label.setWrapText(true);
            Button clear = new Button("Clear feedback"); clear.setOnAction(event -> saveFeedback(movie.getId(), 0)); votes.getChildren().add(new FlowPane(10, 8, label, clear));
        } }
        TitledPane feedbackPanel = new TitledPane("Recommendation feedback · " + discoveryFeedback.feedback.size(), votes);
        feedbackPanel.setExpanded(false); feedbackPanel.setAnimated(false); feedbackPanel.getStyleClass().add("match-details"); cards.getChildren().add(feedbackPanel);
        Platform.runLater(() -> { root.applyCss(); root.layout(); loadVisibleCollectionPosters(); });
    }

    private ArrayList<String> sortedTaste(java.util.Map<String, Integer> counts) {
        ArrayList<String> keys = new ArrayList<>(counts.keySet());
        keys.sort((a, b) -> { int count = Integer.compare(counts.get(b), counts.get(a)); return count != 0 ? count : a.compareToIgnoreCase(b); });
        return keys;
    }

    private String topTaste(java.util.Map<String, Integer> counts) {
        ArrayList<String> keys = new ArrayList<>(counts.keySet());
        keys.sort((a, b) -> { int count = Integer.compare(counts.get(b), counts.get(a)); return count != 0 ? count : a.compareToIgnoreCase(b); });
        ArrayList<String> lines = new ArrayList<>();
        for (String key : keys.subList(0, Math.min(5, keys.size()))) { lines.add(key + " · " + counts.get(key) + " favorites"); }
        return lines.isEmpty() ? "Add favorites to discover your taste." : String.join("\n", lines);
    }

    private void profileSummary(String title, String text) {
        Label name = new Label(title); name.getStyleClass().add("card-title");
        Label body = new Label(text); body.setWrapText(true); body.getStyleClass().add("muted");
        VBox block = new VBox(10, name, body); block.getStyleClass().add("recommendation-card"); cards.getChildren().add(block);
    }

    private void showTasteSetup() {
        TasteSetupDialog wizard = new TasteSetupDialog(window, movies, taste,
                (movie, poster) -> loadMedia(movie, poster, new Label(), false));
        TasteProfile updated = wizard.showAndWait().orElseGet(() -> {
            TasteProfile skipped = new TasteProfile();
            taste.getFavorites().forEach(skipped::put); skipped.setDismissed(true); return skipped;
        });
        try {
            TasteProfileStore.save(tasteFile, updated);
            taste = updated;
            showPersonalized();
            status.setText(updated.favoriteIds().isEmpty() ? "Taste setup skipped. Shape your taste anytime." : "Taste saved. Your best matches are ready.");
        } catch (IOException exception) {
            Alert error = new Alert(Alert.AlertType.ERROR, "Taste could not be saved. Your previous preferences and ratings are unchanged.", ButtonType.OK);
            themeDialog(error.getDialogPane()); error.show();
        }
    }

    private void showCollectionCards(ArrayList<Movie> collection) {
        displayed = new ArrayList<>();
        cards.getStyleClass().remove("home-results");
        cards.getChildren().clear();
        if (collection.isEmpty()) {
            emptyState("No films here yet", "Add films or adjust your search and filters to see your collection.");
            return;
        }
        TilePane grid = new TilePane(14, 18);
        collectionGrid = grid;
        grid.setPrefColumns(3);
        grid.setPrefTileWidth(170);
        grid.setTileAlignment(javafx.geometry.Pos.TOP_LEFT);
        grid.getStyleClass().add("collection-grid");
        for (Movie movie : collection) {
            StackPane poster = new StackPane();
            poster.setMinSize(146, 219); poster.setPrefSize(146, 219); poster.setMaxSize(146, 219);
            Label title = new Label(movie.getTitle());
            title.getStyleClass().add("collection-title"); title.setWrapText(true); title.setMaxWidth(146);
            title.setMinHeight(38); title.setPrefHeight(38); title.setMaxHeight(38);
            title.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            title.setTooltip(new Tooltip(movie.getTitle()));
            Label year = new Label(String.valueOf(movie.getYear())); year.getStyleClass().add("muted");
            int rating = savedRating(movie);
            Label ratingStars = new Label(rating > 0 ? "★".repeat(rating) + "☆".repeat(5 - rating) : "Not rated");
            ratingStars.getStyleClass().add(rating > 0 ? "collection-stars" : "muted");
            ratingStars.setAccessibleText(rating > 0 ? "Your rating: " + rating + " out of 5 stars" : "Not rated");
            Label badge = new Label(watched.contains(movie.getId()) ? "WATCHED" : libraryView.equals("watchlist") ? "WATCH LATER" : "RATED");
            badge.getStyleClass().add("collection-status");
            Button open = new Button(libraryView.equals("ratings") ? "Edit rating" : "Open film");
            open.getStyleClass().add("collection-action"); open.setMaxWidth(Double.MAX_VALUE);
            open.setOnAction(event -> selectMovie(movie));
            VBox tile = new VBox(8, poster, title, year, ratingStars, badge, open);
            if (libraryView.equals("watchlist")) {
                Recommendation score = personalScores.get(movie.getId());
                Label match = new Label(score == null ? (hasTasteSeeds() ? "Your match: 0%" : "Add favorites for a match")
                        : "Your match: " + score.getMatchPercentage() + "%");
                match.setWrapText(true); match.getStyleClass().add("genre"); tile.getChildren().add(3, match);
            }
            tile.getStyleClass().add("collection-tile"); tile.setMinWidth(170); tile.setPrefWidth(170); tile.setMaxWidth(170);
            if (libraryView.equals("watchlist")) {
                Button mark = new Button("Mark watched"); mark.getStyleClass().add("collection-action"); mark.setMaxWidth(Double.MAX_VALUE);
                mark.setOnAction(event -> { selectMovie(movie); watchedToggle.fire(); }); tile.getChildren().add(mark);
            }
            grid.getChildren().add(tile);
            poster.getStyleClass().add("poster-placeholder");
            Label placeholder = new Label("FRAME\n" + movie.getYear());
            placeholder.getStyleClass().add("poster-caption");
            placeholder.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            poster.getChildren().add(placeholder);
            poster.getProperties().put("pendingMovie", movie);
        }
        cards.getChildren().add(grid);
        grid.widthProperty().addListener((observable, oldValue, value) -> Platform.runLater(this::loadVisibleCollectionPosters));
        Platform.runLater(() -> { root.applyCss(); root.layout(); loadVisibleCollectionPosters(); });
    }

    private void loadVisibleCollectionPosters() {
        if (collectionGrid == null || collectionGrid.getScene() == null) { return; }
        javafx.scene.Node scroll = root.lookup(".content-scroll");
        if (!(scroll instanceof ScrollPane)) { return; }
        javafx.scene.Node viewport = scroll.lookup(".viewport");
        if (viewport == null) { return; }
        javafx.geometry.Bounds visible = viewport.localToScene(viewport.getBoundsInLocal());
        for (javafx.scene.Node node : collectionGrid.getChildren()) {
            StackPane poster = (StackPane) ((VBox) node).getChildren().get(0);
            if (poster.getProperties().containsKey("pendingMovie")
                    && visible.intersects(poster.localToScene(poster.getBoundsInLocal()))) {
                Movie movie = (Movie) poster.getProperties().remove("pendingMovie");
                loadMedia(movie, poster, new Label(), false);
            }
        }
    }

    private void activateNavigation(String name) {
        for (java.util.Map.Entry<String, Button> entry : navigation.entrySet()) {
            entry.getValue().getStyleClass().remove("nav-active");
            boolean active = entry.getKey().equals(name);
            if (active) { entry.getValue().getStyleClass().add("nav-active"); }
            entry.getValue().setAccessibleText(entry.getValue().getText() + (active ? ", current section" : ""));
        }
    }

    private void updateFilterChips() {
        filterChips.getChildren().clear();
        if (genreFilter.getValue() != null && !genreFilter.getValue().equals("All genres")) {
            addFilterChip(genreFilter.getValue(), () -> genreFilter.setValue("All genres"));
        }
        if (!fromYear.getText().trim().isEmpty()) { addFilterChip("From " + fromYear.getText().trim(), () -> fromYear.clear()); }
        if (!toYear.getText().trim().isEmpty()) { addFilterChip("To " + toYear.getText().trim(), () -> toYear.clear()); }
        int count = filterChips.getChildren().size();
        filterChips.setVisible(count > 0); filterChips.setManaged(count > 0);
        filtersPane.setText(count == 0 ? "Filters" : "Filters · " + count + " active");
    }

    private void addFilterChip(String text, Runnable remove) {
        Button chip = new Button(text + "  ×");
        chip.getStyleClass().add("filter-chip");
        chip.setAccessibleText("Remove filter: " + text);
        chip.setOnAction(event -> remove.run());
        filterChips.getChildren().add(chip);
    }

    private void runSmoke(Path output) throws Exception {
        Files.createDirectories(output);
        require(heading.getText().equals("Best matches") && forYou.getStyleClass().contains("nav-active"), "Best matches is the startup screen");
        ArrayList<Runnable> steps = new ArrayList<>();
        steps.add(() -> {
        search.setText("Dune");
        require(movieList.getItems().size() >= 2, "Ambiguous search shows multiple films");
        Movie movie = engine.search("19").get(0);
            if (watched.contains(19)) { toggleWatched(); }
        movieList.getSelectionModel().select(movie);
        require(selected.getId() == 19 && !displayed.isEmpty(), "Search result selection produces recommendations");
        root.resize(1180, 800);
        root.applyCss();
        root.layout();
        });
        steps.add(() -> {
        capture(output.resolve("search.png"), 1180, 800);
        starButtons.get(4).fire();
        require(selectedStars == 5 && starButtons.get(4).getText().equals("★"), "Clickable stars fill selected rating");
        });
        steps.add(() -> {
        Movie movie = engine.search("19").get(0);
        save.fire();
        require(loadTestRatings().hasRated(19), "GUI rating saved to disk");
        try { require(WatchlistStore.load(watchedFile, movies).contains(19), "Rating automatically marks film watched on disk"); }
        catch (IOException error) { throw new IllegalStateException(error); }
        require(watchedToggle.getText().equals("Mark as unwatched") && watchlistToggle.isDisabled(), "Rated film shows watched controls");
        watchedToggle.fire();
        if (watchlist.contains(19)) { watchlistToggle.fire(); }
        watchlistToggle.fire();
        require(loadTestWatchlist().contains(19), "Watchlist add survives reload");
        forYou.fire();
        require(heading.getText().equals("Best matches"), "Best matches button changes heading");
        require(forYou.getStyleClass().contains("nav-active"), "For you section highlighted in sidebar");
        require(!displayed.isEmpty() && displayed.stream().noneMatch(result -> ratings.hasRated(result.getMovie().getId())), "Personalized results exclude rated movies");
        root.applyCss(); root.layout();
        });
        steps.add(() -> {
        capture(output.resolve("personalized.png"), 1180, 800);
        require(heading.getText().equals("Best matches"), "Personalized view survives layout");
        myRatings.fire();
        require(collectionGrid != null && collectionGrid.getChildren().size() == movieList.getItems().size(), "Ratings appear as poster tiles");
        require(myRatings.getStyleClass().contains("nav-active"), "Ratings section highlighted in sidebar");
        require(movieList.getItems().size() == ratings.getRatings().size(), "Ratings view shows saved movies");
        root.applyCss(); root.layout();
        });
        steps.add(() -> {
        capture(output.resolve("ratings.png"), 1180, 800);
        Movie movie = engine.search("19").get(0);
        movieList.getSelectionModel().select(movie);
        starButtons.get(2).fire();
        save.fire();
        require(loadTestRatings().getRatings().stream().anyMatch(rating -> rating.getMovie().getId() == 19 && rating.getStars() == 3), "GUI rating update persists");
        require(watched.contains(19), "Updating a rating marks film watched too");
        watchedToggle.fire();
        search.setText("no-movie-exists-12345");
        require(movieList.getItems().isEmpty() && save.isDisabled(), "Empty results disable rating");
        showBrowse();
        search.clear();
        genreFilter.setValue("Sci-Fi"); fromYear.setText("2010"); toYear.setText("2021");
        require(!movieList.getItems().isEmpty() && movieList.getItems().stream().allMatch(item -> MovieFilters.matches(item, "Sci-Fi", 2010, 2021)), "Genre and year filters combine");
        require(!filtersPane.isExpanded() && filterChips.getChildren().size() == 3, "Active badges visible with filters collapsed");
        ((Button) filterChips.getChildren().get(0)).fire();
        require(genreFilter.getValue().equals("All genres") && fromYear.getText().equals("2010") && toYear.getText().equals("2021"), "Removing genre badge preserves year filters");
        genreFilter.setValue("Sci-Fi");
        filtersPane.setExpanded(true);
        require(filtersPane.isExpanded(), "Filters can expand");
        filtersPane.setExpanded(false);
        fromYear.setText("2022");
        require(movieList.getItems().isEmpty() && !filterError.getText().isEmpty(), "Reversed year range has visible error");
        clearFilters.fire();
        require(movieList.getItems().size() == movies.size(), "Clearing filters restores collection");
        require(cards.getChildren().get(0) instanceof Label && ((Label) cards.getChildren().get(0)).getText().equals("Genre spotlight"), "Browse has genre spotlights");
        capture(output.resolve("genre-spotlights.png"), 1180, 800);
        watchlistTab.fire();
        require(collectionGrid != null && collectionGrid.getChildren().size() == movieList.getItems().size(), "Watchlist appears as poster tiles");
        require(movieList.getItems().stream().anyMatch(item -> item.getId() == 19), "Watchlist view includes saved movie");
        movieList.getSelectionModel().select(engine.search("19").get(0));
        require(selectedStars == 3 && starButtons.get(2).getText().equals("★") && starButtons.get(3).getText().equals("☆"), "Saved rating highlights stars");
        root.applyCss(); root.layout();
        });
        steps.add(() -> {
        capture(output.resolve("watchlist.png"), 1180, 800);
        watchlistToggle.fire();
        require(!loadTestWatchlist().contains(19), "Watchlist removal survives reload");
        require(ratings.hasRated(19), "Watchlist removal leaves ratings unchanged");
        showBrowse();
        genreFilter.setValue("Sci-Fi"); fromYear.setText("2010"); toYear.setText("2021");
        window.setWidth(980);
        window.setHeight(700);
        movieList.getSelectionModel().select(engine.search("5").get(0));
        root.applyCss(); root.layout();
        });
        steps.add(() -> {
            capture(output.resolve("compact.png"), 980, 700);
            sortOrder.setValue(MovieSorting.NEWEST);
            for (int i = 1; i < movieList.getItems().size(); i++) {
                require(movieList.getItems().get(i - 1).getYear() >= movieList.getItems().get(i).getYear(), "Newest-first UI sorting");
            }
            movieList.getSelectionModel().select(engine.search("5").get(0));
            VBox firstCard = (VBox) cards.getChildren().get(0);
            VBox text = (VBox) ((HBox) firstCard.getChildren().get(0)).getChildren().get(1);
            TitledPane why = (TitledPane) text.getChildren().get(text.getChildren().size() - 1);
            require(!why.isExpanded(), "Explanation starts collapsed");
            why.setExpanded(true);
            root.applyCss(); root.layout();
            ((ScrollPane) root.lookup(".content-scroll")).setVvalue(0.25);
        });
        steps.add(() -> {
            capture(output.resolve("expanded-reason.png"), 980, 700);
            Movie movie = engine.search("5").get(0);
            selectMovie(movie);
            if (watched.contains(5)) { watchedToggle.fire(); }
            if (!watchlist.contains(5)) { watchlistToggle.fire(); }
            watchedToggle.fire();
            try { require(WatchlistStore.load(watchedFile, movies).contains(5), "Watched status survives reload"); }
            catch (IOException exception) { throw new IllegalStateException(exception); }
            showWatchlist();
            require(movieList.getItems().stream().noneMatch(item -> item.getId() == 5), "Watched film leaves pending watchlist");
            watchedTab.fire();
            require(collectionGrid != null && collectionGrid.getChildren().size() == movieList.getItems().size(), "Watched films appear as poster tiles");
            require(movieList.getItems().stream().anyMatch(item -> item.getId() == 5), "Watched history includes film");
            movieList.getSelectionModel().select(movie);
            require(synopsis.getText().contains("Connect TMDB"), "Missing token has honest description fallback");
            require(!selectedPoster.getChildren().isEmpty(), "Missing poster has visible placeholder");
            require(tmdbLogo() instanceof javafx.scene.Group, "Approved TMDB logo is available locally");
        });
        steps.add(() -> {
            capture(output.resolve("watched.png"), 980, 700);
            watchedToggle.fire();
            try { require(!WatchlistStore.load(watchedFile, movies).contains(5), "Unwatched status persists"); }
            catch (IOException exception) { throw new IllegalStateException(exception); }
            showWatchlist();
            require(movieList.getItems().stream().anyMatch(item -> item.getId() == 5), "Unwatched film returns to saved watchlist");
            testTasteWizard = new TasteSetupDialog(window, movies, new TasteProfile(),
                    (movie, poster) -> loadMedia(movie, poster, new Label(), false));
            testTasteWizard.show();
            require(testTasteWizard.genreButtons.size() >= 3 && testTasteWizard.next.isDisabled(), "Setup starts with genre selection");
            testTasteWizard.genreButtons.get(0).fire(); testTasteWizard.genreButtons.get(1).fire();
            require(testTasteWizard.next.isDisabled(), "Exactly three genres required");
            testTasteWizard.genreButtons.get(2).fire();
            if (testTasteWizard.genreButtons.size() > 3) { testTasteWizard.genreButtons.get(3).fire(); }
            require(testTasteWizard.genreButtons.stream().filter(ToggleButton::isSelected).count() == 3, "Fourth genre blocked");
        });
        steps.add(() -> {
            try { writeSnapshot(testTasteWizard.getDialogPane(), output.resolve("taste-genres.png")); }
            catch (IOException exception) { throw new IllegalStateException(exception); }
            testTasteWizard.next.fire();
            require(testTasteWizard.movieButtons.size() == 10 && testTasteWizard.next.isDisabled(), "Ten films shown per genre");
            for (int i = 0; i < 4; i++) { testTasteWizard.movieButtons.get(i).fire(); }
            require(testTasteWizard.movieButtons.stream().filter(ToggleButton::isSelected).count() == 3, "Exactly three film picks allowed");
            String beforeSwap = testTasteWizard.movieButtons.get(9).getAccessibleText();
            testTasteWizard.swap.fire();
            require(testTasteWizard.movieButtons.stream().filter(ToggleButton::isSelected).count() == 3, "Swap keeps selected favorites");
            require(!beforeSwap.equals(testTasteWizard.movieButtons.get(9).getAccessibleText()), "Swap offers other films");
            testTasteWizard.favoriteSearch.setText("no-such-title-12345");
            require(testTasteWizard.movieButtons.size() == 3 && !testTasteWizard.next.isDisabled(), "Empty search preserves picks and progress");
            testTasteWizard.favoriteSearch.clear();
            require(testTasteWizard.movieButtons.size() == 10, "Clearing taste search restores ten choices");
        });
        steps.add(() -> {
            try { writeSnapshot(testTasteWizard.getDialogPane(), output.resolve("taste-movies.png")); }
            catch (IOException exception) { throw new IllegalStateException(exception); }
            for (int genre = 1; genre < 3; genre++) {
                testTasteWizard.next.fire();
                require(testTasteWizard.movieButtons.size() == 10, "Ten choices in each genre");
                for (int i = 0; i < 3; i++) { testTasteWizard.movieButtons.get(i).fire(); }
            }
            testTasteWizard.back.fire();
            require(testTasteWizard.movieButtons.stream().filter(ToggleButton::isSelected).count() == 3, "Back preserves picks");
            testTasteWizard.next.fire(); testTasteWizard.next.fire();
            TasteProfile result = testTasteWizard.getResult();
            require(result != null && result.getFavorites().size() == 3 && result.getFavorites().values().stream().allMatch(ids -> ids.size() == 3), "Setup returns three favorites for each of three genres");
            try {
                TasteProfileStore.save(tasteFile, result);
                require(TasteProfileStore.load(tasteFile, movies).favoriteIds().equals(result.favoriteIds()), "Taste setup survives reload");
            } catch (IOException exception) { throw new IllegalStateException(exception); }
            testTasteWizard = new TasteSetupDialog(window, movies, result, (movie, poster) -> loadMedia(movie, poster, new Label(), false));
            testTasteWizard.show();
            require(!testTasteWizard.next.isDisabled(), "Revisit restores genre choices");
            testTasteWizard.skip.fire();
            require(testTasteWizard.getResult() == null, "Skip exits without changing choices");
            showPersonalized();
            discoveryMode.setValue("Something different");
            require(heading.getText().equals("Best matches") && discoveryMode.isVisible(), "Discovery mode keeps Best matches active");
            discoveryMode.setValue("Close to my taste");
            showBrowse();
            require(!discoveryMode.isManaged(), "Discovery controls hide outside Best matches");
            showPersonalized();
            if (!displayed.isEmpty()) {
                int hiddenId = displayed.get(0).getMovie().getId();
                changeDismissal(hiddenId, true);
                require(displayed.stream().noneMatch(r -> r.getMovie().getId() == hiddenId), "Not interested removes recommendation");
                try { require(WatchlistStore.load(dismissedFile, movies).contains(hiddenId), "Hidden choice survives reload"); }
                catch (IOException exception) { throw new IllegalStateException(exception); }
                undoDismiss.fire(); require(!dismissed.contains(hiddenId), "Undo restores eligibility");
            }
            mood.setValue("Funny");
            require(displayed.stream().allMatch(r -> r.getMovie().getGenres().contains("Comedy")), "Funny mood filters best matches");
            mood.setValue("Any mood");
            showWatchlist();
            for (int i = 1; i < movieList.getItems().size(); i++) {
                require(matchFraction(movieList.getItems().get(i - 1)) >= matchFraction(movieList.getItems().get(i)), "Watchlist sorts by match");
            }
            capture(output.resolve("watchlist-match.png"), 1180, 800);
            runtimeFilter.setValue("Under 90 minutes");
            require(movies.stream().filter(this::withinRuntime).allMatch(m -> runtimes.getOrDefault(m.getId(), 0) > 0 && runtimes.get(m.getId()) < 90), "Runtime filter excludes unknown and long films");
            runtimeFilter.setValue("Up to 2 hours");
            require(movies.stream().filter(this::withinRuntime).allMatch(m -> runtimes.get(m.getId()) <= 120), "Two-hour runtime limit");
            runtimeFilter.setValue("Any length"); showPersonalized();
            java.util.Set<Integer> previousBatch = new java.util.HashSet<>(); displayed.forEach(r -> previousBatch.add(r.getMovie().getId()));
            showMore.fire();
            require(displayed.stream().noneMatch(r -> previousBatch.contains(r.getMovie().getId())), "Show more avoids prior batch");
            showPersonalized();
            if (!displayed.isEmpty()) {
                int feedbackId = displayed.get(0).getMovie().getId(); int ratingCount = ratings.getRatings().size();
                saveFeedback(feedbackId, 1);
                require(discoveryFavorites().contains(feedbackId) && ratings.getRatings().size() == ratingCount, "Positive feedback refines taste without ratings");
                saveFeedback(feedbackId, -1);
                require(displayed.stream().noneMatch(r -> r.getMovie().getId() == feedbackId), "Negative feedback excludes film");
                try { require(DiscoveryPreferences.load(feedbackFile, movies).feedback.get(feedbackId) == -1, "Feedback survives reload"); }
                catch (IOException error) { throw new IllegalStateException(error); }
                saveFeedback(feedbackId, 0);
            }
            showTasteProfile();
            require(heading.getText().equals("My taste") && profileTab.getStyleClass().contains("nav-active"), "Taste profile navigation works");
            require(workspaceSplit.getItems().size() == 1 && shapeTaste.getText().equals("Edit my taste"), "Taste dashboard uses full workspace");
            require(collectionGrid != null && !hiddenTaste.isExpanded(), "Taste dashboard has posters and collapsed hidden films");
            capture(output.resolve("taste-profile.png"), 1180, 800);
            showBrowse();
            require(workspaceSplit.getItems().contains(libraryPane) && shapeTaste.getText().equals("Shape your taste"), "Leaving taste restores library layout");
            System.out.println("Desktop and taste-setup checks passed."); Platform.exit();
        });
        int[] index = {0};
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(300), event -> {
            Runnable check = steps.get(index[0]++);
            Platform.runLater(() -> {
                try { check.run(); }
                catch (Exception exception) { exception.printStackTrace(); System.exit(1); }
            });
        }));
        timeline.setCycleCount(steps.size());
        timeline.play();
    }

    // Read-only visual verification with existing data; never rates or changes collections.
    private void runReadOnlyPreview(Path output) {
        ArrayList<Runnable> steps = new ArrayList<>();
        steps.add(() -> { capture(output.resolve("home-live.png"), 1180, 800); showRatings(); });
        steps.add(() -> { capture(output.resolve("ratings-live.png"), 1180, 800); showWatchlist(); });
        steps.add(() -> { capture(output.resolve("watchlist-live.png"), 1180, 800); showWatched(); });
        steps.add(() -> { capture(output.resolve("watched-live.png"), 1180, 800); System.out.println("Read-only live preview captured."); Platform.exit(); });
        try { Files.createDirectories(output); }
        catch (IOException exception) { throw new IllegalStateException(exception); }
        int[] index = {0};
        Timeline preview = new Timeline(new KeyFrame(Duration.seconds(8), event -> steps.get(index[0]++).run()));
        preview.setCycleCount(steps.size()); preview.play();
    }

    private UserRatings loadTestRatings() {
        try { return RatingStore.load(ratingsFile, movies); }
        catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    private Watchlist loadTestWatchlist() {
        try { return WatchlistStore.load(watchlistFile, movies); }
        catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    private void capture(Path filename, int width, int height) {
        try { snapshot(filename, width, height); }
        catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    private void snapshot(Path filename, int width, int height) throws IOException {
        root.applyCss();
        root.layout();
        writeSnapshot(root, filename);
    }

    private void writeSnapshot(javafx.scene.Node node, Path filename) throws IOException {
        WritableImage image = node.snapshot(null, null);
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage pixels = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) { pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y)); }
        }
        ImageIO.write(pixels, "png", filename.toFile());
    }

    private void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
