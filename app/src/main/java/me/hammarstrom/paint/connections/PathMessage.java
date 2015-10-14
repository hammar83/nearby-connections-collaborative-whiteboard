package me.hammarstrom.paint.connections;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by Fredrik Hammarström on 12/10/15.
 */
public class PathMessage implements Serializable {

    private static List<String> coords;

    static {
        coords = new ArrayList<>();
    }

    public static void clearList() {
        coords.clear();
    }

    public static void addCoords(float x, float y) {
        coords.add(String.valueOf(x) + "," + String.valueOf(y));
    }

    public static List<String> getCoords() {
        return Collections.unmodifiableList(coords);
    }

}
