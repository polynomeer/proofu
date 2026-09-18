# 0008 AI 제공자와 모델 정책: Anthropic Claude

- 상태: 승인
- 일자: 2026-09-18

## 맥락

요구사항 추출(F04), 매칭 설명(F05), 문서 생성(F06)은 모델 제공자가 정해져야 시작할 수 있다. 부록 B의 결정 기준은 **평가 점수 · 지역 · 보존 계약 · 비용**이다. 추가로 [ai/grounding-policy.md](../../ai/grounding-policy.md)가 요구하는 기술 요건이 있다: 스키마 강제 구조화 출력, 참조 식별자 화이트리스트 검증, 공고 원문의 **원문 구간(`source_span`)** 보존, 원문을 명령이 아닌 데이터로 격리, 반복 컨텍스트 캐싱, 고정 평가 세트 회귀.

확인된 전제 (2026-09-18):

- 한국 사용자를 중심으로 개발하되, 국외 추론은 허용된다.
- 클라우드 제공자는 미정이다.
- 제품 사용량 기준 추정 비용은 사용자당 월 $2 안팎(Opus 5, 공고 10건·문서 5건)으로, 비용은 결정 변수가 아니다.

## 선택지

| 선택지                                | 장점                                                                                                                                                       | 단점                                                        |
| ------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| **Anthropic Claude, 1st-party API**   | `strict` 구조화 출력, `citations`의 `char_location`이 `SourceSpan`에 직접 매핑, 프롬프트 캐싱·배치, `inference_geo`, API 데이터 학습 미사용, 공식 Java SDK | 단일 제공자 기능(인용) 의존                                 |
| Claude via Bedrock / Vertex / Foundry | 같은 SDK·같은 모델, 클라우드 청구 통합, 리전 선택                                                                                                          | 클라우드가 미정, 일부 기능(`inference_geo`, 서버 툴) 미지원 |
| OpenAI / Google 직접                  | 구조화 출력 있음                                                                                                                                           | 원문 문자 위치 인용을 직접 구현해야 함, 별도 계약 검토      |

## 결정

1. **제공자: Anthropic Claude, 1st-party Messages API.** 클라우드가 정해지고 리전·청구 통합이 필요해지면 같은 Java SDK의 백엔드만 Bedrock/Vertex/Foundry로 교체한다 (`AnthropicOkHttpClient.builder().backend(...)`).
2. **접점: AI Gateway 하나 (ADR-0005).** 모델 호출은 `apps/api`의 Gateway를 통해서만 하고, 실행은 `apps/worker`의 Job에서 한다. Gateway 책임: 컨텍스트 정책(민감도 필터), `strict` JSON Schema 검증, `AllowedSources` 화이트리스트 재검증(도메인 `GeneratedOutput.requireGrounded`), 예산·속도 제한, `ai_executions` 기록(원문 미저장).
3. **모델 정책 (초기값, 평가로 갱신):**

   | 작업               | 모델            | `effort` | 비고                                                    |
   | ------------------ | --------------- | -------- | ------------------------------------------------------- |
   | 공고 요구사항 추출 | `claude-opus-5` | `low`    | `citations` 사용, 결과는 `Requirement.extracted`(DRAFT) |
   | 매칭 설명          | `claude-opus-5` | `medium` | 점수는 결정적 코드(`MatchScore`), 모델은 이유 문장만    |
   | 문서 생성          | `claude-opus-5` | `high`   | `GeneratedOutput` 스키마, 블록별 `certainty`            |
   | 문장 개선          | `claude-opus-5` | `medium` | 사실 의미 변경 금지                                     |
   | 회고 가설 제안     | `claude-opus-5` | `low`    | 가설만, 원인 단정 금지                                  |

   단일 모델을 쓰는 이유: 캐시는 모델 단위라 모델을 섞으면 스냅샷 캐싱 이득을 잃고, 최신 모델의 낮은 `effort`가 이전 세대 상위 설정과 비슷하거나 낫다. **Sonnet 5 / Haiku 4.5로의 하향은 작업별로 §9.6 고정 평가 세트를 통과(사실성 중대 오류 0건, 근거 충실도 95% 이상)한 경우에만** 허용하고 ADR을 갱신한다.

