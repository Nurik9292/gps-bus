.PHONY: help dev-up dev-down dev-logs dev-clean test-up test-down prod-build run

# Load .env file if it exists
ifneq (,$(wildcard ./.env))
    include .env
    export
endif

help: ## Show this help message
	@echo 'Usage: make [TARGET] [EXTRA_ARGUMENTS]'
	@echo 'Targets:'
	@awk 'BEGIN {FS = ":.*?## "} { \
		if (/^[a-zA-Z_-]+:.*?##.*$$/) printf "  \033[36m%-20s\033[0m %s\n", $$1, $$2 \
	}' $(MAKEFILE_LIST)

dev-up:
	docker-compose -f docker-compose.yml -f docker-compose.override.yml up -d
	@echo "Development environment started!"
	@echo "PostgreSQL: localhost:5432"
	@echo "Redis: localhost:6379"
	@echo "pgAdmin: http://localhost:5050 (admin@busroute.tm/admin123)"
	@echo "Redis Commander: http://localhost:8081 (admin/admin123)"
	@echo "WireMock: http://localhost:8082"
	@echo "Prometheus: http://localhost:9090"
	@echo "Grafana: http://localhost:3000 (admin/admin123)"

dev-down:
	docker-compose -f docker-compose.yml -f docker-compose.override.yml down

dev-logs:
	docker-compose -f docker-compose.yml -f docker-compose.override.yml logs -f

dev-clean:
	docker-compose -f docker-compose.yml -f docker-compose.override.yml down -v
	docker volume prune -f

test-up:
	docker-compose -f docker-compose.test.yml up -d

test-down:
	docker-compose -f docker-compose.test.yml down -v

db-migrate:
	docker-compose exec postgres psql -U bus_route_user -d bus_route_db -c "SELECT version();"
	mvn flyway:migrate

db-reset:
	mvn flyway:clean flyway:migrate

prod-build:
	docker build -t bus-route-backend:latest -f Dockerfile.prod .

docker-dev-build:
	./scripts/docker-dev.sh build

docker-dev-run:
	./scripts/docker-dev.sh run

docker-dev-stop:
	./scripts/docker-dev.sh stop

docker-dev-logs:
	./scripts/docker-dev.sh logs

docker-dev-shell:
	./scripts/docker-dev.sh shell

docker-dev-clean:
	./scripts/docker-dev.sh clean

docker-dev-rebuild:
	./scripts/docker-dev.sh rebuild

docker-prod-build:
	./scripts/docker-prod.sh build

docker-prod-run:
	./scripts/docker-prod.sh run

docker-prod-push:
	./scripts/docker-prod.sh push

docker-prod-deploy:
	./scripts/docker-prod.sh deploy

osrm-setup: ## Download and preprocess Turkmenistan OSM data for OSRM foot routing
	@mkdir -p docker/osrm/data
	@echo "Downloading Turkmenistan OSM data (~150MB)..."
	docker run --rm -v $(PWD)/docker/osrm/data:/data osrm/osrm-backend:latest \
		sh -c "curl -L --progress-bar -o /data/turkmenistan.osm.pbf \
		https://download.geofabrik.de/asia/turkmenistan-latest.osm.pbf && \
		echo 'Extracting...' && osrm-extract -p /opt/foot.lua /data/turkmenistan.osm.pbf && \
		echo 'Partitioning...' && osrm-partition /data/turkmenistan.osrm && \
		echo 'Customizing...' && osrm-customize /data/turkmenistan.osrm && \
		echo 'OSRM data ready!'"

run: ## Run application with .env variables loaded
	./mvnw spring-boot:run
