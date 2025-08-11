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

### Constraint 5: Error Handling Philosophy

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

**Symlink resolution**: The script resolves symlinks to find the actual script location
**Path independence**: All operations are relative to script location, not current working directory

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

This implementation maintains simplicity while providing the flexibility needed for diverse development environments. The constraint-based design ensures consistency and predictability across different projects and teams.