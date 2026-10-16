.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-10s\033[0m %s\n", $$1, $$2}'

.PHONY: hooks
hooks: ## Install the pre-commit gate (git core.hooksPath=hooks); run once after cloning
	chmod +x hooks/pre-commit
	git config core.hooksPath hooks
	@echo "pre-commit hook installed (core.hooksPath=hooks)"

.PHONY: build
build: ## The Gradle gate: compile, Spotless, unit + integration tests, 100% coverage, jar
	./gradlew build

.PHONY: test
test: ## Unit tests only (pure JVM, fast)
	./gradlew test

.PHONY: server-test
server-test: ## Boot real Paper 26.3 servers in Docker with each store, and observe the protections and the build gate over RCON (minutes; needs Docker)
	./gradlew serverTest

.PHONY: test-scripts
test-scripts: ## The release scripts' tests, held to 100% line and branch coverage (needs coverage 7.6.10)
	python3 -m coverage run --branch --include='scripts/*.py' -m unittest discover -s scripts/tests
	python3 -m coverage report --fail-under=100 -m

.PHONY: format
format: node_modules/.bin/prettier ## Apply Spotless to Java and prettier to YAML, JSON and Markdown
	./gradlew spotlessApply
	node_modules/.bin/prettier --write '**/*.{yml,yaml,json,md}'

.PHONY: lint
lint: node_modules/.bin/prettier ## The non-Gradle checks over the whole tree: prettier, shellcheck, actionlint, gitleaks, the changelog format
	node_modules/.bin/prettier --check '**/*.{yml,yaml,json,md}'
	shellcheck hooks/pre-commit
	actionlint
	python3 scripts/changelog.py check
	gitleaks detect --no-banner --redact

node_modules/.bin/prettier: package.json package-lock.json
	npm ci
	@touch node_modules/.bin/prettier
