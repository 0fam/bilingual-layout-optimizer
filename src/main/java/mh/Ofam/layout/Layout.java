package mh.Ofam.layout;

import java.util.HashMap;
import java.util.Map;

public class Layout {
    String name;
    Map<Character, KeyInfo> charMap = new HashMap<>();
    String[] rows; // just for logs

    Layout(String name, String[] rows) {
        this.name = name;
        this.rows = rows;
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r].toLowerCase();
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                charMap.put(ch, new KeyInfo(r, c));
            }
        }
    }
}
