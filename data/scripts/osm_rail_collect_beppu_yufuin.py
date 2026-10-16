"""
벳푸·유후인만 받는다 (10-16). osm_rail_collect.py 수집이 끝난 뒤에 실행한다.

    프로젝트 루트에서:  python data/scripts/osm_rail_collect_beppu_yufuin.py

지역 범위와 받는 종류는 osm_rail_collect.py의 REGIONS("beppu", "yufuin")와 같다.
받은 뒤 정리(--build)는 모든 지역을 다시 정리하므로 rail_report.txt에 벳푸·유후인이 함께 들어간다.
"""
import sys

import osm_rail_collect

if __name__ == "__main__":
    sys.argv = [sys.argv[0], "beppu", "yufuin"]
    osm_rail_collect.main()
