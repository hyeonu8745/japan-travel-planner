package com.example.japantravelplanner.global.util;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV 한 줄을 칸으로 나눈다.
 * 칸 안에 쉼표가 있으면 따옴표로 감싸져 있으므로("a, b"), 단순 split(",")으로는 안 된다.
 */
public final class CsvParser {

    private CsvParser() {
    }

    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');   // "" 는 따옴표 한 개
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());
        return fields;
    }
}