# Docker Compose Wrapper - Design & Implementation

*Complete technical documentation for system designers, AI coding assistants, maintainers, and developers who need to understand or modify the Docker Compose wrapper system.*

---

## System Overview

This is a **convention-based Docker Compose wrapper system** designed for universal applicability across any project. The system prioritizes simplicity and developer experience through carefully designed constraints and conventions.

### Design Philosophy

**Convention Over Configuration**: The system works through established patterns rather than complex configuration files or options.

**System Philosophy**: All developers want a simple experience getting going with a repo - checkout, import into IDE, launch dependency services, and get coding. This system achieves that simplicity through team conventions maintained by a few people on the team, allowing most developers to just run commands and follow established patterns without needing to understand the underlying implementation details.

**Two-Persona Design**:
- **Persona 1 (Script Users)**: 95% of developers who just want to run `compose up` and get coding
  - **Basic Docker Compose Knowledge**: Understand fundamental Docker Compose concepts (services, volumes, ports, environment variables)
  - **Script Conventions**: Learn the compose script conventions (`compose up`, `compose info`, environment configuration)
- **Persona 2 (Script & Convention Maintainers)**: 5% of developers who design conventions and maintain the system
  - **Advanced Docker Compose Experience**: Deep understanding of labels, environment interpolation, and compose file structure
  - **System Design Mindset**: Responsible for designing and maintaining the conventions, tooling, and documentation
  - **DevOps/Platform Orientation**: Comfortable establishing development infrastructure standards
  - **Script Modification**: Only this persona modifies the compose script itself or establishes new conventions

**Universal Portability**: The entire `docker/` directory can be copied to any project and work immediately.

---

## File Structure & Architecture

### Project Structure

```
docker/
├── compose                      # Universal wrapper script (bash, executable)
├── compose.yaml                 # Single compose file with all services
├── .env                         # Team defaults (optional, committed)
├── .env.local                   # Personal overrides (optional, gitignored)
├── README.md                    # User-facing documentation
├── DESIGN-AND-IMPLEMENTATION.md # This file - complete technical reference
├── postgres/                    # PostgreSQL-specific configurations
│   ├── init.sql                 # Database initialization
│   └── pgadmin_servers.json
├── observability/               # Monitoring stack configurations
│   ├── config/                  # Service config files
│   └── grafana/                 # Grafana-specific files
└── [service-type]/              # Other service directories
```

### Core Components

**Universal Wrapper Script** (`compose`):
- Works from any directory in the repository via script location detection
- Provides enhanced commands (`info`, `clean`) beyond standard Docker Compose
- Loads environment variables and validates conventions

**Single Compose File** (`compose.yaml`):
- All services defined in one file for simplicity
- Uses `info.*` labels for service information display
- Environment variable defaults with `${VAR:-default}` syntax

**Environment Loading System**:
- Precedence: shell environment > `.env.local` > `.env` > compose.yaml defaults
- Auto-export mechanism for Docker Compose integration

---

## Core Design Constraints

### Constraint 1: File Structure & Dependencies

**Required Components**:
- **Dependencies**: `docker`, `yq`, and `jq` must be available in PATH
- **Single script**: Only one `compose` script in the `docker/` directory  
- **Single compose file**: Only `compose.yaml` (exact filename) in the `docker/` directory
- **Optional service directories**: Organized by service type (`postgres/`, `observability/`, etc.)

**File Organization Principles**:
- **Service-based directory structure**: Group related files by logical function
- **Flexible configuration mounting**: Reference service configs using relative paths
- **No script enforcement**: Directory structure is convention, not requirement
- **Image flexibility**: Services can use network images, local builds, or both
- **Dependency management**: Handled via Docker Compose `depends_on` field

**Simple Onboarding**: `git clone && cd docker && ./compose up` works immediately without setup.

### Constraint 2: Environment Loading System

**Environment File Strategy**:
- **`.env` optional**: `compose.yaml` has built-in defaults using `${VAR:-default}` syntax
- **`.env.local` for personal overrides**: Gitignored, takes precedence over `.env`
- **Shell environment highest priority**: Allows temporary overrides

**Loading Implementation**:
```bash
load_env() {
    set -a                                        # Auto-export variables
    [[ -f "$script_dir/.env" ]] && source "$script_dir/.env"              # Team defaults
    [[ -f "$script_dir/.env.local" ]] && source "$script_dir/.env.local"  # Personal overrides  
    set +a                                        # Stop auto-export
}
```

