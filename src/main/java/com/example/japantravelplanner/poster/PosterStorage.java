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

    /** @param props 저장 폴더 (storage/posters) */
    public PosterStorage(PosterProperties props) {
        this.root = Path.of(props.getStorageDir()).toAbsolutePath().normalize();
    }

    /** PNG 를 저장하고 상대 경로를 돌려준다 ({tripId}/{posterId}-{pageNo}.png) */
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

    /** 저장된 PNG 읽기 */
    public byte[] read(String relative) {
        try {
            return Files.readAllBytes(resolve(relative));
        } catch (IOException e) {
            throw new UncheckedIOException("포스터 파일 읽기 실패", e);
        }
    }

    /** 파일 지우기 (없거나 실패해도 조용히) */
    public void deleteQuietly(String relative) {
        if (relative == null) return;
        try {
            Files.deleteIfExists(resolve(relative));
        } catch (IOException ignored) {
            // 파일이 없거나 지울 수 없어도 여행 삭제는 계속한다
        }
    }

    /** 상대 경로 → 실제 경로. 저장 폴더 밖으로 나가는 경로(../ 등)는 거절 */
    private Path resolve(String relative) {
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("잘못된 파일 경로");
        return file;
    }
}
