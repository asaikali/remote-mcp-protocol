# Observability Services Docker Configuration

This directory contains Docker configuration for the complete observability stack used in our Docker Compose setup.

## Services Provided

**Grafana Dashboard** - Web-based observability UI for visualizing metrics, logs, and traces
- **Port**: 3000 (HTTP)
- **URL**: http://localhost:3000
- **Authentication**: Anonymous access enabled (admin privileges)

**Prometheus Metrics** - Collects and stores application metrics for monitoring and alerting
- **Port**: 9090 (HTTP)
- **URL**: http://localhost:9090
- **API**: http://localhost:9090/api/v1

**Loki Logs** - Log aggregation system for collecting and querying application logs
- **Port**: 3100 (HTTP) 
- **API**: http://localhost:3100
- **Metrics**: http://localhost:3100/metrics

**Tempo Tracing** - Distributed tracing backend for following requests across services
- **Port**: 3200 (OTLP HTTP), 9411 (Zipkin HTTP)
- **OTLP**: http://localhost:3200
- **Zipkin**: http://localhost:9411

**OpenTelemetry Collector** - Receives, processes, and exports observability data
- **Port**: 4317 (OTLP gRPC), 4318 (OTLP HTTP)
- **gRPC**: grpc://localhost:4317
- **HTTP**: http://localhost:4318

## Architecture Overview

This observability stack provides two integration patterns for Spring Boot applications:

### Pattern 1: Direct Integration (Spring Boot → Backend Services)

```text
+-----------------------------------------------------------+
|                  Spring Boot App                          |
|  Micrometer prometheus metrics registry                   |
|  Micrometer zipkin brave tracing bridge                   |
|  Loki logback appender                                    |
+-----------------------------------------------------------+
         |                   |                     ↑
POST :9411/api/v2/spans       |                     |
         |                   |          GET /actuator/prometheus
         |        POST /loki/api/v1/push           |
         ↓                   ↓                     |
+--------------------+ +--------------------+ +--------------------+
|       Tempo        | |        Loki        | |    Prometheus      |
|  OTLP   3200 HTTP  | |      3100 HTTP     | |    9090 HTTP       |
|  Zipkin 9411 HTTP  | |                    | |                    |
+--------------------+ +--------------------+ +--------------------+
         ↑                   ↑                     ↑
         |                   |                     |
         |                   |                     |
     +------------------------------------------------------------+
     |                         Grafana                            |
     |                      (3000 Web UI)                         |
     |  - Queries metrics from Prometheus                         |
     |  - Queries logs from Loki                                  |
     |  - Queries traces from Tempo                               |
     +------------------------------------------------------------+
```

### Pattern 2: OpenTelemetry Collector Integration

```text
+-----------------------------------------------------------+
|                  Spring Boot App                          |
|  Micrometer otel metrics registry                         |
|  Micrometer otel tracing bridge                           |
|  OpenTelemetry logback appender                           |
+-----------------------------------------------------------+
         |                   |                     |
POST :4318/v1/traces          |           POST :4318/v1/metrics
         |                   |                     |
         |         POST :4318/v1/logs              |
         ↓                   ↓                     ↓
     +----------------------------------------------------+
     |            OpenTelemetry Collector                 |
     |   OTLP HTTP :4318           OTLP gRPC :4317        |
     +----------------------------------------------------+
         |                   |                     ↑
   POST :3200/v1/traces      |          GET /metrics
         |                   |                     |
         |         POST /loki/api/v1/push          |
         ↓                   ↓                     |
+--------------------+ +--------------------+ +--------------------+
|       Tempo        | |        Loki        | |    Prometheus      |
|      3200 HTTP     | |      3100 HTTP     | |    9090 HTTP       |
+--------------------+ +--------------------+ +--------------------+
         ↑                   ↑                     ↑
         |                   |                     |
         |                   |                     |
     +------------------------------------------------------------+
     |                         Grafana                            |
     |                      (3000 Web UI)                         |
     |  - Queries metrics from Prometheus                         |
     |  - Queries logs from Loki                                  |
     |  - Queries traces from Tempo                               |
     +------------------------------------------------------------+
```