**Precedence Order**: Shell environment > `.env.local` > `.env` > compose.yaml defaults

### Constraint 3: Environment Variable Conventions

**Naming Patterns** (enforced by convention, not script):
- **Images**: `<SERVICE_NAME>_IMAGE` (e.g., `POSTGRES_IMAGE=postgres:17`)
- **Single port**: `<SERVICE_NAME>_PORT` (e.g., `PG_PORT=15432`)  
- **Multiple ports**: `<SERVICE_NAME>_PORT_<PORT_NAME>` (e.g., `MCP_INSPECTOR_WS_PORT=6277`)
- **Credentials**: `<SERVICE_NAME>_CRED_<TYPE>` (e.g., `POSTGRES_CRED_USERNAME`, `POSTGRES_CRED_PASSWORD`)

**Usage Pattern in compose.yaml**:
```yaml
services:
  postgres:
    image: ${POSTGRES_IMAGE:-postgres:17}
    ports:
      - "${PG_PORT:-15432}:5432"
    environment:
      POSTGRES_USER: ${POSTGRES_CRED_USERNAME:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_CRED_PASSWORD:-password}
```

**No Script Enforcement**: Convention maintainers (Persona 2) enforce naming manually to keep script simple.

### Constraint 4: Docker Compose File Conventions

**Philosophy**: Let Docker Compose handle standard functionality automatically - only specify when overriding defaults.

**Container Naming**:
- **❌ DON'T**: Set explicit `container_name` in services  
- **✅ DO**: Let Docker Compose auto-generate names using `<project>-<service>-<index>` pattern
- **Exception**: Only set `container_name` when external systems need a specific name

**Service Field Ordering Convention**:
Services should follow this field order for consistency and readability:

1. **`image` / `build`** - Base image or build context (image first if not building locally)  
2. **`depends_on`** - Service startup dependencies 
3. **`command` / `entrypoint`** - Runtime execution overrides (if needed)
4. **`environment`** - Configuration via environment variables 
5. **`ports`** - Network port mappings (often reference env vars)
6. **`volumes`** - Data persistence and config file mounts
7. **`networks`** - Network configuration (if custom networks needed)
8. **`restart`** - Container restart policy
9. **`labels`** - Metadata for tooling and info command

**Rationale**: This order follows information priority - essential identity first, then dependencies, configuration, persistence, and finally metadata.

**Service Configuration Best Practices**:
- **Environment variable defaults**: `${VAR:-default}` for all configurable values
- **Info labels**: All services SHOULD have `info.*` labels for connection information
- **Resource naming**: Use descriptive service names that match their function
- **Volume naming**: Prefer named volumes over bind mounts for data persistence
- **Network isolation**: Let Docker Compose create default networks automatically

**Restart Policy Recommendations**:
- **Infrastructure approach**: Use `restart: unless-stopped` for stable services (databases, observability)
  - **Benefits**: Survives Docker daemon restarts, consistent behavior, reliable development environment
  - **Respects manual control**: Still allows `docker compose stop/down` to work normally
- **Development approach**: Omit restart policy for actively developed services
  - **Benefits**: Crashes immediately visible, better for debugging
  - **Use case**: Services under active development where you want to notice failures immediately
- **Choose consistently**: Pick one approach per project based on development workflow needs

### Constraint 5: Service Management Approach

**All Services Together**: No selective service management in the wrapper
- `compose up` starts ALL services by design
- Eliminates decision fatigue about "which services do I need?"
- Modern machines handle multiple containers easily

**Selective Operations Available**:
- Use `docker compose up <service-name>` directly for specific services
- Use `docker compose logs <service-name>` for specific service logs  
- The wrapper provides convenient commands for common all-service operations

### Constraint 6: Service Labeling System

**Generic Script Design**: The script is completely service-agnostic through labels.

**Label Namespace**: `info.*` prefix for all wrapper-related labels
- **`info.group`** - Service group for organizing output (e.g., "Database Services", "API Services")
- **`info.title`** - Human-readable service name (REQUIRED for visibility in `compose info`)  
- **`info.url.<type>`** - Connection URLs by type (ui, api, jdbc, grpc, etc.)
- **`info.cred.<type>`** - Credential information by type (username, password, api_key, etc.)

