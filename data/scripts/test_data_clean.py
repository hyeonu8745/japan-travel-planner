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


if __name__ == "__main__":
    n = 0
    for k, f in list(globals().items()):
        if k.startswith("test_"):
            f(); n += 1
    print(f"OK {n}개 통과")
