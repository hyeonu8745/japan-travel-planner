"""commons_geo_photos.py 테스트.  프로젝트 루트에서: python data/scripts/test_commons_geo_photos.py"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import commons_geo_photos as cg


def test_이름이_들어간_사진_중_가장_가까운_것을_고른다():
    names = cg.names_of({"name": "十文字原展望台", "nameEn": "Jumonjibaru Observatory"})
    cands = [{"title": "File:Beppu bay 2019.jpg", "dist": 20},                       # 이름 없음
             {"title": "File:十文字原展望台からの夜景.jpg", "dist": 120},
             {"title": "File:Jumonjibaru Observatory 01.JPG", "dist": 80},            # 영어 이름·대문자 확장자
             {"title": "File:十文字原展望台 案内図.jpg", "dist": 10},                   # 안내도
             {"title": "File:十文字原展望台.svg", "dist": 5}]                          # 사진 아님
    assert cg.pick_file(cands, names)["title"] == "File:Jumonjibaru Observatory 01.JPG"


def test_이름이_짧거나_맞는_사진이_없으면_안_붙인다():
    assert cg.names_of({"name": "塔", "nameEn": None}) == []
    assert cg.pick_file([{"title": "File:Some temple.jpg", "dist": 10}], cg.names_of({"name": "大安寺"})) is None


def test_공백_기호를_무시하고_비교한다():
    names = cg.names_of({"name": "油山市民の森 中央展望台"})
    assert cg.title_matches("File:油山市民の森_中央展望台_2020.jpg", names)


if __name__ == "__main__":
    k = 0
    for name, f in list(globals().items()):
        if name.startswith("test_"):
            f(); k += 1
    print(f"OK {k}개 통과")
