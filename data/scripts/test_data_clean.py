"""data_clean.py 관광지·식당 규칙 테스트.  프로젝트 루트에서: python data/scripts/test_data_clean.py"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import data_clean as dc

RULES = dc.load_sensitive()


def excluded(name, **kw):
    r = dc.sensitive_of({"name": name, **kw}, RULES)
    return bool(r and r["action"] == "EXCLUDE")


def test_폐관_장기휴관_관람불가_관광지아닌곳은_뺀다():
    for n in ["神戸らんぷミュージアム", "富士博物館", "富士ビジターセンター", "北浜テルマス温泉（閉館）",
              "九州湯布院民芸村", "豊見城グスク", "日本郵船歴史博物館", "鎌倉文学館",
              "幟町カトリック教会", "カトリック北一条教会", "カトリック大名町教会", "カトリック別府教会",
              "伏見城", "本川公衆便所", "休憩所", "国際通り右入口", "マンボー漫画喫茶 出口(西口)"]:
        assert excluded(n), n
    assert excluded("x", nameEn="Kyushu Yufuin Folk Craft Village")
    assert excluded("Fukuoka Japan Temple")
    # 실제 데이터처럼 영어 이름이 함께 붙어 있어도 뺀다
    assert excluded("伏見城", nameEn="Fushimi Castle")
    assert excluded("富士博物館", nameEn="Fuji Museum")
    assert excluded("カトリック北一条教会", nameEn="Catholic Kita ichi jo church")


def test_비슷한_이름의_정상_관광지는_남긴다():
    for n in ["山梨県立富士山世界遺産センター", "河口湖美術館", "富士山博物館ふじさんミュージアム", "日本郵船氷川丸",
              "鎌倉国宝館", "カトリック浦上教会", "伏見稲荷大社", "休憩所跡の碑", "原爆ドーム", "八窓庵",
              "入口の地蔵堂", "大阪城", "国際通り"]:
        assert not excluded(n), n
    # 다른 성당·박물관은 남긴다
    assert not excluded("カトリック築地教会", nameEn="Tsukiji Catholic Church")
    assert not excluded("河口湖美術館", nameEn="Kawaguchiko Museum of Art")


def test_같은_이름은_500m_안이면_하나로_합친다():
    a = {"name": "戎橋筋商店街", "lat": 34.6680, "lng": 135.5013}
    near = {"name": "戎橋筋 商店街", "lat": 34.6699, "lng": 135.5013}   # 약 210m
    far = {"name": "戎橋筋商店街", "lat": 34.6780, "lng": 135.5013}    # 약 1.1km
    assert dc.same_place([a], near) is a
    assert dc.same_place([a], far) is None


def test_위키데이터가_서로_다르면_가까워도_합치지_않는다():
    # 도쿄 미나토구 安楽寺 두 곳 (약 200m, 서로 다른 절)
    a = {"name": "安楽寺", "lat": 35.6500726, "lng": 139.7525464, "wikidata": "Q119927217", "wikidataIsPlace": True}
    b = {"name": "安楽寺", "lat": 35.6505857, "lng": 139.7547653, "wikidata": "Q115324498", "wikidataIsPlace": True}
    assert dc.same_place([a], b) is None
    c = {"name": "安楽寺", "lat": 35.6505857, "lng": 139.7547653}  # 한쪽에만 있으면 합침
    assert dc.same_place([a], c) is a


def test_주소의_이전_안내문을_잘라낸다():
    a = "大分県別府市北浜2-1-16   このお店は「別府市北浜2-7-6」から移転しています。 ※移転前の情報は最新のものとは異なります。 移転前の店舗情報を見る"
    assert dc.clean_address(a) == "大分県別府市北浜2-1-16"
    assert dc.clean_address("東京都千代田区丸の内1-9-1 東京駅一番街 2F") == "東京都千代田区丸の内1-9-1 東京駅一番街 2F"
    assert dc.clean_address(None) is None


def test_도시에서_너무_먼_식당은_도시권_밖():
    assert dc.in_city("takayama", 36.1408, 137.2522)            # 다카야마 시내
    assert not dc.in_city("takayama", 36.2440, 137.5050)        # 오쿠히다 온천 (약 28km)
    assert dc.in_city("kanazawa", 36.4820, 136.7570)            # 유와쿠 온천 (가나자와시, 약 12km)
    assert not dc.in_city("yufuin", 33.2480, 131.5000)          # 벳푸 쪽 (약 13km)
    assert dc.in_city("unknown", 0, 0)                          # 반경 모르는 도시는 거르지 않음


def test_하코네에_섞인_유가와라_마나즈루는_다른_동네():
    towns = dc.OTHER_TOWNS["hakone"]
    assert any(t in "神奈川県足柄下郡湯河原町宮下32-3" for t in towns)
    assert any(t in "神奈川県足柄下郡真鶴町真鶴1200-18" for t in towns)
    assert not any(t in "神奈川県小田原市風祭77-1" for t in towns)       # 오다와라는 남김
    assert not any(t in "神奈川県足柄下郡箱根町湯本706" for t in towns)


def test_편의시설은_가장_가까운_도시권으로_나누고_밖이면_버린다():
    assert dc.city_of(35.681, 139.767) == "tokyo"          # 도쿄역
    assert dc.city_of(35.4437, 139.6380) == "yokohama"     # 요코하마역 (도쿄 반경 안이지만 요코하마가 더 가까움)
    assert dc.city_of(35.3192, 139.5503) == "kamakura"     # 가마쿠라역
    assert dc.city_of(35.2824, 139.6680) is None           # 요코스카 (도쿄 파일에 섞여 있던 환전소)
    assert dc.city_of(35.6558, 139.3389) is None           # 하치오지 (도쿄역에서 약 39km)


def _a(name, city="tokyo", lat=35.66, lng=139.73):
    return {"name": name, "city": city, "lat": lat, "lng": lng, "areas": [city + "_x"]}


def test_야경_명소는_이름이_같거나_포함되면_기존_관광지에_붙인다():
    cands = [_a("六本木ヒルズ"), _a("東京シティビュー"), _a("東京タワー")]
    assert dc.match_night(cands, "東京タワー")["name"] == "東京タワー"
    assert dc.match_night(cands, "六本木ヒルズ 東京シティビュー")["name"] == "東京シティビュー"   # 더 긴 이름
    assert dc.match_night([_a("JRタワー展望室T38")], "JRタワー展望室 T38") is not None              # 공백 차이
    assert dc.match_night([_a("塔")], "東京スカイツリー塔") is None                                  # 2자 이하는 부분 일치 안 함
    assert dc.match_night(cands, "福岡タワー") is None


def test_데이터에_없는_야경_명소는_OSM_좌표로_추가하고_도시_밖이면_버린다(monkeypatch=None):
    out = [_a("福岡市博物館", "fukuoka", 33.5893, 130.3530)]
    spots = [{"city": "fukuoka", "name": "福岡タワー", "kind": "전망대", "hours": "9:30-22:00", "fee": "800엔", "closed": None, "url": None},
             {"city": "fukuoka", "name": "遠い展望台", "kind": "전망대", "hours": None, "fee": None, "closed": None, "url": None},
             {"city": "fukuoka", "name": "福岡市博物館", "kind": "야간 개장", "hours": None, "fee": None, "closed": None, "url": None}]
    osm = {("fukuoka", "福岡タワー"): {"osmId": "way/1", "lat": 33.5933, "lng": 130.3515},
           ("fukuoka", "遠い展望台"): {"osmId": "node/2", "lat": 34.5, "lng": 131.5}}
    old = dc.load_night_spots, dc.load_night_osm
    dc.load_night_spots, dc.load_night_osm = (lambda: spots), (lambda: osm)
    try:
        tagged, added, missing = dc.apply_night_spots(out, RULES)
    finally:
        dc.load_night_spots, dc.load_night_osm = old
    assert (tagged, added, missing) == (1, 1, ["fukuoka:遠い展望台"])
    tower = next(a for a in out if a["name"] == "福岡タワー")
    assert tower["kind"] == "VIEW" and tower["nightView"] and tower["nightInfo"]["fee"] == "800엔"
    assert tower["areas"] == ["fukuoka_x"]                     # 가장 가까운 관광지의 지역을 따름
    assert out[0]["nightView"] is True                          # 기존 관광지에 표시


def test_야경_제외_목록():
    assert {"灰ヶ峰", "アメリカンビレッジ", "箱根ロープウェイ"} <= dc.NIGHT_SKIP


def test_정기휴일_글에서_요일과_표시를_뽑는다():
    ci = dc.closed_info
    assert ci("月・木 12:00 - 15:00 火・水 定休日")["closedDays"] == ["TUE", "WED"]
    assert ci("17:00 - 23:00 ■ 定休日月曜日(祝日の場合は翌日)")["closedDays"] == ["MON"]
    assert ci("■ 定休日毎週水曜日")["closedDays"] == ["WED"]
    assert ci("■定休日火曜日")["closedDays"] == ["TUE"]
    assert ci("■ 定休日第３水曜日")["closedDays"] == []                # 매주가 아님
    assert ci("■ 定休日毎月第2火曜日")["closedDays"] == []
    r = ci("■ 定休日年中無休（ホテルに準ずる）")
    assert r["closedDays"] == [] and r["noRegularHoliday"]
    assert ci(None, "日・祝") == {"closedDays": ["SUN"], "closedIrregular": False, "noRegularHoliday": False, "closedOnHolidays": True}
    assert ci(None, "日曜日、祝日")["closedDays"] == ["SUN"]
    assert ci(None, "月")["closedDays"] == ["MON"]
    assert ci(None, "無休")["noRegularHoliday"] and ci(None, "なし")["noRegularHoliday"]
    assert ci(None, "年末年始")["closedDays"] == [] and not ci(None, "年末年始")["noRegularHoliday"]
    assert ci("■ 定休日不定休")["closedIrregular"]
    assert ci(None, "日曜日、祝日、第1・3月曜日")["closedDays"] == ["SUN"]   # 第… 부분만 빼고 일요일은 남김
    assert ci(None, "不定期")["closedIrregular"]
    assert ci("■ 定休日無")["noRegularHoliday"]


def test_제미나이_공식_정보를_관광지에_붙인다():
    out = [_a("清水寺", "kyoto"), _a("東京タワー")]
    rows = [{"city": "kyoto", "name": "清水寺", "status": "운영", "reservation": "불필요", "closedDays": "무휴",
             "hours": "06:00~18:00", "fee": "400엔", "url": None},
            {"city": "kyoto", "name": "없는 절", "status": "운영", "reservation": None, "closedDays": None, "hours": None, "fee": None, "url": None}]
    assert dc.apply_official_info(out, rows) == 1
    assert out[0]["officialInfo"]["fee"] == "400엔" and out[1]["officialInfo"] is None


if __name__ == "__main__":
    n = 0
    for k, f in list(globals().items()):
        if k.startswith("test_"):
            f(); n += 1
    print(f"OK {n}개 통과")
