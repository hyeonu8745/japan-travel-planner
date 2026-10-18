"""부가 데이터 검사 (10-18): 관광지 사진·소개, 영업시간, 공휴일, 온천·로커 등.  프로젝트 루트에서: python data/scripts/extra_audit.py"""
import json
import re
import sys
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from data_clean import CITY_RADIUS, km, city_of  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
D = ROOT / "data"
out = []


def P(*a):
    out.append(" ".join(str(x) for x in a))


def J(p):
    return json.loads((D / p).read_text(encoding="utf-8"))


A = J("processed/clean_attractions.json")
T = J("processed/clean_restaurants_tabelog.json")
H = J("processed/clean_restaurants_chain.json")

# ---------- 1. 사진·소개 ----------
M = J("extra/attraction_media.json")
P("==== 1. 관광지 사진·한국어 소개 (attraction_media.json)", len(M), "건")
keys = Counter(k for v in M.values() for k in v)
P("  항목별 개수", dict(keys.most_common()))
wd = {a["wikidata"] for a in A if a.get("wikidata")}
P("  관광지 중 위키데이터 있음", len(wd), "/", len(A), "| 미디어 항목 있음", len(wd & set(M)), "| 미디어에만 있음(빠진 관광지)", len(set(M) - wd))
empty = [k for k, v in M.items() if not v]
P("  빈 항목", len(empty))
sample = next((v for v in M.values() if v), {})
P("  예시", json.dumps(sample, ensure_ascii=False)[:600])
# 사진 출처 표시에 필요한 칸
photo = {k: v for k, v in M.items() if any(x in v for x in ("image", "photo", "imageUrl", "commonsFile", "file"))}
P("  사진 있는 항목", len(photo))
# ---------- 2. 관광지 영업시간 (OSM opening_hours) ----------
P("\n==== 2. 관광지 영업시간 (OSM opening_hours)")
oh = [a["openingHours"] for a in A if a.get("openingHours")]
P("  있음", len(oh), "/", len(A))
simple = re.compile(r"^((Mo|Tu|We|Th|Fr|Sa|Su|PH)(-(Mo|Tu|We|Th|Fr|Sa|Su))?(,(Mo|Tu|We|Th|Fr|Sa|Su|PH)(-(Mo|Tu|We|Th|Fr|Sa|Su))?)* )?"
                    r"(\d{1,2}:\d{2}-\d{1,2}:\d{2}\+?)(,\d{1,2}:\d{2}-\d{1,2}:\d{2})*"
                    r"(; ?((Mo|Tu|We|Th|Fr|Sa|Su|PH)(-(Mo|Tu|We|Th|Fr|Sa|Su))?(,(Mo|Tu|We|Th|Fr|Sa|Su|PH))* )?(\d{1,2}:\d{2}-\d{1,2}:\d{2}\+?(,\d{1,2}:\d{2}-\d{1,2}:\d{2})*|off|closed))*$")
ok = [x for x in oh if simple.match(x.strip()) or x.strip() == "24/7"]
bad = [x for x in oh if x not in ok]
P("  단순 형식(요일 + 시간)으로 읽힘", len(ok), "| 복잡·비표준", len(bad))
P("  비표준 예", bad[:12])
late = [a["name"] for a in A if a.get("openingHours") and re.search(r"\b(2[4-9]|3\d):", a["openingHours"])]
P("  24시 넘김 표기", len(late))
# ---------- 3. 식당 영업시간·휴일·예산 ----------
P("\n==== 3. 식당 (타베로그) 영업시간·휴일·예산")
for src in ("apify", "kaggle"):
    s = [t for t in T if t["source"] == src]
    P(f"  [{src}] {len(s)}곳: 영업시간 {sum(1 for t in s if t.get('businessHours'))}, 정기휴일 요일 {sum(1 for t in s if t['closedDays'])}, "
      f"부정기 {sum(t['closedIrregular'] for t in s)}, 점심예산 {sum(1 for t in s if t['budgetLunchMax'])}, 저녁예산 {sum(1 for t in s if t['budgetDinnerMax'])}")
miss = [t for t in T if "定休日" in (t.get("businessHours") or "") and not t["closedDays"] and not t["closedIrregular"]]
P("  영업시간에 「定休日」가 있는데 요일을 못 뽑은 곳", len(miss))
for t in miss[:6]:
    m = re.search(r".{0,25}定休日.{0,15}", t["businessHours"])
    P("    ", t["name"], "|", m.group(0) if m else "")
