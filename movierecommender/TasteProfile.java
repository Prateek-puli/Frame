package movierecommender;

import java.util.*;

public class TasteProfile {
    private final LinkedHashMap<String, ArrayList<Integer>> favorites = new LinkedHashMap<>();
    private boolean dismissed;

    public boolean isDismissed() { return dismissed; }
    public void setDismissed(boolean value) { dismissed = value; }
    public LinkedHashMap<String, ArrayList<Integer>> getFavorites() {
        LinkedHashMap<String, ArrayList<Integer>> copy = new LinkedHashMap<>();
        favorites.forEach((genre, ids) -> copy.put(genre, new ArrayList<>(ids)));
        return copy;
    }
    public void put(String genre, Collection<Integer> ids) { favorites.put(genre, new ArrayList<>(ids)); }
    public Set<Integer> favoriteIds() {
        Set<Integer> ids = new HashSet<>(); favorites.values().forEach(ids::addAll); return ids;
    }
}
