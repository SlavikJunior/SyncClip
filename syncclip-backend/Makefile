.PHONY: check check-integration test arch build run smoke

check: ## архитектурный граф + все тесты (интеграционные требуют TEST_DB_URL)
	./gradlew check

check-integration: ## тесты с обязательным PostgreSQL (OPS-05)
	@test -n "$$TEST_DB_URL" || (echo "TEST_DB_URL is required (см. README)"; exit 1)
	./gradlew check

arch:
	./gradlew checkModuleDependencies

test:
	./gradlew test

build:
	./gradlew :server:app:installDist

run: ## локальный запуск (требует DB_* и JWT_SECRET)
	./gradlew :server:app:run

smoke: ## E2E против запущенного сервера на 127.0.0.1:18080
	python3 scripts/e2e_smoke.py