hol = [t for t in T if t["source"] == "kaggle" and t.get("holiday") and not t["closedDays"] and not t["closedIrregular"]]
P("  캐글 휴일 텍스트가 있는데 요일 없음", len(hol), Counter(t["holiday"][:12] for t in hol).most_common(8))
weird = [t for t in T if t["budgetLunchMax"] and t["budgetDinnerMax"] and t["budgetLunchMax"] > t["budgetDinnerMax"] * 2]
P("  점심 예산이 저녁의 2배 넘음", len(weird))
P("  평점 분포", Counter(round(t["rating"], 1) for t in T if t.get("rating")).most_common(6), "평점 없음", sum(1 for t in T if not t.get("rating")))
P("  한국어 메뉴", sum(t["koreanMenu"] for t in T), "| 백명점", sum(t["hyakumeiten"] for t in T), "| 완전 예약제", sum(t["reservationOnly"] for t in T))
# ---------- 4. 체인점 ----------
P("\n==== 4. 체인점")
P("  영업시간 있음", sum(1 for h in H if h.get("openingHours")), "/", len(H), "| 예산 없음", sum(1 for h in H if not h.get("budgetMin")),
  "| 도시권 밖", sum(1 for h in H if h["city"] in CITY_RADIUS and km(CITY_RADIUS[h["city"]][:2], (h["lat"], h["lng"])) > CITY_RADIUS[h["city"]][2]))
P("  브랜드 상위", Counter(h["brand"] for h in H).most_common(8))
# ---------- 5. 공휴일 ----------
P("\n==== 5. 공휴일 (japan_holidays.json)")
Hd = J("extra/japan_holidays.json")
years = Counter(h["date"][:4] for h in Hd)
P("  ", len(Hd), "건, 범위", min(years), "~", max(years), "| 2026:", years.get("2026"), "2027:", years.get("2027"))
P("  2026 목록", [h["date"][5:] + h["name"] for h in Hd if h["date"].startswith("2026")])
dup = [d for d, c in Counter(h["date"] for h in Hd).items() if c > 1]
P("  같은 날 두 번", dup[:5])
# ---------- 6. 온천·로커·안내소·편의점·ATM (osm_rail/<지역>_extra.json) ----------
P("\n==== 6. 온천·로커·안내소·편의점·ATM")
for f in sorted((D / "osm_rail").glob("*_extra.json")):
    e = json.loads(f.read_text(encoding="utf-8"))
    rows = [x for v in e.values() for x in v]
    noname = sum(1 for x in rows if not x.get("name") and x.get("osmId"))
    outside = sum(1 for x in rows if x.get("lat") is not None and city_of(x["lat"], x["lng"]) is None)
    P(f"  {f.stem[:-6]:12s} {({k: len(v) for k, v in e.items()})} 이름 없음 {noname}, 도시권 밖 {outside}")
bath = [x for f in (D / "osm_rail").glob("*_extra.json") for x in json.loads(f.read_text(encoding="utf-8")).get("publicBath", [])]
P("  온천 종류", Counter(x.get("bathType") for x in bath).most_common(6))
# ---------- 7. 휠체어 ----------
W = J("osm_rail/attractions_wheelchair.json")
ids = {a["osmId"] for a in A}
P("\n==== 7. 휠체어 정보", len(W), "건, 관광지와 연결", sum(1 for k in W if k in ids), Counter(W[k] for k in W if k in ids))
# ---------- 8. 제미나이 조사 결과와 관광지 연결 ----------
P("\n==== 8. 제미나이 조사 결과")
for fn in ("attraction_check_gemini.tsv", "attraction_check_gemini_2.tsv"):
    rows = [l.split("\t") for l in (D / "extra" / fn).read_text(encoding="utf-8").splitlines()[1:] if l.strip()]
    names = {re.sub(r"\s", "", a["name"]) for a in A}
    hit = sum(1 for r in rows if len(r) > 1 and re.sub(r"\s", "", r[1]) in names)
    P(f"  {fn}: {len(rows)}줄, 관광지 이름과 맞음 {hit}, 상태 {Counter(r[2] for r in rows if len(r) > 2).most_common(6)}")
print("\n".join(out))
(D / "processed" / "extra_audit_report.txt").write_text("\n".join(out) + "\n", encoding="utf-8")
