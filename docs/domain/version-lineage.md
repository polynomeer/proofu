---
owner: 도메인 책임자
status: 구현 기준
review: 도메인 변경 시
---

# 버전과 계보

- 원천 데이터 변경은 `revision`을 증가시키며 삭제는 기본적으로 soft delete 후 보존 정책에 따라 제거합니다.
- Document Version은 부모 버전, 선택된 원천 revision, 공고 snapshot, 프롬프트 템플릿 버전, 모델 식별자를 참조합니다.
- 사용자 편집과 AI 생성 변경을 구분해 기록합니다 (`created_by`: `USER` | `AI`).
- Submission Snapshot은 생성 후 수정하지 않으며 정정이 필요하면 새 스냅샷을 만듭니다.
- 비교는 문서 전체뿐 아니라 섹션 및 문장 단위 변경 유형을 표시합니다.

## Provenance

`provenance_links`는 (문서 버전, 블록) → (원천 유형, 원천 ID, 원천 revision, 관계)를 고정합니다.

- 원천 revision은 생성 후 변경되지 않습니다.
- 모델이 반환한 식별자는 서버가 입력 집합에 대해 화이트리스트 검증합니다.
- 블록 `certainty`: `SUPPORTED` | `INFERRED` | `UNSUPPORTED`. `INFERRED`와 `UNSUPPORTED`는 사용자 승인 없이 최종 내보내기에 포함되지 않습니다.

## 내보내기 상태

```
REQUESTED -> RENDERING -> VALIDATING -> READY | FAILED | EXPIRED
```

동일 문서 버전과 템플릿 조합의 재시도는 가능하면 기존 성공 산출물을 재사용합니다.
