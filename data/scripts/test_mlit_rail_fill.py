"""mlit_rail_fill.py 테스트.  프로젝트 루트에서: python data/scripts/test_mlit_rail_fill.py"""
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mlit_rail_fill as mf


def test_섞인_역을_한_줄_순서로_되돌린다():
    # 게이한 본선 교토 구간 실제 위치 (出町柳 → 伏見稲荷)
    keihan = [("出町柳", 35.0300, 135.7730), ("神宮丸太町", 35.0170, 135.7720), ("三条", 35.0092, 135.7722),
              ("祇園四条", 35.0035, 135.7720), ("清水五条", 34.9960, 135.7690), ("七条", 34.9887, 135.7685),
              ("東福寺", 34.9810, 135.7700), ("鳥羽街道", 34.9745, 135.7700), ("伏見稲荷", 34.9675, 135.7705)]
    rnd = random.Random(1)
    shuffled = keihan[:]
    rnd.shuffle(shuffled)
    order = mf.order_stations([(lat, lng) for _, lat, lng in shuffled])
    names = [shuffled[i][0] for i in order]
    assert names in ([k[0] for k in keihan], [k[0] for k in keihan][::-1]), names


def test_굽은_노선도_꼬이지_않는다():
    # 반원 위의 역 (함관선 오타루 구간처럼 해안을 따라 굽는 노선)
    import math
    pts = [(43.0 + 0.1 * math.sin(t / 10 * math.pi), 141.0 + 0.1 * math.cos(t / 10 * math.pi)) for t in range(11)]
    rnd = random.Random(7)
    idx = list(range(11))
    rnd.shuffle(idx)
    order = mf.order_stations([pts[i] for i in idx])
    got = [idx[i] for i in order]
    assert got in (list(range(11)), list(range(10, -1, -1))), got


def test_역이_두_개_이하면_그대로():
    assert mf.order_stations([(1, 1)]) == [0]
    assert mf.order_stations([(1, 1), (1, 2)]) == [0, 1]


def test_경계에_걸린_중간_역도_남긴다():
    seq = ["A", "B", "C", "D", "E"]
    inside = {"B", "D"}
    assert mf.trim_span(seq, lambda s: s in inside) == ["B", "C", "D"]   # C 는 밖이어도 사이라서 남김
    assert mf.trim_span(seq, lambda s: False) == []


def test_역_이름_비교는_역_괄호_공백을_무시한다():
    assert mf.n("押上〈スカイツリー前〉") == mf.n("押上 スカイツリー前")
    assert mf.n("小樽駅") == mf.n("小樽")


if __name__ == "__main__":
    k = 0
    for name, f in list(globals().items()):
        if name.startswith("test_"):
            f(); k += 1
    print(f"OK {k}개 통과")