4. **API 사용 규칙:** 사고(thinking)는 기본 adaptive 유지(끄지 않는다). 구조화 출력은 `output_config.format` + 도구 `strict: true`. 공고·Evidence 원문은 `document` 블록으로 넣고 시스템 지시와 분리한다. 스냅샷·시스템 프롬프트에 `cache_control`을 두어 추출→매칭→생성이 캐시를 공유한다. 평가 세트 실행은 배치 API(50% 할인). 프리필은 사용하지 않는다(400).
5. **지역과 보존 (초기값):** 국외 추론을 허용하므로 `inference_geo`는 설정하지 않는다. 데이터 보존은 기본(30일)으로 시작하고 ZDR은 신청하지 않는다. 개인정보 고지에는 "AI 처리 시 미국 등 국외 서버에서 추론"을 명시한다 ([security/privacy-requirements.md](../../security/privacy-requirements.md)). 한국 사용자 비중이 커지거나 법률 검토가 요구하면 `inference_geo` 또는 아시아 리전(Bedrock/Vertex)으로 전환한다.
6. **예산과 제한 (초기값):**

   | 항목                   | 값                               | 초과 시                                                                  |
   | ---------------------- | -------------------------------- | ------------------------------------------------------------------------ |
   | 워크스페이스 월 예산   | **$5**                           | 80%에서 경고 배너, 100%에서 새 AI 작업 거부(`RATE_LIMITED`, 사용자 안내) |
   | 워크스페이스 속도 제한 | AI 작업 30건/시간                | 429                                                                      |
   | 배포 전체 일일 예산    | `AI_DAILY_BUDGET_USD` (기본 $50) | 모든 AI 작업 일시 중지, 운영 알림                                        |
   | 호출당 입력 상한       | 50K 토큰                         | 컨텍스트 축소(오래된 Evidence부터 제외) 후 재시도, 안 되면 사용자 검토   |
   | 호출당 출력 상한       | 추출·매칭 8K, 생성 16K           | 스트리밍 사용                                                            |

   비용은 `ai_executions.cost_micros`에 호출마다 기록하고 대시보드·설정에서 워크스페이스에 보여준다.

## 결과

- 구현 시 정정(2026-09-18): `citations`는 구조화 출력(`output_config.format`)과 함께 쓸 수 없어(400) 사용하지 않는다. 대신 모델이 **원문 인용문(quote)** 을 반환하고 서버가 `RequirementExtractor.locate`로 `SourceSpan`을 계산·검증한다. 결정적이고 제공자 중립적이므로 ADR-0005의 제공자 중립은 그대로 유지된다.
- 구현 순서: Gateway 골격(정책·검증·예산·기록) → F04 추출 → F05 매칭 설명 → F06 생성. 각 단계는 [ai/evaluation.md](../../ai/evaluation.md)의 평가 세트(`packages/ai-evaluation`, 합성 데이터)를 함께 만든다.
- 환경 변수: `AI_PROVIDER=anthropic`, `ANTHROPIC_API_KEY`, `AI_MODEL_DEFAULT=claude-opus-5`, `AI_DAILY_BUDGET_USD`, `AI_WORKSPACE_MONTHLY_BUDGET_USD`.

## 재검토 조건

- 고정 평가 세트에서 하위 모델이 작업별 기준을 통과하면 해당 작업의 모델을 갱신한다.
- 클라우드 결정, 한국 리전 가용성 확인, 또는 법률 검토가 국내 추론을 요구하면 백엔드/지역을 바꾼다.
- 월 실제 비용이 예산 초기값과 2배 이상 어긋나면 예산·제한을 재조정한다.
- 다른 제공자가 동등한 문자 위치 인용을 제공하면 제공자 의존 격리를 재평가한다.
