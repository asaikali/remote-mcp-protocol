# Docker Compose Wrapper - Implementation Notes

*Technical documentation for AI coding assistants, system maintainers, and developers who need to understand or modify the Docker Compose wrapper system.*

---

## System Overview

This is a **convention-based Docker Compose wrapper system** designed for universal applicability across any project. The system prioritizes simplicity and developer experience through carefully designed constraints and conventions.

### Design Philosophy

**Convention Over Configuration**: The system works through established patterns rather than complex configuration files or options.

**Two-Persona Design**:
- **Persona 1 (Script Users)**: 95% of developers who just want to run `compose up` and get coding
- **Persona 2 (Maintainers)**: 5% of developers who design conventions and maintain the system

**Universal Portability**: The entire `docker/` directory can be copied to any project and work immediately.

---

## Architecture & Core Components

### File Structure

```
docker/
├── compose                 # Universal wrapper script (bash, executable)
├── compose.yaml           # Single compose file with all services
├── .env                   # Team defaults (optional, committed)
├── .env.local            # Personal overrides (optional, gitignored)
├── compose-design.md     # Design constraints specification
├── implementation-notes.md # This file - technical documentation
└── [service-directories]/ # Service-specific configurations
```

### Script Location Detection

The compose script uses **script location detection** to work from any directory:

```bash
get_script_dir() {
    local script_path="${BASH_SOURCE[0]}"
    # Resolve symlinks to get actual script location
    while [[ -L "$script_path" ]]; do
        script_path=$(readlink "$script_path")
    done
    dirname "$script_path"
}
```

All file operations (compose.yaml lookup, .env loading) are relative to the script location, not the current working directory.

### Environment Loading System

**Loading Order (highest to lowest priority)**:
1. Shell environment variables (`export VAR=value`)
2. `.env.local` file (personal overrides, gitignored)
3. `.env` file (team defaults, committed)
4. `compose.yaml` defaults (`${VAR:-default}` syntax)

**Implementation**:
```bash
load_env() {
    local script_dir
    script_dir=$(get_script_dir)
    
    set -a  # Auto-export variables
    [[ -f "$script_dir/.env" ]] && source "$script_dir/.env"
    [[ -f "$script_dir/.env.local" ]] && source "$script_dir/.env.local"
    set +a  # Stop auto-export
}
```

### Docker Compose Wrapper Function

All Docker Compose operations go through a wrapper that ensures correct directory context:

```bash
docker_compose() {
    local script_dir
    script_dir=$(get_script_dir)
    # Run docker compose from script directory
    (cd "$script_dir" && command docker compose "$@")
}
```

---

## Core Design Constraints

### Constraint 1: File Structure & Dependencies

**Required Files**:
- Single `compose` script in `docker/` directory
- Single `compose.yaml` file (exact filename) in `docker/` directory
- Dependencies: `docker`, `yq`, `jq` in PATH

**Validation**:
```bash
check_compose_file() {
    local script_dir
    script_dir=$(get_script_dir)
    local compose_file="$script_dir/compose.yaml"
    
    if [[ ! -f "$compose_file" ]]; then
        log error "compose.yaml not found next to compose script"
        exit 1
    fi
}
```

### Constraint 2: Service Management Approach

**All Services Together**: No selective service management in the wrapper
- `compose up` starts ALL services
- Use `docker compose up <service>` directly for selective startup
- Eliminates decision fatigue about "which services do I need?"

### Constraint 3: Labels System for Service Information

**Generic Script Design**: The script is completely service-agnostic. All service-specific information comes through Docker Compose labels.

**Label Namespace**: `info.*` prefix for all wrapper-related labels

**Label Structure**:
```yaml
labels:
  - "info.group=Service Category"           # Groups services in output
  - "info.title=Human Readable Name"        # REQUIRED - service display name
  - "info.url.<type>=connection-url"        # URLs by type (ui, api, jdbc, etc.)
  - "info.cred.<type>=credential-value"     # Credentials by type
```

**Label Processing**: Uses `docker compose config --format json` + `jq` to extract and process labels:

```bash
# Extract service info from labels
services_json=$(docker_compose config --format json | jq '.services
                | to_entries
                | map({
                    service: .key,
                    info: ((.value.labels // {})
                          | to_entries
                          | map(select(.key|startswith("info.")))
                          | map({path: (.key | split(".")[1:]), value: .value})
                          | reduce .[] as $i ({}; setpath($i.path; $i.value))
                         )
                  })')
```

### Constraint 4: Environment Variable Conventions

**Naming Patterns** (enforced by convention, not script):
- **Ports**: `<SERVICE>_PORT` (single), `<SERVICE>_PORT_<TYPE>` (multiple)
- **Images**: `<SERVICE>_IMAGE`
- **Credentials**: `<SERVICE>_CRED_<TYPE>`

