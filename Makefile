# Единая точка проверки Android/KMP-проекта.
#
# Состав — из docs/PROFILE.md (раздел «Проверка»), здесь только команды.
# Шаблон: ~/dev/arch-standard/templates/Makefile
#
#   make check          — всё сразу
#   make check-gradle   — компиляция и тесты
#   make check-graph    — граф зависимостей модулей (INV-01…03, INV-24)
#   make check-arch     — Konsist: слои, соглашения, покрытие проверок
#   make check-visual   — Roborazzi: сравнение с baseline

.PHONY: check check-gradle check-graph check-arch check-visual record-visual

check: check-gradle

check-gradle:
	./gradlew check

check-graph:
	./gradlew checkModuleDependencies

check-arch:
	./gradlew :core:architecture-tests:test

check-visual:
	./gradlew \
		:core:designsystem:verifyRoborazziJvm \
		:feature:history:impl:verifyRoborazziJvm \
		:feature:sync:impl:verifyRoborazziJvm \
		:feature:settings:impl:verifyRoborazziJvm

record-visual:
	./gradlew \
		:core:designsystem:recordRoborazziJvm \
		:feature:history:impl:recordRoborazziJvm \
		:feature:sync:impl:recordRoborazziJvm \
		:feature:settings:impl:recordRoborazziJvm
