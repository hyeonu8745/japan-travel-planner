package com.example.japantravelplanner.poster;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 포스터 한 장. 그림은 파일로 두고 경로만 저장한다 */
@Entity
@Table(name = "trip_poster_pages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripPosterPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poster_id", nullable = false)
    private TripPoster poster;

    @Column(name = "page_no", nullable = false)
    private int pageNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PosterPageKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PosterStatus status;

    @Column(length = 20)
    private String size;

    /** 이미지 AI에 보낸 글 (결과가 이상할 때 확인용) */
    @Column(columnDefinition = "text")
    private String prompt;

    @Column(name = "file_path", length = 300)
    private String filePath;

    public static TripPosterPage of(int pageNo, PosterPageKind kind, String size, String prompt) {
        TripPosterPage p = new TripPosterPage();
        p.pageNo = pageNo;
        p.kind = kind;
        p.size = size;
        p.prompt = prompt;
        p.status = PosterStatus.GENERATING;
        return p;
    }

    void attach(TripPoster poster) {
        this.poster = poster;
    }

    public void done(String filePath) {
        this.filePath = filePath;
        this.status = PosterStatus.DONE;
    }

    public void failed() {
        this.status = PosterStatus.FAILED;
    }
}
