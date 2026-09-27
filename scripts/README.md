# Scripts

개발 보조 스크립트. 각 스크립트는 상단 주석에 목적과 사용법을 적고, `--help`로 같은 내용을 보여 줍니다.
공통 함수(포트 예약, 도커 기동, 프로세스 트리 종료, 실패 리포트)는 `lib/common.sh`에 있습니다.

| 스크립트           | 용도                                                                                              |
| ------------------ | ------------------------------------------------------------------------------------------------- |
| `dev.sh`           | 로컬 스택 전체(Postgres·api·worker·mock IdP·웹 dev 서버)를 한 터미널에서. Ctrl-C면 띄운 것만 정리 |
| `stop.sh`          | `dev.sh`나 중단된 `e2e.sh`가 남긴 프로세스·컨테이너 정리                                          |
| `doctor.sh`        | 이 머신이 스택과 테스트를 돌릴 수 있는지 점검(읽기 전용)                                          |
| `verify.sh`        | 커밋 전 관문 전부(format·enum·pnpm check·gradlew check, 선택적으로 e2e)를 단계별 로그와 함께      |
| `db.sh`            | 로컬 Postgres 접속·덤프·초기화. 포트가 바뀌어도 컨테이너에서 실제 포트를 읽는다                   |
| `e2e.sh`           | Playwright 여정 테스트(실제 스택 + mock IdP + 가짜 AI)                                            |
| `check-enums.py`   | enum 값이 domain Kotlin ↔ `migrations/` CHECK ↔ `openapi.yaml` 세 곳에서 일치하는지 (CI)          |
| `contract-diff.sh` | 선언한 계약과 실행 중인 api의 오퍼레이션 목록 비교 (CI)                                           |
| `ai-live-check.sh` | 실제 모델 호출 스모크 (`.env`의 `ANTHROPIC_API_KEY` 사용)                                         |

## dev.sh

```bash
scripts/dev.sh                    # oidc + mock IdP, 로그인·로그아웃까지 동작
scripts/dev.sh --auth header      # 로그인 없이 시드 workspace
scripts/dev.sh --no-build --open  # 직전 빌드 재사용 + 브라우저 열기
scripts/dev.sh --keep             # Ctrl-C 후에도 계속 띄워 둠 (정리는 scripts/stop.sh)
```

- **도커가 꺼져 있으면 직접 켠다**: Docker Desktop CLI(`docker desktop start`) → 30초 안에 안 올라오면
  `docker desktop restart`(창은 떠 있는데 엔진만 죽은 상태) → OrbStack/colima 순으로 시도하고,
  `DOCKER_START_CMD`로 덮어쓸 수 있습니다. macOS에서 엔진이 죽어 있으면 `docker info`가 영원히 멈추므로
  모든 확인에 제한 시간을 둡니다.
- **포트는 비켜 간다**: 5432·8080·8090·3000·8181이 사용 중이면 그 위 첫 빈 포트로 올리고, 누가 쓰고 있는지
  같이 알려 줍니다. Postgres 호스트 포트는 compose의 `POSTGRES_PORT`로 넘어갑니다.
- **정리**: Ctrl-C(또는 `SIGTERM`)면 프로세스 트리를 전부 종료하고, **이 스크립트가 띄운 경우에만** 컨테이너를
  멈춥니다. 이미 떠 있던 Postgres는 건드리지 않습니다.
- **로그**: 프로세스별 `logs/dev/<이름>.log`, 실패하면 설정·로그 꼬리·에러 줄을 모은
  `logs/dev/failure-<시각>.log`를 쓰고 경로를 출력합니다. `logs/`는 git에 올리지 않습니다.
