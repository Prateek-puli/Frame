package movierecommender;

import java.util.*;
import java.util.function.BiConsumer;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

public class TasteSetupDialog extends Dialog<TasteProfile> {
    final ArrayList<ToggleButton> genreButtons = new ArrayList<>();
    final ArrayList<ToggleButton> movieButtons = new ArrayList<>();
    final Button next = new Button("Continue");
    final Button back = new Button("Back");
    final Button skip = new Button("Skip setup");
    final Button swap = new Button("Show other films");
    final TextField favoriteSearch = new TextField();
    private final HBox choiceTools = new HBox(10, favoriteSearch, swap);
    private final HashMap<String, Integer> offsets = new HashMap<>();
    private final Label title = new Label();
    private final Label instruction = new Label();
    private final Label progress = new Label();
    private final TilePane grid = new TilePane(10, 12);
    private final ArrayList<Movie> movies;
    private final BiConsumer<Movie, StackPane> posters;
    private final ArrayList<String> genres = new ArrayList<>();
    private final LinkedHashMap<String, LinkedHashSet<Integer>> picks = new LinkedHashMap<>();
    private int step = -1;

    public TasteSetupDialog(Window owner, ArrayList<Movie> movies, TasteProfile existing, BiConsumer<Movie, StackPane> posters) {
        this.movies = movies; this.posters = posters;
        initOwner(owner); setTitle("Shape your taste");
        title.getStyleClass().add("heading"); instruction.setWrapText(true);
        progress.getStyleClass().add("eyebrow");
        grid.setPrefColumns(5); grid.setPrefTileWidth(132);
        ScrollPane scroll = new ScrollPane(grid); scroll.setFitToWidth(true); scroll.getStyleClass().add("content-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        next.getStyleClass().add("primary");
        next.setOnAction(event -> advance()); back.setOnAction(event -> retreat());
        skip.setOnAction(event -> { setResult(null); close(); });
        Region space = new Region(); HBox.setHgrow(space, Priority.ALWAYS);
        HBox actions = new HBox(10, skip, space, back, next);
        favoriteSearch.setPromptText("Search favorites in this genre…");
        HBox.setHgrow(favoriteSearch, Priority.ALWAYS);
        favoriteSearch.textProperty().addListener((observable, before, after) -> { if (step >= 0) { buildMovies(); } });
        swap.setOnAction(event -> {
            String genre = genres.get(step);
            offsets.put(genre, offsets.getOrDefault(genre, 0) + 10 - picks.get(genre).size());
            favoriteSearch.clear(); buildMovies();
        });
        VBox content = new VBox(14, progress, title, instruction, choiceTools, scroll, actions);
        content.setPrefSize(750, 640);
        getDialogPane().setContent(content);
        // A cancel button makes window-close/Escape available; our visible Skip button is clearer.
        getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        setResultConverter(button -> null);
        var cancel = getDialogPane().lookupButton(ButtonType.CANCEL); cancel.setVisible(false); cancel.setManaged(false);
        getDialogPane().getStylesheets().add(getClass().getResource("desktop.css").toExternalForm());
        existing.getFavorites().forEach((genre, ids) -> picks.put(genre, new LinkedHashSet<>(ids)));
        buildGenres(existing);
    }

    private void buildGenres(TasteProfile existing) {
        choiceTools.setVisible(false); choiceTools.setManaged(false);
        grid.getChildren().clear(); genreButtons.clear(); grid.setPrefTileWidth(132);
        title.setText("What do you love watching?");
        instruction.setText("Choose 3 genres. Next, choose 3 favorites from 10 films in each genre. Favorites guide matches without adding star ratings.");
        TreeSet<String> available = new TreeSet<>(); movies.forEach(movie -> available.addAll(movie.getGenres()));
        for (String genre : available) {
            if (candidates(genre).size() < 10) { continue; }
            ToggleButton button = new ToggleButton(genre); button.setWrapText(true); button.setPrefSize(132, 55);
            button.getStyleClass().add("taste-genre");
            button.setSelected(existing.getFavorites().containsKey(genre));
            button.selectedProperty().addListener((observable, before, selected) -> {
                if (selected && genreButtons.stream().filter(ToggleButton::isSelected).count() > 3) { button.setSelected(false); }
                updateProgress();
            });
            genreButtons.add(button); grid.getChildren().add(button);
        }
        if (genreButtons.size() < 3) { instruction.setText("This collection needs at least 3 genres with 10 films each. You can skip setup and add more movies later."); }
        step = -1; back.setVisible(false); back.setManaged(false); next.setText("Continue"); updateProgress();
    }

    private ArrayList<Movie> candidates(String genre) {
        ArrayList<Movie> list = new ArrayList<>();
        for (Movie movie : movies) { if (movie.getGenres().contains(genre)) { list.add(movie); } }
        return list;
    }

    private void advance() {
        if (step == -1) {
            genres.clear(); genreButtons.stream().filter(ToggleButton::isSelected).forEach(button -> genres.add(button.getText()));
            if (genres.size() != 3) { return; }
            for (String genre : genres) { picks.computeIfAbsent(genre, key -> new LinkedHashSet<>()); }
            step = 0; favoriteSearch.clear(); buildMovies();
        } else if (picks.get(genres.get(step)).size() == 3) {
            if (step < 2) { step++; favoriteSearch.clear(); buildMovies(); }
            else {
                TasteProfile result = new TasteProfile(); result.setDismissed(true);
                genres.forEach(genre -> result.put(genre, picks.get(genre))); setResult(result); close();
            }
        }
    }

    private void retreat() {
        if (step > 0) { step--; favoriteSearch.clear(); buildMovies(); }
        else {
            TasteProfile chosen = new TasteProfile(); genres.forEach(genre -> chosen.put(genre, picks.get(genre)));
            buildGenres(chosen);
        }
    }

    private void buildMovies() {
        grid.getChildren().clear(); movieButtons.clear(); grid.setPrefTileWidth(132);
        String genre = genres.get(step);
        choiceTools.setVisible(true); choiceTools.setManaged(true);
        title.setText("Your " + genre + " favorites"); instruction.setText("Choose 3 films you enjoy. You can go back or skip setup at any time.");
        ArrayList<Movie> options = candidates(genre);
        ArrayList<Movie> chosen = new ArrayList<>();
        ArrayList<Movie> remaining = new ArrayList<>();
        String query = favoriteSearch.getText().trim().toLowerCase(Locale.ROOT);
        for (Movie movie : options) {
            if (picks.get(genre).contains(movie.getId())) { chosen.add(movie); }
            else if (movie.getTitle().toLowerCase(Locale.ROOT).contains(query)) { remaining.add(movie); }
        }
        swap.setDisable(remaining.size() <= 10 - chosen.size());
        int offset = query.isEmpty() && !remaining.isEmpty() ? offsets.getOrDefault(genre, 0) % remaining.size() : 0;
        int count = Math.min(10 - chosen.size(), remaining.size());
        for (int i = 0; i < count; i++) { chosen.add(remaining.get((offset + i) % remaining.size())); }
        if (count == 0 && !query.isEmpty()) {
            Label noResults = new Label("No other films found in " + genre + ". Try another title.");
            noResults.setWrapText(true); grid.getChildren().add(noResults);
        }
        for (Movie movie : chosen) {
            StackPane poster = new StackPane(); poster.setMinSize(96, 144); poster.setPrefSize(96, 144); poster.setMaxSize(96, 144);
            Label name = new Label(movie.getTitle()); name.setWrapText(true); name.setMaxWidth(112); name.setPrefHeight(35);
            Label year = new Label(String.valueOf(movie.getYear())); year.getStyleClass().add("muted");
            ToggleButton button = new ToggleButton(); button.setGraphic(new VBox(6, poster, name, year));
            button.setPrefWidth(132); button.getStyleClass().add("taste-movie"); button.setAccessibleText("Choose " + movie.getTitle() + " (" + movie.getYear() + ")");
            button.setSelected(picks.get(genre).contains(movie.getId()));
            button.selectedProperty().addListener((observable, before, selected) -> {
                if (selected && picks.get(genre).size() == 3) { button.setSelected(false); return; }
                if (selected) { picks.get(genre).add(movie.getId()); } else { picks.get(genre).remove(movie.getId()); }
                updateProgress();
            });
            movieButtons.add(button); grid.getChildren().add(button); posters.accept(movie, poster);
        }
        back.setVisible(true); back.setManaged(true); next.setText(step == 2 ? "See my best matches" : "Next genre"); updateProgress();
    }

    private void updateProgress() {
        int count = step == -1 ? (int) genreButtons.stream().filter(ToggleButton::isSelected).count() : picks.get(genres.get(step)).size();
        progress.setText((step == -1 ? "START WITH GENRES" : "GENRE " + (step + 1) + " OF 3") + " · " + count + " / 3 selected");
        next.setDisable(count != 3);
    }
}
