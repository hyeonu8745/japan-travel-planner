"""위키데이터 사진이 없는 관광지에, 위키미디어 공용에서 그 장소 근처에서 찍힌 사진을 찾아 붙인다 (10-19).
인터넷이 되는 PC에서 실행 (약 1,350곳, 40분 안팎 — 위키미디어가 빠른 요청을 막아서 1.5초 간격). 끊겨도 다시 실행하면 이어서 한다.

    프로젝트 루트에서:  python data/scripts/commons_geo_photos.py

고르는 기준 (엉뚱한 사진을 막기 위해)
  - 관광지 좌표 반경(전망·공원 500m, 그 밖 250m) 안의 사진 파일
  - 파일 이름에 관광지 이름(일본어·영어, 공백·기호 무시, 3자 이상)이 들어간 것만
  - 지도·로고·안내판·평면도 사진, 사진이 아닌 파일(svg·pdf 등)은 뺌
  - 그중 가장 가까운 사진
출력  data/extra/attraction_media_geo.json  {osmId: {image, thumb, width, height, credit, license, licenseUrl, artist, distance} | {}}
다음  python data/scripts/data_clean.py  (관광지에 photo 필드로 붙음)
"""
import json
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ATTR = ROOT / "data" / "processed" / "clean_attractions.json"
MEDIA = ROOT / "data" / "extra" / "attraction_media.json"
DST = ROOT / "data" / "extra" / "attraction_media_geo.json"
API = "https://commons.wikimedia.org/w/api.php"
UA = "TabiMate-student-project/1.0 (commons geo photos; non-commercial)"
PHOTO_EXT = (".jpg", ".jpeg", ".png", ".webp")
BAD_WORDS = re.compile(r"(?i)map|地図|案内図|logo|ロゴ|sign|看板|案内板|plan|平面図|diagram|icon|flag|旗|stamp|スタンプ|ticket|パンフ")
WIDE_KINDS = {"VIEW", "PARK", "CASTLE", "THEME"}


def key(s):
    return re.sub(r"[\s_\-・･.,'’()（）「」『』〈〉【】]", "", unicodedata.normalize("NFKC", s or "")).lower()


def names_of(a):
    out = []
    for n in (a.get("name"), a.get("nameJa"), a.get("nameEn")):
        k = key(n)
        if len(k) >= 3 and k not in out:
            out.append(k)
    return out


def title_matches(title, names):
    t = key(re.sub(r"^File:", "", title))
    return any(n in t for n in names)


def pick_file(cands, names):
    """geosearch 결과 [{title, dist}] 중 사진이고, 이름이 들어가고, 지도·로고가 아닌 것 중 가장 가까운 것"""
    ok = [c for c in cands
          if c["title"].lower().endswith(PHOTO_EXT) and not BAD_WORDS.search(c["title"]) and title_matches(c["title"], names)]
    return min(ok, key=lambda c: c["dist"]) if ok else None


PAUSE = 1.5          # 요청 사이 쉬는 초 (10-19: 0.3초로는 429 Too Many Requests)
_fail_streak = 0


class RateLimited(Exception):
    pass


def get_json(params, tries=4):
    """429·503 이면 서버가 알려 준 시간(Retry-After, 없으면 30·60·120초)만큼 쉬고 다시. 연속으로 막히면 RateLimited."""
    global _fail_streak
    url = API + "?" + urllib.parse.urlencode({**params, "format": "json", "formatversion": 2, "maxlag": 5})
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Encoding": "identity"})
            with urllib.request.urlopen(req, timeout=60) as r:
                data = json.loads(r.read().decode())
            if (data.get("error") or {}).get("code") == "maxlag":
                raise urllib.error.HTTPError(url, 503, "maxlag", {}, None)
            _fail_streak = 0
            return data
        except urllib.error.HTTPError as e:
            if e.code in (429, 503):
                wait = int((e.headers or {}).get("Retry-After") or 0) or 30 * 2 ** i
                print(f"  서버가 천천히 하라고 해서 {wait}초 쉼 ({e.code})")
                time.sleep(wait)
                continue
            print(f"  실패: {e}")
            return None
        except Exception as e:  # noqa: BLE001
            print(f"  실패: {e}")
            time.sleep(5 * (i + 1))
    _fail_streak += 1
    if _fail_streak >= 3:
        raise RateLimited()
    return None


