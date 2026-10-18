package movierecommender;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

public class MovieLoader {
    // Five fields. Quoted commas and escaped quotes are supported; multiline fields are not.
    public static ArrayList<Movie> load(String filename) throws IOException {
        ArrayList<Movie> movies = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(
                Paths.get(filename), StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || !header.equals("id,title,director,year,genre")) {
                throw new IOException("Expected the header: id,title,director,year,genre");
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] fields;
                try {
                    fields = parseCsvLine(line);
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Line " + lineNumber + ": " + exception.getMessage(), exception);
                }
                if (fields.length != 5) {
                    throw new IOException("Line " + lineNumber
                            + " must have exactly five comma-separated fields.");
                }
                for (int i = 0; i < fields.length; i++) {
                    fields[i] = fields[i].trim();
                    if (fields[i].isEmpty()) {
                        throw new IOException("Line " + lineNumber
                                + " needs nonempty fields.");
                    }
                }
                try {
                    int id = Integer.parseInt(fields[0]);
                    int year = Integer.parseInt(fields[3]);
                    for (Movie existing : movies) {
                        if (existing.getId() == id) {
                            throw new IllegalArgumentException("Duplicate movie ID: " + id);
                        }
                    }
                    movies.add(new Movie(id, fields[1], fields[2], year, fields[4]));
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Line " + lineNumber + ": "
                            + exception.getMessage(), exception);
                }
            }
        }

        if (movies.isEmpty()) {
            throw new IOException("The movie file has no movies.");
        }
        return movies;
    }

    public static String[] parseCsvLine(String line) {
        ArrayList<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean closedQuote = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (quoted) {
                if (character == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                        closedQuote = true;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == ',') {
                fields.add(field.toString());
                field.setLength(0);
                closedQuote = false;
            } else if (closedQuote) {
                if (!Character.isWhitespace(character)) {
                    throw new IllegalArgumentException("Unexpected text after closing quotation mark.");
                }
            } else if (character == '"') {
                if (!field.toString().trim().isEmpty()) {
                    throw new IllegalArgumentException("Quote must begin a field.");
                }
                field.setLength(0);
                quoted = true;
            } else {
                field.append(character);
            }
        }
        if (quoted) { throw new IllegalArgumentException("Unclosed quotation mark."); }
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }
}
