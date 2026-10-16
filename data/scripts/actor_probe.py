"""
Apify 도구(액터) 정보 확인용 (10-16). 입력 형식·설명서·가격을 data/extra/actor_probe_<id>.json 에 저장한다.
실행하지 않고 정보만 읽으므로 비용 0원.

    python data/scripts/actor_probe.py zQZYaBMyKWruR1piR
"""
import json
import os
import sys
from pathlib import Path

from apify_client import ApifyClient
from dotenv import load_dotenv

root = Path(__file__).resolve().parents[2]
load_dotenv(root / ".env")
client = ApifyClient(os.environ["APIFY_TOKEN"])
actor_id = sys.argv[1]


def plain(x):
    return x if isinstance(x, (dict, list)) else getattr(x, "__dict__", str(x))


actor = client.actor(actor_id).get() or {}
actor = plain(actor)
info = {"actor": actor}
try:   # 버전(빌드) 목록: 번호·만든 날짜 (예전 버전으로 돌릴 수 있는지 보려고, 10-16)
    builds = client.actor(actor_id).builds().list(desc=True, limit=20)
    items = builds.items if hasattr(builds, "items") else builds.get("items", [])
    info["builds"] = [str(plain(b)) for b in items]
except Exception as e:
    info["builds"] = f"목록 실패: {e}"
build_id = ((actor.get("taggedBuilds") or {}).get("latest") or {}).get("buildId")
if build_id:
    info["build"] = plain(client.build(build_id).get() or {})   # 입력 형식(actorDefinition.input 또는 inputSchema)·설명서 전부
out = root / "data" / "extra" / f"actor_probe_{actor_id}.json"
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(info, ensure_ascii=False, indent=1, default=str), encoding="utf-8")
print(f"{actor.get('username')}/{actor.get('name')} → {out}")
