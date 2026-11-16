package com.example.japantravelplanner.global.schema;

import com.example.japantravelplanner.attraction.entity.Attraction;
import com.example.japantravelplanner.facility.entity.Facility;
import com.example.japantravelplanner.restaurant.entity.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Id;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 엔티티 칸이 Flyway SQL(V1~)에 모두 있는지 DB 없이 확인한다.
 * 서버는 ddl-auto: validate 라서 칸이 하나라도 빠지면 켜지지 않는다 (컨테이너에는 DB 가 없어 기동 테스트로는 못 잡는다).
 */
class MigrationColumnsTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    @Test
    void restaurantColumnsExistInMigrations() throws IOException {
        assertColumnsExist(Restaurant.class, "restaurants");
    }

    @Test
    void attractionColumnsExistInMigrations() throws IOException {
        assertColumnsExist(Attraction.class, "attractions");
    }

    @Test
    void facilityColumnsExistInMigrations() throws IOException {
        assertColumnsExist(Facility.class, "facilities");
    }

    private void assertColumnsExist(Class<?> entity, String table) throws IOException {
        String sql = allSql();
        List<String> missing = new ArrayList<>();
        for (String column : columns(entity)) {
            if (!definedFor(sql, table, column)) missing.add(column);
        }
        assertThat(missing).as("%s 에 없는 칸", table).isEmpty();
    }

    private static String allSql() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATIONS)) {
            StringBuilder sb = new StringBuilder();
            for (Path p : files.sorted().toList()) sb.append(Files.readString(p)).append('\n');
            return sb.toString().toLowerCase(Locale.ROOT);
        }
    }

    /** CREATE TABLE 안에 있거나, ALTER TABLE 표 ... ADD COLUMN 칸 으로 더해졌는지 */
    private static boolean definedFor(String sql, String table, String column) {
        Pattern create = Pattern.compile("create table public\\." + table + " \\((.*?)\\n\\);", Pattern.DOTALL);
        var m = create.matcher(sql);
        while (m.find()) {
            if (Pattern.compile("(^|\\n)\\s+" + column + "\\s").matcher(m.group(1)).find()) return true;
        }
        Pattern alter = Pattern.compile("alter table public\\." + table + "\\s(.*?);", Pattern.DOTALL);
        var a = alter.matcher(sql);
        while (a.find()) {
            if (Pattern.compile("add column " + column + "\\s").matcher(a.group(1)).find()) return true;
        }
        return false;
    }

    /** @Column 이름 (없으면 필드 이름을 snake_case 로), @Embedded 안쪽 칸까지 */
    private static List<String> columns(Class<?> type) {
        List<String> names = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (f.isAnnotationPresent(Embedded.class)) {
                names.addAll(columns(f.getType()));
                continue;
            }
            Column c = f.getAnnotation(Column.class);
            if (f.isAnnotationPresent(Id.class)) {
                names.add("id");
            } else if (c != null && !c.name().isEmpty()) {
                names.add(c.name());
            } else {
                names.add(f.getName().replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT));
            }
        }
        return names;
    }
}
