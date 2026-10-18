"""osm_rail_collect.route_stops 테스트 (옛 방식·새 방식 노선 역할).  프로젝트 루트에서: python data/scripts/test_osm_route_roles.py"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import osm_rail_collect as orc


def m(ref, role, typ="node"):
    return {"type": typ, "ref": ref, "role": role}


def test_새_방식은_stop_platform_역할만():
    r = orc.route_stops([m(1, "stop"), m(2, "platform"), m(3, ""), m(9, "", "way"), m(4, "stop_exit_only"), m(5, "platform_entry_only")])
    assert r == {"stops": [1, 4], "platforms": [2, 5]}


def test_역할이_빈_옛_방식_노선은_노드를_정류장으로():
    # 나라 시내순환: 노드 18개가 모두 역할 없음
    r = orc.route_stops([m(10, ""), m(11, ""), m(100, "forward", "way"), m(12, "")])
    assert r["stops"] == [10, 11, 12] and r["ptv1"] is True and "backwardStops" not in r


def test_forward_backward_stop_은_방향을_나눈다():
    # 가마쿠라 버스: forward_stop / backward_stop (뒤에 공백이 붙은 역할도 있음)
    r = orc.route_stops([m(1, "forward_stop"), m(2, "forward_stop "), m(3, "backward_stop"), m(4, "backward_stop")])
    assert r["stops"] == [1, 2] and r["backwardStops"] == [3, 4] and r["ptv1"] is True


def test_노드가_없으면_빈_목록():
    assert orc.route_stops([m(1, "", "way")]) == {"stops": [], "platforms": [], "ptv1": False}


if __name__ == "__main__":
    k = 0
    for name, f in list(globals().items()):
        if name.startswith("test_"):
            f(); k += 1
    print(f"OK {k}개 통과")