**Complete Service Example**:
```yaml
services:
  postgres:
    image: ${POSTGRES_IMAGE:-postgres:17}
    ports:
      - "${PG_PORT:-15432}:5432"
    environment:
      POSTGRES_USER: ${POSTGRES_CRED_USERNAME:-postgres}
      POSTGRES_PASSWORD: ${POSTGRES_CRED_PASSWORD:-password}
    labels:
      - "info.group=Database Services"
      - "info.title=PostgreSQL Database"
      - "info.url.jdbc=jdbc:postgresql://localhost:${PG_PORT:-15432}/mydb"
      - "info.url.ui=http://localhost:${PGADMIN_PORT:-15433}"
      - "info.cred.username=${POSTGRES_CRED_USERNAME:-postgres}"
      - "info.cred.password=${POSTGRES_CRED_PASSWORD:-password}"
```

**Label Extraction**: Script uses `docker compose config --format json` + `jq` to extract and process labels.

### Constraint 7: Universal Access & Script Execution

**Direct Execution Only**:
- Script must be executed directly, not through symbolic links
- Universal access achieved via relative paths or PATH modification
- Script location detected using `dirname "${BASH_SOURCE[0]}"`

**Supported Access Patterns**:
- **Direct paths**: `docker/compose up`, `../docker/compose up`
- **PATH modification**: `export PATH="$PATH:$(pwd)/docker"` or direnv with `PATH_add docker`
- **Shell aliases**: `alias compose='docker/compose'`

**Not Supported**:
- Symbolic links: `ln -s project/docker/compose ~/.local/bin/compose`
- Complex link chains or relative symlinks

**PATH-based Access**: Works perfectly with PATH modifications (like direnv) because the shell executes the script directly from its actual location.

### Constraint 8: Docker Compose Wrapper Commands

**Standard Commands**: Mirror Docker Compose behavior with enhanced formatting
- `up`, `down`, `ps`, `logs`, `build` - Standard operations with visual output
- `compose up` includes auto-build behavior and port conflict detection
- Script should be true to Docker Compose behavior for known commands

### Constraint 9: Custom Commands  

**`clean` Command**:
- No Docker Compose equivalent
- Stops containers and removes volumes: `docker compose down -v --remove-orphans`
- Docker Compose automatically manages which resources belong to the project

**`info` Command**:
- Displays grouped service connection information using `info.*` labels
- Provides all key connection information developers need
- Groups services by `info.group` for organized output

### Constraint 10: Error Handling Philosophy

**Fail Fast on Convention Violations**:
- Missing `compose.yaml` file
- Missing required dependencies (`docker`, `yq`, `jq`)

**Let Docker Compose Handle Everything Else**:
- Service validation, network issues, image pull failures
- YAML syntax errors, missing environment variables
- Keep script simple by leveraging Docker Compose features

**Simple Troubleshooting Only**:
- Generic failure handling after `docker compose up` failures with port conflict suggestions
- Helpful error messages with actionable next steps
- No complex error handling logic

**Common Error Troubleshooting**:
- **Generic Failure Handling**: Detect `docker compose up` failure and suggest port conflicts as likely cause
- **Human Resolution**: Developers handle the actual diagnosis and conflict resolution, script provides generic guidance
- Example output:
  ```
  ✗ Failed to start services - likely port conflicts
  → Check running containers with: docker ps
  → Stop conflicting services or change ports in .env
  ```

**What We Don't Handle**: Image pull failures, network connectivity, volume mount problems, service dependencies, dynamic port conflicts.

---

## Implementation Details

### Script Location Detection

The compose script uses simple **script location detection** to work from any directory:

```bash
get_script_dir() {
    # Get the directory where this script is located
    # Works with direct execution and PATH-based access (like direnv)
    # Note: Does not support symbolic links - use direct paths or PATH modification instead
    dirname "${BASH_SOURCE[0]}"
}
```

**Why This Works**:
- `${BASH_SOURCE[0]}` always contains the path to the script being executed
- Works with direct execution: `/path/to/docker/compose`
- Works with PATH access: `compose` (when docker/ is in PATH)
- Does not work with symlinks (by design for simplicity)

### Environment Loading Implementation

```bash
load_env() {
    local script_dir
    script_dir=$(get_script_dir)
    
    # Auto-export all variables set in env files so Docker Compose can use them
    set -a  
    
    # Load .env if it exists (team defaults, committed to git)
    # The [[ ]] test prevents errors if the file doesn't exist
    [[ -f "$script_dir/.env" ]] && source "$script_dir/.env"
    
    # Load .env.local if it exists (personal overrides, gitignored)
    # This file takes precedence over .env for the same variables
    [[ -f "$script_dir/.env.local" ]] && source "$script_dir/.env.local"
    
    # Stop auto-exporting variables for the rest of the script
    set +a
}
```

