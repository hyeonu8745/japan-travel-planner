package com.example.japantravelplanner.poster;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 포스터 그림 파일 (storage/posters/{tripId}/{posterId}-{pageNo}.png). DB에는 상대 경로만 */
@Component
public class PosterStorage {

    private final Path root;

    public PosterStorage(PosterProperties props) {
        this.root = Path.of(props.getStorageDir()).toAbsolutePath().normalize();
    }

    public String save(long tripId, long posterId, int pageNo, byte[] png) {
        String relative = tripId + "/" + posterId + "-" + pageNo + ".png";
        Path file = resolve(relative);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, png);
        } catch (IOException e) {
            throw new UncheckedIOException("포스터 파일 저장 실패", e);
        }
        return relative;
    }

    public byte[] read(String relative) {
        try {
            return Files.readAllBytes(resolve(relative));
        } catch (IOException e) {
            throw new UncheckedIOException("포스터 파일 읽기 실패", e);
        }
    }

    public void deleteQuietly(String relative) {
        if (relative == null) return;
        try {
            Files.deleteIfExists(resolve(relative));
        } catch (IOException ignored) {
            // 파일이 없거나 지울 수 없어도 여행 삭제는 계속한다
        }
    }

    private Path resolve(String relative) {
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("잘못된 파일 경로");
        return file;
    }
}
