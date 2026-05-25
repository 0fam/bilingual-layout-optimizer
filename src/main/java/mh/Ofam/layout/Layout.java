package mh.Ofam.layout;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class Layout {
    String name;
    Map<Character, KeyInfo> charMap = new HashMap<>();
    char[] matrix;

    public Layout(String name, String[] rows) {
        this.name = name;
        this.matrix = String.join("", rows).toCharArray();
        for (int i = 0; i < matrix.length; i++) {
            char lowChar = Character.toLowerCase(matrix[i]);
            charMap.put(lowChar, new KeyInfo(i / 10, i % 10));
        }
//        for (int r = 0; r < rows.length; r++) {
//            String row = rows[r].toLowerCase();
//            for (int c = 0; c < row.length(); c++) {
//                char ch = row.charAt(c);
//                charMap.put(ch, new KeyInfo(r, c));
//            }
//        }
    }

    public Layout(String name, char[] matrix) {
        this.name = name;
        if (matrix.length != 30) throw new IndexOutOfBoundsException("Matrix must be 30 characters long");
        this.matrix = matrix;
        for (int i = 0; i < matrix.length; i++) {
            char lowChar = Character.toLowerCase(matrix[i]);
            charMap.put(lowChar, new KeyInfo(i / 10, i % 10));
        }
    }


    public Layout clone() {
        return new Layout(name, matrix);
    }

    public void print() {
        System.out.println(spacer(matrix));
    }

    private String spacer(char[] a) {
        StringBuilder result = new StringBuilder(a.length * 2);
        for (int i = 0; i < a.length; i++) {
            result.append(" ").append(a[i]);
            if (i % 10 == 0) result.append("\n");
        }
        return result.toString();
    }

}