**Usage in compose.yaml**:
```yaml
services:
  postgres:
    image: ${POSTGRES_IMAGE:-postgres:17}
    ports:
      - "${PG_PORT:-15432}:5432"
    environment:
      POSTGRES_USER: ${POSTGRES_CRED_USERNAME:-postgres}
```

### Constraint 5: Universal Access & Script Execution

**Direct Execution Only**:
- Script must be executed directly, not through symbolic links
- Universal access achieved via relative paths or PATH modification (direnv, shell aliases)
- Script location detected using `dirname "${BASH_SOURCE[0]}"`

**Supported Access Patterns**:
- Direct paths: `docker/compose up`, `../docker/compose up`
- PATH modification: `export PATH="$PATH:$(pwd)/docker"` or direnv with `PATH_add docker`
- Shell aliases: `alias compose='docker/compose'`

**Not Supported**:
- Symbolic links: `ln -s project/docker/compose ~/.local/bin/compose`
- Complex link chains or relative symlinks

### Constraint 6: Error Handling Philosophy

**Fail Fast on Convention Violations**:
- Missing `compose.yaml`
- Missing required dependencies (`docker`, `yq`, `jq`)

**Let Docker Compose Handle Everything Else**:
- Service validation
- Network issues  
- Image pull failures
- YAML syntax errors

**Simple Troubleshooting Only**:
- Port conflict detection after `docker compose up` failures
- Helpful error messages with actionable next steps

---

## Commands Implementation

### Standard Docker Compose Commands

**Pass-through commands**: `up`, `down`, `ps`, `logs`, `build`

These mirror Docker Compose behavior with enhanced output formatting:

```bash
case "$cmd" in
    up|down|ps|logs|build)
        show_operation_header "$(tr '[:lower:]' '[:upper:]' <<< "${cmd:0:1}")${cmd:1}"
        
        if [[ "$cmd" == "up" ]]; then
            # Handle port conflicts on failure
            if ! docker_compose "up" -d "$@"; then
                log error "Failed to start services - likely port conflicts"
                exit 1
            fi
            log success "Services started successfully"
        else
            docker_compose "$cmd" "$@"
        fi
        ;;
esac
```

### Custom Commands

#### `clean` Command
Stops services and removes volumes:
```bash
clean)
    show_operation_header "Cleaning"
    docker_compose "down" -v --remove-orphans
    log success "Cleanup completed"
    ;;
```

#### `info` Command
Displays grouped service information using labels:

**Process**:
1. Extract all services with `info.*` labels from compose configuration
2. Group services by `info.group` label
3. Display each group with services that have `info.title` labels
4. Show URLs and credentials for each service

**Implementation highlights**:
```bash
# Group services by info.group
groups_json=$(echo "$services_json" | jq 'group_by(.info.group // "Other") 
                                         | map({group: (.[0].info.group // "Other"), services: .})')

# Process each group
echo "$groups_json" | jq -r '.[] | "\(.group)\t\(.services | @json)"' | while IFS=$'\t' read -r group services_in_group; do
    # Show group header in red
    printf "%b== %s%b\n\n" "$RED" "$group" "$NC"
    
    # Show services with info.title
    echo "$services_in_group" | jq -r '.[] | select(.info.title) | "\(.service)\t\(.info | @json)"' | while IFS=$'\t' read -r service info_json; do
        process_service_info_json "$service" "$info_json"
    done
done
```

---

## AI Coding Assistant Guidelines

### When Adding New Services

**ALWAYS include both service definition AND required labels**:

```yaml
services:
  new-service:
    image: ${NEW_SERVICE_IMAGE:-default:latest}
    ports:
      - "${NEW_SERVICE_PORT:-8080}:80"
    environment:
      USERNAME: ${NEW_SERVICE_CRED_USERNAME:-admin}
    labels:
      - "info.group=Service Category"                    # For grouping in output
      - "info.title=Human Readable Service Name"         # REQUIRED for visibility
      - "info.url.ui=http://localhost:${NEW_SERVICE_PORT:-8080}"
      - "info.cred.username=${NEW_SERVICE_CRED_USERNAME:-admin}"
```

### Critical Rules for AI Agents

1. **Never modify the compose script logic** unless explicitly asked to change system behavior
2. **Always preserve universal access** - test that commands work from any directory  
3. **Maintain service-agnostic design** - no hardcoded service names in the script
4. **Follow environment variable naming conventions** consistently
5. **Include info.title label** for any service that should appear in `compose info`
6. **Use `${VAR:-default}` syntax** for all configurable values in compose.yaml
7. **Let Docker Compose handle validation** - don't add complex error handling to the script

