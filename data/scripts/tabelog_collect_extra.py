"""
[추가 지역용] tabelog_collect.py 와 같은 동작, 목록만 tabelog_areas_extra.tsv 를 읽는다. 예산 기본 4달러.
기존 목록·스크립트·받은 파일은 건드리지 않는다 (같은 이름 파일이 있으면 건너뜀).

타베로그 지역 랭킹을 Apify(huggable_quote/tabelog-scraper)로 받아 지역·페이지별 JSON으로 저장한다.

- PC: 프로젝트 루트에서  python data/scripts/tabelog_collect.py
      .env 의 APIFY_TOKEN 을 읽고 data/tabelog_raw/ 에 저장
- 코랩: 셀에 붙여 넣고 실행. Secrets(열쇠 아이콘)의 APIFY_TOKEN, 구글 드라이브 MyDrive/tabelog_raw/ 에 저장
- 이미 받은 파일(같은 이름)은 건너뛴다 → 중간에 끊겨도 다시 실행하면 이어서 받는다
- 리뷰는 받지 않는다(includeReviews=false). 수집 데이터는 비공개로만 쓴다(타베로그 약관)

설치: pip install apify-client python-dotenv
"""
import json
import os
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ACTOR = "huggable_quote/tabelog-scraper"
PER_RUN = 100          # 스크래퍼 한 번 실행의 최대 개수 (= 5페이지)
PAGES_PER_RUN = PER_RUN // 20
BUDGET_USD = float(os.environ.get("TABELOG_EXTRA_BUDGET_USD", "3.9"))   # 이 금액을 넘으면 새 실행을 시작하지 않는다
PARALLEL = 6           # 동시에 돌릴 실행 수
# 10-16: 도구가 10-14에 0.1.26·0.1.27로 바뀐 뒤 평점 3.1~3.4대 가게 대부분이 "Schema validation failed"로 저장되지 않음
# → 10-12에 잘 받았던 0.1.25로 돌린다 (비우면 최신). TABELOG_ONLY=지역이름 이면 그 지역만 (시험용)
BUILD = os.environ.get("TABELOG_BUILD", "0.1.25") or None
ONLY = os.environ.get("TABELOG_ONLY", "").strip()

IN_COLAB = "google.colab" in sys.modules


def setup():
    if IN_COLAB:
        from google.colab import drive, userdata
        drive.mount("/content/drive")
        token = userdata.get("APIFY_TOKEN")
        out = Path("/content/drive/MyDrive/tabelog_raw")
        areas = Path("/content/drive/MyDrive/tabelog_areas_extra.tsv")
    else:
        from dotenv import load_dotenv
        root = Path(__file__).resolve().parents[2]
        load_dotenv(root / ".env")
        token = os.environ.get("APIFY_TOKEN")
        out = root / "data" / "tabelog_raw"
        areas = Path(__file__).resolve().parent / "tabelog_areas_extra.tsv"
    if not token:
        sys.exit("APIFY_TOKEN 이 없어요 (.env 또는 코랩 Secrets)")
    out.mkdir(parents=True, exist_ok=True)
    return token, out, areas


def page_url(first_page_url: str, page: int) -> str:
    """.../rstLst/?SrtT=rt...  →  .../rstLst/6/?SrtT=rt..."""
    if page == 1:
        return first_page_url
    base, _, query = first_page_url.partition("?")
    base = base.rstrip("/") + f"/{page}/"
    return base + ("?" + query if query else "")


def load_jobs(areas_file: Path, out: Path):
    jobs = []
    for line in areas_file.read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        cols = line.split("\t")
        name, url = cols[0].strip(), (cols[1].strip() if len(cols) > 1 else "")
        pages = int(cols[2]) if len(cols) > 2 and cols[2].strip() else 10
        if not url:
            print(f"  (건너뜀) {name}: 주소 없음")
            continue
        for start in range(1, pages + 1, PAGES_PER_RUN):
            end = min(start + PAGES_PER_RUN - 1, pages)
            file = out / f"{name}_p{start:02d}-{end:02d}.json"
            jobs.append((name, start, end, page_url(url, start), (end - start + 1) * 20, file))
    return jobs


def run_one(client, job):
    name, start, end, url, limit, file = job
    run = client.actor(ACTOR).call(run_input={
        "startUrls": [{"url": url}],
        "maxRestaurants": limit,
        "includeReviews": False,
    }, build=BUILD)
    items = [as_dict(x) for x in client.dataset(field(run, "default_dataset_id", "defaultDatasetId")).iterate_items()]
    file.write_text(json.dumps(items, ensure_ascii=False, indent=1), encoding="utf-8")
    cost = field(run, "usage_total_usd", "usageTotalUsd") or len(items) * 0.003   # 비용 값을 못 받으면 건당 단가로 추정
    good = sum(1 for x in items if (x.get("restaurantRating") or 0) >= 3.5)
    return name, start, end, len(items), good, float(cost), field(run, "status", "status")


def field(obj, attr, key):
    """apify-client 버전에 따라 실행 결과가 객체(run.default_dataset_id) 또는 dict(run["defaultDatasetId"])"""
    if isinstance(obj, dict):
        return obj.get(key)
    value = getattr(obj, attr, None)
    return value.value if hasattr(value, "value") else value   # status가 enum일 때


def as_dict(item):
    if isinstance(item, dict):
        return item
    for method in ("model_dump", "dict"):
        if hasattr(item, method):
            return getattr(item, method)()
    return dict(item)


def main():
    from apify_client import ApifyClient
    token, out, areas_file = setup()
    client = ApifyClient(token)
    jobs = load_jobs(areas_file, out)
    todo = [j for j in jobs if not j[5].exists() and (not ONLY or j[0] == ONLY)]
    print(f"도구 버전: {BUILD or '최신'}" + (f", 지역: {ONLY}" if ONLY else ""))
    print(f"전체 {len(jobs)}개 실행 중 이미 받은 것 {len(jobs) - len(todo)}개, 이번에 {len(todo)}개 (예산 ${BUDGET_USD})")

    spent, total, total_good, done = 0.0, 0, 0, 0
    with ThreadPoolExecutor(max_workers=PARALLEL) as pool:
        pending = {}
        queue = list(todo)
        while queue or pending:
            # 예상 비용(실행당 약 $0.30)까지 넣어 예산을 넘지 않을 때만 새로 시작
            while queue and len(pending) < PARALLEL and spent + 0.31 * (len(pending) + 1) <= BUDGET_USD:
                job = queue.pop(0)
                pending[pool.submit(run_one, client, job)] = job
            if not pending:
                print(f"예산 ${BUDGET_USD}에 닿아 멈춤. 남은 실행 {len(queue)}개 (다시 실행하면 이어서 받아요)")
                break
            for fut in as_completed(list(pending)):
                job = pending.pop(fut)
                done += 1
                try:
                    name, s, e, n, good, cost, status = fut.result()
                    spent += cost
                    total += n
                    total_good += good
                    print(f"[{done}/{len(todo)}] {name} p{s}-{e}: {n}곳 (3.5 이상 {good}곳) ${cost:.3f} {status}  | 누적 ${spent:.2f}")
                except Exception as ex:   # 실패한 실행은 파일을 만들지 않으므로 다음에 다시 받는다
                    print(f"[{done}/{len(todo)}] {job[0]} p{job[1]}-{job[2]} 실패: {ex}")
                break
    print(f"합계 {total}곳 (3.5 이상 {total_good}곳), 이번 비용 ${spent:.2f}")


if __name__ == "__main__":
    main()
