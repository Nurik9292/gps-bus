# Replay-харнесс prediction (оффлайн-контур)

Измерительный стенд модели v3.1: файлы на входе — файлы/метрики на выходе.
Никаких Redis / Spring / Reactor / прод-пайплайна (закреплено `OfflineContourPurityTest`).

## Как прогнать одной командой

```bash
# весь стенд (формат, метрики, модели, сценарии 15/16 с базлайнами):
./mvnw test -Dtest='biz.ugur.busroutebackend.replay.**'

# только сценарии (печатают базлайны и хэши):
./mvnw test -Dtest=ReplayScenariosTest
```

## Экспорт геометрии из dev-БД (read-only, по требованию)

```bash
./mvnw test -Dtest=GeometryFixtureExporterTest \
  -Dgeometry.export=true -Dgeometry.pass=$DB_PASSWORD \
  -Dgeometry.routes=25,10,8
# → src/test/resources/fixtures/geometry/route-<N>-dir<D>.json
```

## Состав

| Компонент | Роль |
|---|---|
| `GpsFix` + `GpsFixJsonl` | формат фикса = JSONL рекордера (вкл. hdop/satellites/accuracy, wallClock); ридер источник-агностичен (рекордер / синтетика / будущие генераторы) |
| `GeometryFixture` (+exporter) | полилиния 4326 + cumDist[] + L по контракту s-слоя (s = fraction·L, кумулятивный Haversine); сверка с БД: ±0.4 м к `total_distance_*` |
| `PredictionModel` | порт: фикс+геометрия → Estimate(s, speed, mode, varianceS) |
| `models/GeometricSnapModel` | референс: ближайшая точка → s (без фильтра) |
| `models/HoldLastModel` | референс-negative: держит первую оценку |
| `metrics/PositionMetrics` | слой 1: |s−s_true|, шаги, телепорты, смены режима |
| `metrics/ConsistencyMetrics` | слой 2: NEES/NIS + χ²-интервал (покрыто тестами на распределениях) |
| `metrics/ArrivalDetector` | слой 3 (каркас): факт прибытия по П-2 (arr_zone=50 м, arr_speed=5 км/ч — конфиг) |
| `synth/SyntheticScenario` | Scenario-16 (стоянка→разгон 0→V→крейсер), Scenario-15 (крейсер + forward-биас снапа); шум σ, Tugdk-качество, seed, truth-файл |
| `ReplayHarness` | прогон: фиксы+истина+модель → метрики + SHA-256 вывода (детерминизм) |
| `InvariantAssertions` | INV-2/3/7/8 + bounded-error (ядро Scenario-15) |
| `scenarios/ReplayScenariosTest` | живые 15/16 на референсах (+ санити нулевого шума, детерминизм, негативный контроль); каркас 01–14 `@Disabled` |

## Параметры сценариев (выбраны, требуют подтверждения в ревью — в спеке §14 числовых нет)

V=12.5 м/с (45 км/ч), a=1.0 м/с² (≤ a_max §4.5), интервал фикса 7 с (замер 5–10 с),
b_bias=20 м (< D_snap 40–80 м), σ_pos=5 м, стоянка 60–120 с, длительность 600–900 с.