## Docker Configuration

### Configuration Files

**OpenTelemetry Collector** (`config/otel-collector.yaml`):
- Receives OTLP data on ports 4317 (gRPC) and 4318 (HTTP)
- Forwards traces to Tempo via OTLP
- Forwards logs to Loki 
- Exposes metrics for Prometheus scraping

**Tempo** (`config/tempo.yaml`):
- Accepts OTLP traces on port 3200
- Supports Zipkin format on port 9411
- Stores traces for Grafana visualization

**Prometheus** (`config/prometheus.yaml`):
- Scrapes metrics from applications and collector
- Configured with exemplar support for trace correlation
- Includes host.docker.internal for local app monitoring

### Grafana Setup

**Pre-configured Datasources** (`grafana/provisioning/datasources/`):
- **Prometheus**: http://prometheus:9090 (metrics)
- **Loki**: http://loki:3100 (logs)  
- **Tempo**: http://tempo:3200 (traces)

**Pre-loaded Dashboards** (`grafana/provisioning/dashboards/`):
- JVM Micrometer metrics dashboard
- Spring Boot application monitoring
- Microservices observability dashboard
- Prometheus statistics dashboard
- Spring Boot HikariCP connection pool monitoring

**Authentication**: Anonymous access enabled with admin privileges for local development.

## Spring Boot Integration

### Pattern 1: Direct Backend Integration

**Dependencies**:
```xml
<dependencies>
  <!-- Actuator for /actuator/prometheus endpoint -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
  </dependency>
  
  <!-- Prometheus metrics -->
  <dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
  </dependency>
  
  <!-- Zipkin tracing -->
  <dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
  </dependency>
  <dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
  </dependency>
  
  <!-- Loki logging -->
  <dependency>
    <groupId>com.github.loki4j</groupId>
    <artifactId>loki-logback-appender</artifactId>
    <version>1.6.0</version>
  </dependency>
</dependencies>
```

### Pattern 2: OpenTelemetry Collector Integration

**Dependencies**:
```xml
<dependencies>
  <!-- Actuator for observability endpoints -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
  </dependency>
  
  <!-- OTLP metrics -->
  <dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-otlp</artifactId>
  </dependency>
  
  <!-- OTLP tracing -->
  <dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
  </dependency>
  
  <!-- OTLP logging -->
  <dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-logback-appender</artifactId>
    <version>1.32.0</version>
  </dependency>
</dependencies>
```

## Usage

**Start Services**: `compose up` (from parent directory)

**Access UIs**:
- **Grafana**: http://localhost:3000 (main dashboard)
- **Prometheus**: http://localhost:9090 (metrics explorer)

**Application Configuration**: Point your Spring Boot app to the appropriate endpoints:
- **Prometheus scraping**: Expose `/actuator/prometheus`
- **Zipkin traces**: Send to http://localhost:9411
- **OTLP data**: Send to http://localhost:4318 (HTTP) or localhost:4317 (gRPC)
- **Loki logs**: Send to http://localhost:3100/loki/api/v1/push

## Customization

**Port Configuration**: All ports are configurable via environment variables:
- `GRAFANA_PORT` - Grafana web UI (default: 3000)
- `PROMETHEUS_PORT` - Prometheus web UI (default: 9090)
- `LOKI_PORT` - Loki API (default: 3100)
- `TEMPO_PORT` - Tempo OTLP (default: 3200)
- `TEMPO_ZIPKIN_PORT` - Tempo Zipkin compatibility (default: 9411)
- `OTEL_COLLECTOR_HTTP_PORT` - Collector OTLP HTTP (default: 4318)
- `OTEL_COLLECTOR_GRPC_PORT` - Collector OTLP gRPC (default: 4317)

**Adding Dashboards**: Place JSON dashboard files in `grafana/provisioning/dashboards/` and they will be automatically loaded.

**Modifying Datasources**: Edit `grafana/provisioning/datasources/datasource.yml` to adjust connection settings.

**Custom Prometheus Targets**: Edit `config/prometheus.yaml` to add scrape targets for your applications.