# Grafana dashboards (source of truth)

JSON-дашборды этого BC. После изменения:

```powershell
# из platform/
.\scripts\sync-grafana-dashboards.ps1
# или ./deploy/observability/up.sh (sync + restart)
```

Grafana подхватывает обновления каждые **30 секунд** (file provisioning).

Перегенерация стандартного дашборда:

```bash
python platform/scripts/generate-grafana-dashboard.py <repo-name>
# iam-service не перезаписывается без --force
```

Prometheus `job` для этого BC: см. `platform/deploy/observability/prometheus/scrape-*.yml`.