### Docker Compose Wrapper Function

```bash
docker_compose() {
    local script_dir
    script_dir=$(get_script_dir)
    
    # Run docker compose from the script directory (not current working directory)
    # The subshell (parentheses) ensures we don't change the caller's working directory
    # 'command' prevents any potential docker compose function/alias conflicts
    (cd "$script_dir" && command docker compose "$@")
}
```

### Commands Implementation

**Standard Commands**:
```bash
case "$cmd" in
    up|down|ps|logs|build)
        # Capitalize the first letter of the command for display
        # ${cmd:0:1} = first character, ${cmd:1} = rest of string
        # tr converts first char to uppercase
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

**Custom Commands**:
```bash
clean)
    show_operation_header "Cleaning"
    docker_compose "down" -v --remove-orphans
    log success "Cleanup completed"
    ;;
```

### Service Info Processing (Complex Section)

**COMPLEX JQ PIPELINE**: Extract service info from Docker Compose configuration

This is the most complex part of the script - here's how it works:

1. `docker_compose config --format json` = get complete compose config as JSON
2. `.services | to_entries` = convert services object to array of {key, value} pairs
3. `map({service: .key, info: ...})` = transform each service entry
4. For the info section:
   - `(.value.labels // {})` = get service labels or empty object
   - `| to_entries` = convert labels to array of {key, value} pairs  
   - `| map(select(.key|startswith("info.")))` = keep only info.* labels
   - `| map({path: (.key | split(".")[1:]), value: .value})` = convert "info.group.Database" to {path:["group","Database"], value:"..."}
   - `| reduce .[] as $i ({}; setpath($i.path; $i.value))` = rebuild nested object from paths

Example: `"info.url.jdbc=jdbc://..."` becomes `{url: {jdbc: "jdbc://..."}}`

```bash
# Extract all services with info labels
services_json=$(docker_compose config --format json 2>/dev/null | jq '.services
                | to_entries
                | map({
                    service: .key,
                    info:
                      ((.value.labels // {})
                       | to_entries
                       | map(select(.key|startswith("info.")))
                       | map({path: (.key | split(".")[1:]), value: .value})
                       | reduce .[] as $i ({}; setpath($i.path; $i.value))
                      )
                  })' 2>/dev/null)
```

**GROUP SERVICES BY info.group LABEL**: 

jq breakdown:
1. `group_by(.info.group // "Other")` = group services by group label, default to "Other"
2. `map({group: (.[0].info.group // "Other"), services: .})` = create {group: "name", services: [...]} objects

```bash
# Group services and display
groups_json=$(echo "$services_json" | jq 'group_by(.info.group // "Other") | map({group: (.[0].info.group // "Other"), services: .})')

# Process each group: extract group name and services as tab-separated values
echo "$groups_json" | jq -r '.[] | "\(.group)\t\(.services | @json)"' | while IFS=$'\t' read -r group services_in_group; do
    # Display group header in red color
    printf "%b== %s%b\n\n" "$RED" "$group" "$NC"
    
    # Process each service in this group
    echo "$services_in_group" | jq -r '.[] | select(.info.title) | "\(.service)\t\(.info | @json)"' | while IFS=$'\t' read -r service info_json; do
        process_service_info_json "$service" "$info_json"
    done
done
```

**URL and Credential Processing**:
```bash
# Extract and display URLs from the info.url.* labels
# jq breakdown: .url // {} = get .url object or empty object if null
#               to_entries[] = convert {key:value} to [{key:"key", value:"value"}]
#               "\(.key): \(.value)" = format as "key: value" strings
local urls
urls=$(echo "$info_json" | jq -r '.url // {} | to_entries[] | "\(.key): \(.value)"' 2>/dev/null || true)
if [[ -n "$urls" ]]; then
    # Read each URL line and display with indentation
    while IFS= read -r url_line; do
        printf "    → %s\n" "$url_line"
    done <<< "$urls"  # Here-string to pass urls to the while loop
fi
```

---

## Spring Boot Integration

This Docker Compose wrapper system works seamlessly with Spring Boot's Docker Compose support (`spring-boot-docker-compose` module).

### Spring Boot Docker Compose Module

When Spring Boot applications include the `spring-boot-docker-compose` dependency, they automatically:
1. **Search for compose.yaml** in the working directory (our system provides this)
2. **Auto-start containers** with `docker compose up` when the application starts
3. **Create service connection beans** for supported containers
4. **Auto-stop containers** with `docker compose stop` when the application shuts down

### Automatic Service Discovery

Spring Boot automatically discovers and connects to services based on container image names:

| Container Image | Spring Boot Auto-Configuration |
|---|---|
| `postgres` or `bitnami/postgresql` | `JdbcConnectionDetails`, `R2dbcConnectionDetails` |
| `redis`, `bitnami/redis` | `RedisConnectionDetails` |
| `rabbitmq`, `bitnami/rabbitmq` | `RabbitConnectionDetails` |
| `mongo`, `bitnami/mongodb` | `MongoConnectionDetails` |
| `otel/opentelemetry-collector-contrib` | `OtlpMetricsConnectionDetails`, `OtlpTracingConnectionDetails` |

### Spring Boot Specific Labels

In addition to our `info.*` labels, services can use Spring Boot-specific labels:

**Service Connection Override**:
```yaml
services:
  my-custom-postgres:
    image: my-company/custom-postgres:latest
    labels:
      - "org.springframework.boot.service-connection=postgres"  # Tell Spring Boot this is PostgreSQL
      - "info.group=Database Services"                          # Our wrapper system
      - "info.title=Custom PostgreSQL"                          # Our wrapper system
```

**Skip Service Connection**:
```yaml
services:
  utility-service:
    image: nginx:alpine
    labels:
      - "org.springframework.boot.ignore=true"          # Spring Boot ignores this service
      - "info.group=Utility Services"                   # But our wrapper still shows it
      - "info.title=Nginx Proxy"
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
```

### Best Practices for Spring Boot Integration

**1. Image Selection**: Use standard images that Spring Boot recognizes automatically
**2. Dual Labeling Strategy**: Include both Spring Boot and wrapper system labels
**3. Development Workflow Options**:
   - Let Spring Boot manage containers: `mvn spring-boot:run`
   - Manual control: `compose up` then `mvn spring-boot:run`
   - Hybrid: `compose up`, `mvn spring-boot:run`, `compose info`, `compose down`

---

## AI Coding Assistant Guidelines

### Critical Rules for AI Agents

When working with this system, AI agents must:

1. **Never modify the compose script logic** unless explicitly asked to change system behavior
2. **Always preserve universal access** - test that commands work from any directory  
3. **Maintain service-agnostic design** - no hardcoded service names in the script
4. **Follow environment variable naming conventions** consistently
5. **Include info.title label** for any service that should appear in `compose info`
6. **Use `${VAR:-default}` syntax** for all configurable values in compose.yaml
7. **Let Docker Compose handle validation** - don't add complex error handling to the script

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

### Service Organization Best Practices

**Directory Structure**:
- Group service configurations by logical function under `docker/`
- Use relative paths in compose.yaml: `./service-type/config.yml`
- Keep related files together in service directories

### Common Tasks for AI Agents

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

### AI Agent Guidelines for Spring Boot Projects

When working with Spring Boot applications:

1. **Use Standard Images**: Prefer standard Docker images that Spring Boot auto-detects over custom images
2. **Dual Labels**: Always include both `info.*` labels for our wrapper AND Spring Boot labels if using custom images
3. **Configuration Path**: If the Spring Boot app is not in the repository root, configure `spring.docker.compose.file` to point to `docker/compose.yaml`
4. **Lifecycle Coordination**: Consider whether the application should manage containers or if developers will use the wrapper manually

---

## Troubleshooting & Common Issues

### Port Conflicts

**Detection**: The script detects `docker compose up` failures and generically suggests port conflicts as a likely cause, without actually detecting whether port conflicts are the real issue. It provides helpful guidance by suggesting the most common cause of startup failures.

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

## Summary

This system achieves its goals through carefully balanced constraints that:

1. **Enforce explicit structure** - Fixed file locations and naming
2. **Promote consistency** - Standardized environment variable and label naming
3. **Maintain simplicity** - Leverage Docker Compose features rather than reimplementing
4. **Enable generic functionality** - Service-agnostic through labeling conventions
5. **Provide developer experience** - Clear error messages and helpful commands
6. **Ensure predictability** - Fail-fast behavior and consistent conventions
7. **Prioritize simplicity** - All services start together, no complex selection logic

The constraint-based design ensures consistency and predictability across different projects and teams while maintaining the flexibility needed for diverse development environments.