### Common Tasks

**Adding environment defaults**:
```bash
# Add to docker/.env (committed to git)
NEW_SERVICE_PORT=8080
NEW_SERVICE_IMAGE=nginx:alpine
```

**Adding personal overrides**:
```bash
# Add to docker/.env.local (gitignored)
NEW_SERVICE_PORT=9080  # Avoid conflicts
```

**Testing universal access**:
```bash
# Test from different directories
cd /any/directory/in/repo
compose up
compose info
compose down
```

### Service Organization Best Practices

**Directory Structure**:
- Group service configurations by logical function under `docker/`
- Use relative paths in compose.yaml: `./service-type/config.yml`
- Keep related files together in service directories

**Example Structure**:
```
docker/
├── compose
├── compose.yaml
├── postgres/
│   ├── init.sql
│   └── pgadmin_servers.json
├── observability/
│   ├── config/
│   │   ├── prometheus.yaml
│   │   └── grafana.ini
│   └── dashboards/
└── api-services/
    ├── service-a.env
    └── service-b.conf
```

---

## Troubleshooting & Common Issues

### Port Conflicts

**Detection**: The script detects `docker compose up` failures and suggests port conflicts as a likely cause.

**Resolution**: Users modify `.env.local` or use shell environment overrides:
```bash
# Temporary override
PG_PORT=5432 compose up

# Permanent personal override  
echo "PG_PORT=5432" >> docker/.env.local
```

### Convention Violations

**Missing compose.yaml**: Script fails immediately with clear error message
**Missing dependencies**: Script checks for `docker`, `yq`, `jq` and fails with installation instructions

### Universal Access Issues  

**Script execution**: The script must be executed directly, not through symbolic links
**Path independence**: All operations are relative to script location, not current working directory
**Direnv integration**: Works perfectly because direnv adds the docker/ directory to PATH without using symlinks

**If universal access isn't working**:
- Use `which compose` to verify the script location
- Ensure you're not using symbolic links
- Consider direnv with `PATH_add docker` or shell aliases instead

---

## Extension Points

### Adding New Custom Commands

Add cases to the main command switch:

```bash
case "$cmd" in
    # ... existing commands ...
    
    new-command)
        show_operation_header "New Command"
        # Custom command logic here
        docker_compose "some-operation" "$@"
        log success "New command completed"
        ;;
esac
```

### Modifying Label Processing

The `info` command can be extended to support new label types by modifying the `process_service_info_json` function.

### Environment Loading Extensions

The environment loading system can be extended to support additional file types or loading strategies by modifying the `load_env` function.

---

## System Maintenance

### When Updating the System

1. **Test universal access** from multiple directories
2. **Verify label processing** with `compose info`  
3. **Check environment loading** with different `.env` configurations
4. **Validate error handling** for common failure cases
5. **Update documentation** in both README.md and this file

### Monitoring System Health

The system is designed to be self-validating:
- Script checks for required files and dependencies on every run
- Docker Compose handles service validation
- Label system provides service connectivity validation through `compose info`

---

## Spring Boot Integration

This Docker Compose wrapper system is designed to work seamlessly with Spring Boot's Docker Compose support (`spring-boot-docker-compose` module).

### Spring Boot Docker Compose Module

When Spring Boot applications include the `spring-boot-docker-compose` dependency, they automatically:
1. **Search for compose.yaml** in the working directory (our system provides this)
2. **Auto-start containers** with `docker compose up` when the application starts
3. **Create service connection beans** for supported containers
4. **Auto-stop containers** with `docker compose stop` when the application shuts down

### Integration Benefits

**Automatic Service Discovery**: Spring Boot automatically discovers and connects to services based on container image names:

| Container Image | Spring Boot Auto-Configuration |
|---|---|
| `postgres` or `bitnami/postgresql` | `JdbcConnectionDetails`, `R2dbcConnectionDetails` |
| `redis`, `bitnami/redis` | `RedisConnectionDetails` |
| `rabbitmq`, `bitnami/rabbitmq` | `RabbitConnectionDetails` |
| `mongo`, `bitnami/mongodb` | `MongoConnectionDetails` |
| `otel/opentelemetry-collector-contrib` | `OtlpMetricsConnectionDetails`, `OtlpTracingConnectionDetails` |

### Spring Boot Specific Labels

In addition to our `info.*` labels, services can use Spring Boot-specific labels:

#### Service Connection Override
```yaml
services:
  my-custom-postgres:
    image: my-company/custom-postgres:latest
    labels:
      - "org.springframework.boot.service-connection=postgres"  # Tell Spring Boot this is PostgreSQL
      - "info.group=Database Services"                          # Our wrapper system
      - "info.title=Custom PostgreSQL"                          # Our wrapper system
```

