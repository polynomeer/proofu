---
owner: 플랫폼과 온콜 책임자
status: 운영 기준 초안
review: 분기 복구 훈련 후
---

# 백업과 복구

목표는 [architecture/non-functional.md](../architecture/non-functional.md)의 **RPO 24시간 이하, RTO 4시간 이하**이고, 확인 방법은 **분기 복구 훈련**입니다. 이 문서는 그 훈련을 명령 두 개로 줄여 놓은 것입니다.

## 무엇이 백업되는가

지금은 **PostgreSQL 하나가 전부**입니다. 경력·Evidence·공고·문서뿐 아니라 렌더링된 내보내기 파일도 `export_files`에 bytea로 들어 있습니다([ADR-0009](../architecture/adr/0009-document-rendering-and-export-storage.md)). 그래서 데이터베이스 덤프 하나가 완전한 복구 지점입니다 — 객체 저장소가 정해지면 이 문장이 깨지므로, 그때 이 문서와 복구 검사를 함께 고칩니다.

백업 파일은 **모든 워크스페이스의 개인정보**입니다. 암호화와 보관 위치는 저장소 쪽 책임이고 클라우드와 함께 정합니다([open-decisions](../project/open-decisions.md)). `scripts/backup.sh`는 암호화하지 않습니다.

## 뜨기

```bash
scripts/backup.sh                 # 로컬 compose postgres → backups/
PGHOST=… PGUSER=… PGPASSWORD=… PGDATABASE=… scripts/backup.sh   # 다른 서버
```

세 개가 나옵니다: `*.dump`(pg_dump 커스텀 포맷), `*.sha256`, `*.manifest.json`. **매니페스트가 핵심**입니다 — 표별 정확한 행 수, 스키마가 올라가 있던 마이그레이션, 서버 버전이 들어 있어서 복구를 "pg_restore가 0으로 끝났다"보다 강하게 검사할 수 있습니다.

운영에서는 관리형 데이터베이스의 자체 백업(일일 전체 + WAL 연속 보관)이 1차입니다. 이 스크립트는 **검증·이관·일회성 덤프**용입니다.

## 되살리기

```bash
scripts/restore.sh backups/proofu-….dump          # 새 데이터베이스로 복구하고 검사
scripts/restore.sh --rehearse --boot              # 지금 백업 → 복구 → api 기동까지
scripts/restore.sh … --into proofu --force        # 운영 복구(라이브 DB 덮어쓰기, 확인 질문)
```

기본값은 **새 데이터베이스**(`proofu_restore_<시각>`)이고 끝나면 지웁니다. 라이브 데이터베이스로 되돌리는 것은 `--force` + 데이터베이스 이름 입력이 필요합니다.

검사하는 것:

| 검사                  | 왜                                                                                                   |
| --------------------- | ---------------------------------------------------------------------------------------------------- |
| sha256                | 파일이 뜰 때의 그 파일인지                                                                           |
| 표별 행 수            | 매니페스트와 전부 일치. 하나라도 다르면 실패                                                         |
| 스키마 버전           | 백업 시점과 같은 마이그레이션인지                                                                    |
| READY 내보내기의 파일 | `exports.object_key → export_files`는 **외래 키가 아니다**(ADR-0009). 다운로드 안 되는 문서를 찾는다 |
| 만료된 내보내기       | 만료된 산출물의 바이트가 실제로 지워졌는지 — 보존 약속의 반대 방향 검사                              |
| provenance 출처       | `source_type`에 따라 가리키는 표가 달라 외래 키를 걸 수 없다. 근거를 잃은 문장을 찾는다              |
| `--boot`              | 복구본 위에서 api가 뜨는지. Flyway 이력 검증 + Hibernate `ddl-auto=validate`가 스키마를 다시 본다    |

## 분기 복구 훈련

```bash
scripts/restore.sh --rehearse --boot
```

마지막 실행(2026-10-15, 로컬 compose, 35개 표 2,432행 / 0.6MB 덤프):

| 단계                    | 시간 |
| ----------------------- | ---- |
| 덤프                    | 2s   |
| pg_restore              | 1s   |
| api 기동(복구본 위에서) | 9s   |

**이 숫자는 RTO의 하한일 뿐입니다.** 실제 RTO에는 백업 파일을 가져오는 시간, 인스턴스 기동, DNS·시크릿 전환이 더해지고, 그건 클라우드가 정해져야 잴 수 있습니다. 운영 환경에서 훈련할 때마다 위 표를 갱신합니다.

## 아직 없는 것

- **암호화와 오프사이트 보관** — 저장소 결정에 달려 있습니다.
- **PITR(WAL 기반 시점 복구)** — 관리형 DB 기능으로 켤 항목입니다. 지금 구조에서 RPO는 "마지막 덤프 시각"입니다.
- **삭제와 백업의 관계** — 계정 삭제는 즉시 반영되지만 백업에는 최대 35일 남습니다([data/retention.md](../data/retention.md)). 사용자 고지 문구가 이미 그렇게 되어 있어야 합니다.
