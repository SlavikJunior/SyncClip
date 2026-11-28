# Единая точка проверки репозитория-контейнера SyncClip.
#
# Два независимых Gradle-проекта:
#   syncclip-client/  — KMP-клиент (Android, iOS, Desktop)
#   syncclip-backend/ — Ktor-сервер (JVM)
#
#   make check          — клиент + сервер
#   make check-client   — компиляция и тесты клиента
#   make check-backend  — компиляция и тесты сервера (интеграционные — с TEST_DB_URL)
#   make check-graph    — граф зависимостей модулей клиента (INV-01…03, INV-24)
#   make check-arch     — Konsist: слои, соглашения, покрытие проверок
#   make check-visual   — Roborazzi: сравнение с baseline

CLIENT_DIR  := syncclip-client
BACKEND_DIR := syncclip-backend
GRADLE_CLIENT := ./$(CLIENT_DIR)/gradlew -p $(CLIENT_DIR)

.PHONY: check check-client check-backend check-graph check-arch check-visual record-visual ide-reset

check: check-client check-backend

check-client:
	$(GRADLE_CLIENT) check

check-backend:
	$(MAKE) -C $(BACKEND_DIR) check

check-graph:
	$(GRADLE_CLIENT) checkModuleDependencies

check-arch:
	$(GRADLE_CLIENT) :core:architecture-tests:test

check-visual:
	$(GRADLE_CLIENT) \
		:core:designsystem:verifyRoborazziJvm \
		:feature:history:impl:verifyRoborazziJvm \
		:feature:sync:impl:verifyRoborazziJvm \
		:feature:settings:impl:verifyRoborazziJvm \
		:feature:auth:impl:verifyRoborazziJvm \
		:app:common:verifyRoborazziJvm

record-visual:
	$(GRADLE_CLIENT) \
		:core:designsystem:recordRoborazziJvm \
		:feature:history:impl:recordRoborazziJvm \
		:feature:sync:impl:recordRoborazziJvm \
		:feature:settings:impl:recordRoborazziJvm \
		:feature:auth:impl:recordRoborazziJvm \
		:app:common:recordRoborazziJvm

# Полный сброс локального состояния IDE после изменения структуры репозитория.
# Запускать при ЗАКРЫТЫХ Android Studio и IntelliJ IDEA, затем открыть корень
# и выполнить Sync Project with Gradle Files.
#
# Сохраняется конфигурация (.idea/gradle.xml с привязкой двух Gradle-проектов,
# run-конфигурации, JDK); удаляется только модель проекта — её IDE пересоберёт.
ide-reset:
	@rm -f .idea/workspace.xml .idea/tasks.xml .idea/usage.statistics.xml .idea/modules.xml
	@rm -f .idea/*.iml
	@rm -rf .idea/caches .idea/modules .idea/shelf
	@rm -rf .gradle
	@for d in "$$HOME"/Library/Caches/Google/AndroidStudio*/projects/syncclip.* \
	          "$$HOME"/Library/Caches/JetBrains/*/projects/syncclip.*; do \
		if [ -d "$$d" ]; then rm -rf "$$d"; echo "Удалён кэш модели IDE: $$d"; fi; \
	done
	@echo "Локальное состояние IDE сброшено. Открой корень SyncClip и выполни Sync Project with Gradle Files."