#### Skip Service Connection
```yaml
services:
  utility-service:
    image: nginx:alpine
    labels:
      - "org.springframework.boot.ignore=true"          # Spring Boot ignores this service
      - "info.group=Utility Services"                   # But our wrapper still shows it
      - "info.title=Nginx Proxy"
```

#### SSL Configuration
```yaml
services:
  secure-redis:
    image: redis:latest
    labels:
      - "org.springframework.boot.sslbundle.pem.truststore.certificate=ca.crt"
      - "info.group=Cache Services"
      - "info.title=Secure Redis"
```

#### Disable Readiness Checks
```yaml
services:
  background-job:
    image: my-app/background-processor:latest
    labels:
      - "org.springframework.boot.readiness-check.tcp.disable=true"
      - "info.group=Background Services"
      - "info.title=Job Processor"
```

### Configuration Options

Spring Boot applications can configure Docker Compose behavior in `application.yaml`:

```yaml
spring:
  docker:
    compose:
      # Use our compose.yaml file (relative to application working directory)
      file: "docker/compose.yaml"
      
      # Control lifecycle management
      lifecycle-management: start-and-stop  # or 'none', 'start-only'
      
      # Use docker compose start instead of up
      start:
        command: start
      
      # Use docker compose down instead of stop  
      stop:
        command: down
        timeout: 1m
        
      # Configure readiness checks
      readiness:
        timeout: 2m
        tcp:
          connect-timeout: 10s
          read-timeout: 5s
```

### Best Practices for Spring Boot Integration

#### 1. Image Selection
Use standard images that Spring Boot recognizes automatically:
```yaml
services:
  postgres:
    image: ${POSTGRES_IMAGE:-postgres:17}          # Spring Boot auto-detects
    # vs custom image requiring service-connection label
```

#### 2. Dual Labeling Strategy
Include both Spring Boot and wrapper system labels:
```yaml
services:
  postgres:
    image: postgres:17
    labels:
      # Spring Boot labels (if needed for custom images)
      - "org.springframework.boot.service-connection=postgres"
      
      # Our wrapper system labels
      - "info.group=Database Services"
      - "info.title=PostgreSQL Database"
      - "info.url.jdbc=jdbc:postgresql://localhost:${PG_PORT:-15432}/mydb"
      - "info.cred.username=postgres"
      - "info.cred.password=password"
```

#### 3. Development Workflow
```bash
# Option 1: Let Spring Boot manage containers
mvn spring-boot:run    # Spring Boot starts containers automatically

# Option 2: Use our wrapper for manual control  
compose up            # Start containers manually
mvn spring-boot:run   # Spring Boot detects running containers, doesn't start them again
compose down          # Manual cleanup when needed
```

### AI Agent Guidelines for Spring Boot Projects

When working with Spring Boot applications:

1. **Use Standard Images**: Prefer standard Docker images that Spring Boot auto-detects over custom images
2. **Dual Labels**: Always include both `info.*` labels for our wrapper AND Spring Boot labels if using custom images
3. **Configuration Path**: If the Spring Boot app is not in the repository root, configure `spring.docker.compose.file` to point to `docker/compose.yaml`
4. **Lifecycle Coordination**: Consider whether the application should manage containers or if developers will use the wrapper manually

### Example Complete Service Definition

```yaml
services:
  postgres:
    image: ${POSTGRES_IMAGE:-postgres:17}
    environment:
      POSTGRES_USER: ${POSTGRES_CRED_USERNAME:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_CRED_PASSWORD:-password}
      PGDATA: "/data/postgres"
    ports:
      - "${PG_PORT:-15432}:5432"
    volumes:
      - postgres:/data/postgres
      - ./postgres/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 5
    restart: unless-stopped
    labels:
      # Spring Boot automatically detects postgres image - no service-connection label needed
      
      # Our wrapper system labels
      - "info.group=Database Services"
      - "info.title=PostgreSQL Database"
      - "info.url.jdbc=jdbc:postgresql://localhost:${PG_PORT:-15432}/mydb"
      - "info.url.psql=postgresql://postgres:password@localhost:${PG_PORT:-15432}/postgres"
      - "info.cred.username=${POSTGRES_CRED_USERNAME:-postgres}"
      - "info.cred.password=${POSTGRES_CRED_PASSWORD:-password}"
```

This integration provides the best of both worlds: automatic Spring Boot service discovery and our enhanced developer experience through the wrapper system.

---

This implementation maintains simplicity while providing the flexibility needed for diverse development environments. The constraint-based design ensures consistency and predictability across different projects and teams.