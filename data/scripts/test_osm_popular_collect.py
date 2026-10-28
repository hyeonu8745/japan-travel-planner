"""osm_popular_collect.py 테스트 (이름으로 찾은 후보 고르기).  프로젝트 루트에서: python data/scripts/test_osm_popular_collect.py"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import osm_popular_collect as op


def pick(cands, pats):
    good = [c for c in cands if op.score(c, pats)[0]]
    return max(good, key=lambda c: op.score(c, pats))["name"] if good else None


def c(osm, name, tags, wd=None):
    return {"osmId": osm, "name": name, "tags": tags, "wikidata": wd}


def test_호텔_학교는_고르지_않는다():
    assert pick([c("way/1", "ドーミーイン PREMIUM 博多キャナルシティ前", {"tourism": "hotel"}),
                 c("way/2", "キャナルシティ博多", {"shop": "mall"}, "Q1")], ["キャナルシティ"]) == "キャナルシティ博多"
    assert pick([c("way/3", "市立百道浜小学校", {"amenity": "school"}),
                 c("way/4", "シーサイドももち海浜公園", {"leisure": "park"})], ["ももち浜", "百道浜"]) == "シーサイドももち海浜公園"
    assert pick([c("way/5", "東山ホテル", {"tourism": "hotel"})], ["東山"]) is None


def test_이름이_정확히_같은_곳을_먼저():
    assert pick([c("node/1", "金閣寺道", {"highway": "bus_stop"}), c("way/2", "金閣寺前の店", {"shop": "gift"}),
                 c("way/3", "金閣寺", {"amenity": "place_of_worship"})], ["金閣寺"]) == "金閣寺"


def test_위키데이터가_계속_429면_쉬었다가_끝내_None():
    import urllib.error
    calls, sleeps = [], []
    def fake_open(req, timeout=0):
        calls.append(req.full_url)
        raise urllib.error.HTTPError(req.full_url, 429, "Too Many", {"Retry-After": "7"}, None)
    old_open, old_sleep = op.urllib.request.urlopen, op.time.sleep
    op.urllib.request.urlopen, op.time.sleep = fake_open, sleeps.append
    try:
        assert op.wiki_entities(["Q1", "Q2"], tries=3) is None
    finally:
        op.urllib.request.urlopen, op.time.sleep = old_open, old_sleep
    assert len(calls) == 3 and sleeps == [7, 7, 7] and "maxlag=5" in calls[0]


if __name__ == "__main__":
    k = 0
    for name, f in list(globals().items()):
        if name.startswith("test_"):
            f(); k += 1
    print(f"OK {k}개 통과")