def strip_html(s):
    return re.sub(r"<[^>]+>", "", s or "").strip() or None


def main():
    attrs = json.loads(ATTR.read_text(encoding="utf-8"))
    media = json.loads(MEDIA.read_text(encoding="utf-8")) if MEDIA.exists() else {}
    geo = json.loads(DST.read_text(encoding="utf-8")) if DST.exists() else {}
    todo = [a for a in attrs
            if not (a.get("wikidata") and (media.get(a["wikidata"]) or {}).get("thumb")) and a["osmId"] not in geo]
    print(f"사진 없는 관광지 중 새로 찾을 곳 {len(todo)}")
    picked = {}
    try:
        search(todo, geo, picked)
    except RateLimited:
        DST.write_text(json.dumps(geo, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"서버가 계속 막아서 멈췄어요. 여기까지 저장했어요 ({len(geo)}곳). 10~30분 뒤 다시 실행하면 이어서 해요.")
        return
    fill_info(geo)


def search(todo, geo, picked):
    for n, a in enumerate(todo, 1):
        names = names_of(a)
        if not names:
            geo[a["osmId"]] = {}
            continue
        radius = 500 if a["kind"] in WIDE_KINDS else 250
        data = get_json({"action": "query", "list": "geosearch", "gscoord": f"{a['lat']}|{a['lng']}",
                         "gsradius": radius, "gsnamespace": 6, "gslimit": 100})
        if data is None:
            continue   # 다음 실행 때 다시
        best = pick_file(data.get("query", {}).get("geosearch", []), names)
        geo[a["osmId"]] = {"image": best["title"][5:], "distance": round(best["dist"])} if best else {}
        if best:
            picked[a["osmId"]] = best["title"]
        if n % 50 == 0 or n == len(todo):
            print(f"  {n}/{len(todo)} (사진 찾음 {len(picked)})")
            DST.write_text(json.dumps(geo, ensure_ascii=False, indent=1), encoding="utf-8")
        time.sleep(PAUSE)
    DST.write_text(json.dumps(geo, ensure_ascii=False, indent=1), encoding="utf-8")


def fill_info(geo):
    """사진 주소(800px)·라이선스·작가"""
    need = [k for k, v in geo.items() if v.get("image") and "thumb" not in v]
    for i in range(0, len(need), 50):
        batch = need[i:i + 50]
        data = get_json({"action": "query", "titles": "|".join("File:" + geo[k]["image"] for k in batch),
                         "prop": "imageinfo", "iiprop": "url|extmetadata|size", "iiurlwidth": 800})
        if data is None:
            continue
        info_by = {}
        for p in data.get("query", {}).get("pages", []):
            info = (p.get("imageinfo") or [{}])[0]
            meta = info.get("extmetadata", {})
            info_by[key(p.get("title", ""))] = {
                "thumb": info.get("thumburl"), "width": info.get("thumbwidth"), "height": info.get("thumbheight"),
                "credit": info.get("descriptionurl"), "license": strip_html(meta.get("LicenseShortName", {}).get("value")),
                "licenseUrl": meta.get("LicenseUrl", {}).get("value"), "artist": strip_html(meta.get("Artist", {}).get("value"))}
        for k in batch:
            geo[k].update(info_by.get(key("File:" + geo[k]["image"]), {"thumb": None}))
        print(f"  사진 정보 {min(i + 50, len(need))}/{len(need)}")
        DST.write_text(json.dumps(geo, ensure_ascii=False, indent=1), encoding="utf-8")
        time.sleep(PAUSE)
    DST.write_text(json.dumps(geo, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"저장: {DST} (사진 붙은 곳 {sum(1 for v in geo.values() if v.get('thumb'))} / 찾아본 곳 {len(geo)})")


if __name__ == "__main__":
    main()
