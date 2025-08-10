# Compose Script Implementation Plan

Based on the design constraints in `compose-design.md`, here's the implementation plan for the new `compose` script.

## Impact of Developer Persona Constraints

**Constraint 0** keeps implementation simple:

### **Script Simplicity Focus**
- **Basic Error Messages**: Simple, direct messages - no advanced troubleshooting output
- **Convention Maintainers Use Docker Compose Directly**: They can run `docker compose config`, `docker compose ps`, etc. for debugging
- **Script Users Get Simple Commands**: Just the essential commands with basic output
- **No Advanced Features**: Keep the script focused on core functionality only

### **Implementation Priorities & Decisions**
1. **Simplicity Over Features**: Resist adding complex output or advanced debugging
2. **Basic Validation**: Simple pass/fail convention checking
3. **Standard Docker Compose Output**: Let docker compose provide the detailed output
4. **Minimal Script Logic**: Keep bash code simple and maintainable

### **Implementation Decisions**
- **Colors**: Use simple ANSI color codes (not external color libraries)
- **yq Dependency**: Assume `yq` is installed, fail fast with clear message if missing
- **Error Handling**: Exit immediately on each validation failure (simplest approach)
- **Docker Compose**: Use v2 only (`docker compose`, not `docker-compose`)
- **Script Location**: `compose` script at repo root (added to PATH via direnv)
- **Profile Edge Cases**: Let humans/docker compose handle invalid profiles (no validation)

# PART 1: Convention Compliance

**🚨 CANNOT PROCEED TO PART 2 WITHOUT USER APPROVAL 🚨**

Before implementing the compose script, the `compose.yaml` and `.env` files must be updated to follow the design constraints.

## Part 1 Current Issues:
1. **Profile System**: Current `compose.yaml` uses `["mcp"]`, `["postgres"]`, `["observability"]` but constraints require `["default", "all"]` profiles
2. **Missing Status Labels**: No `status.*` labels in services for connection information
3. **Missing .env File**: Environment variables are defined inline with defaults, but convention requires separate `.env` file
4. **Environment Variable Names**: Need to verify all variables follow `<SERVICE_NAME>_*` naming convention

## Part 1 Phase 1: Profile System Decision
**TODO LIST:**
- [ ] **Decision Required**: Determine profile approach:
  - Option A: Replace current profiles with `["default", "all"]` system
  - Option B: Keep current profiles (`mcp`, `postgres`, `observability`) and update constraints
  - Option C: Hybrid approach - services have both `["default", "all"]` AND specific profiles
- [ ] **User Approval**: Get explicit approval on chosen profile approach

## Part 1 Phase 2: Environment Variables
**TODO LIST:**
- [ ] **Analyze Current Variables**: Review current `${VAR:-default}` patterns in compose.yaml
- [ ] **Verify Naming**: Ensure all variables follow `<SERVICE_NAME>_*` convention
- [ ] **Create .env File**: Extract defaults from compose.yaml into separate `.env` file
- [ ] **Update compose.yaml**: Remove inline defaults, use pure `${VAR}` interpolation
- [ ] **User Review**: Get approval on `.env` file contents and structure

## Part 1 Phase 3: Status Labels
**TODO LIST:**
- [ ] **Add MCP Labels**: Add `status.*` labels to MCP services (everything-sse, everything-streamable, mcp-inspector)
- [ ] **Add PostgreSQL Labels**: Add `status.*` labels to PostgreSQL services (postgres, pgadmin)
- [ ] **Add Observability Labels**: Add `status.*` labels to observability services (grafana, prometheus, loki, tempo, otel-collector)
- [ ] **Follow Conventions**: Use `status.url.*` and `status.cred.*` patterns with environment variable interpolation
- [ ] **User Review**: Get approval on label structure and content

## Part 1 Phase 4: Final Validation
**TODO LIST:**
- [ ] **Convention Check**: Verify all services have profiles set
- [ ] **Environment Check**: Verify `.env` file exists and follows naming conventions
- [ ] **Label Check**: Verify all services have appropriate `status.*` labels
- [ ] **User Approval**: Get final approval on convention compliance before proceeding to Part 2

---

# PART 2: Compose Script Implementation

**⚠️  PART 2 CANNOT BEGIN UNTIL PART 1 IS COMPLETE AND APPROVED ⚠️**

## Part 2 Phase 1: Core Infrastructure
**TODO LIST:**
- [ ] **Script Setup**: Create new `compose` script with proper shebang and error handling (`set -Eeuo pipefail`)
- [ ] **Logging Functions**: Implement basic logging functions with simple ANSI colors
- [ ] **Dependency Checks**: Add checks for required commands (`docker` and `yq`)
- [ ] **Convention Validation**: Implement validation functions:
  - `check_compose_file()` - Check for `compose.yaml` existence (exact filename required)
  - `check_env_file()` - Check for `.env` existence (required by convention)  
  - `check_service_profiles()` - Validate all services have profiles set (using yq, fail fast if yq missing)

## Part 2 Phase 2: Environment and Profile Management
**TODO LIST:**
- [ ] **Environment Loading**: Implement `load_env()` function using `set -a` / `source` approach
- [ ] **Profile Discovery**: Implement `get_profiles()` function using `docker compose config --profiles`
- [ ] **Profile Parsing**: Implement profile parsing logic:
  - No profiles specified → use "default" profile
  - Specific profiles specified → use those profiles
  - Space-separated profiles → use specified profiles
- [ ] **Docker Compose Wrapper**: Create `docker_compose_with_profiles()` helper function

## Part 2 Phase 3: Basic Commands
**TODO LIST:**
- [ ] **Docker Compose Wrappers**: Implement commands that wrap docker compose:
  - `up [profiles...]` - Start services with profile filtering
  - `down [profiles...]` - Stop services with profile filtering
  - `ps [profiles...]` - Show container status with profile filtering
  - `logs [profiles...]` - Show logs with profile filtering
  - `build [profiles...]` - Build images with profile filtering
- [ ] **Visual Headers**: Add colored dividers showing which profile is being operated on
- [ ] **Error Handling**: Add port conflict detection for `up` command failures

## Part 2 Phase 4: Custom Commands
**TODO LIST:**
- [ ] **Clean Command**: Implement `clean [profiles...]` - Remove volumes and networks
- [ ] **Profiles Command**: Implement `profiles` - List all available profiles with descriptions
- [ ] **Status Command**: Implement `status [profiles...]` - Show connection information using `status.*` labels
- [ ] **Usage Function**: Implement help/usage display

**`profiles` command output**:
```bash
compose profiles
Available profiles:
  default          # Services that run in default development environment
  all              # All services (for integration testing)  
  db               # Database services (postgres, pgadmin)
  observability    # Observability stack (grafana, tempo, prometheus, otel-collector)
```

**`clean` command implementation**:
```bash
clean)
  validate_conventions
  load_env
  profiles=$(parse_profiles "$@")
  show_profile_divider "$profiles"
  
  # Use docker compose down with volume removal
  docker_compose_with_profiles "down" "$profiles" "-v" "--remove-orphans"
  ;;
```

## Phase 3: Status Command Implementation

### 3.1 Label Extraction
- Use `docker compose config` to extract service labels
- Filter for labels starting with `status.`
- Parse label types:
  - `status.url.*` - Connection URLs
  - `status.cred.*` - Credential information

### 3.2 Status Display
**Simple inline implementation** (no separate functions needed):
```bash
status)
  validate_conventions
  load_env
  profiles=$(parse_profiles "$@")
  log header "Container Status ($profiles profile)" "blue"
  
  local containers=$(docker_compose "$profiles" ps -q)
  for container in $containers; do
    local container_name=$(docker inspect "$container" --format '{{.Name}}' | sed 's/^.//')
    local status_labels=$(docker inspect "$container" --format '{{range $key, $value := .Config.Labels}}{{if hasPrefix $key "status."}}{{$key}}={{$value}}{{"\n"}}{{end}}{{end}}')
    
    if [[ -n "$status_labels" ]]; then
      echo
      echo "$container_name:"
      echo "$status_labels"
    fi
  done
  ;;
```

**Status Command Behavior**:
- Get all running containers for specified profile
- Use `docker inspect` to extract labels from each container with Go template filtering
- Display raw `status.*` labels (whatever convention maintainers put in compose.yaml)
- Display status info per container individually
- **No scaled container support**: Script assumes 1 container per service (scaling not supported)

## Phase 4: Error Handling & Validation

### 4.1 Convention Validation Functions
**`check_compose_file()`**:
```bash
check_compose_file() {
  [[ -f compose.yaml ]] || { log error "compose.yaml not found"; exit 1; }
}
```

**`check_env_file()`**:
```bash
check_env_file() {
  [[ -f .env ]] || { log error ".env file not found"; exit 1; }
}
```

**`check_service_profiles()`**:
```bash
check_service_profiles() {
  local services_without_profiles=$(yq '.services | to_entries | map(select(.value.profiles == null)) | .[].key' compose.yaml 2>/dev/null)
  
  if [[ -n "$services_without_profiles" ]]; then
    log error "Services missing profiles: $(echo $services_without_profiles | tr '\n' ' ')"
    exit 1
  fi
}
```

**`validate_conventions()`**:
```bash
validate_conventions() {
  check_compose_file
  check_env_file  
  check_service_profiles
}
```

### 4.2 Docker Compose Integration
- For non-convention errors (invalid profiles, missing images, etc.), let docker compose handle validation
- Pass through docker compose error messages without modification
- Maintain fail-fast behavior throughout

### 4.3 Common Error Troubleshooting
**`detect_port_conflicts()` (for `up` command)**:
```bash
detect_port_conflicts() {
  # Simple approach: check all running containers for port conflicts
  # Don't need to know which specific ports the profile was trying to use
  
  local containers_with_ports=$(docker ps --format "table {{.Names}}\t{{.Ports}}" | tail -n +2)
  
  if [[ -n "$containers_with_ports" ]]; then
    log error "Port conflicts detected with running containers:"
    echo "$containers_with_ports"
    echo
    
    # Show project directories for conflicting containers
    docker ps --format "{{.Names}}" | while read container; do
      local project_dir=$(docker inspect "$container" -f '{{index .Config.Labels "com.docker.compose.project.working_dir"}}' 2>/dev/null || true)
      
      if [[ -n "$project_dir" ]]; then
        log info "Container: $container (Project: $project_dir)"
        log info "Stop it with: cd $project_dir && docker compose down"
      else
        log info "Container: $container (Stop with: docker stop $container)"
      fi
    done
  fi
}
```

### 4.4 Error Categories
**Convention Violations (Script handles)**:
- Missing `compose.yaml` file
- Missing `.env` file  
- Services without profiles

**Common Development Errors (Script provides troubleshooting)**:
- Port conflicts (show conflicting container and project directory)

**Docker Compose Errors (Let docker compose handle)**:
- Invalid profile names
- Missing environment variables
- Invalid Docker images
- Service dependency issues
- Network/volume configuration errors

## Implementation Details

### Core Functions Needed:
```bash
# Convention validation (detailed implementations in Phase 4)
validate_conventions()        # Calls all validation functions
check_compose_file()         # Validate compose.yaml exists
check_env_file()            # Validate .env exists  
check_service_profiles()    # Use yq to find services without profiles

# Environment handling
load_env()                  # Use set -a / source approach

# Profile management
get_profiles()             # Use docker compose config --profiles
parse_profiles()           # Handle "default", "all", and specific profiles
build_profile_args()       # Build --profile flags for docker compose

# Docker compose wrapper
docker_compose_with_profiles()

# Common error troubleshooting
detect_port_conflicts()    # Check for port conflicts and show project directories

# Utilities
log()                      # Colored logging function (with optional color parameter)
show_profile_divider()     # Visual separator for profile operations
```

### Command Structure:
```bash
case $cmd in
  up)
    validate_conventions
    load_env
    profiles=$(parse_profiles "$@")
    show_profile_divider "$profiles"
    
    # Try to start services
    if ! docker_compose_with_profiles "$cmd" "$profiles" "${remaining_args[@]}"; then
      # If failed, check for port conflicts
      detect_port_conflicts "$profiles"
      exit 1
    fi
    ;;
  down|ps|logs|build)
    validate_conventions
    load_env
    profiles=$(parse_profiles "$@")
    show_profile_divider "$profiles"
    docker_compose_with_profiles "$cmd" "$profiles" "${remaining_args[@]}"
    ;;
  clean)
    validate_conventions
    load_env
    profiles=$(parse_profiles "$@")
    show_profile_divider "$profiles"
    docker_compose_with_profiles "down" "$profiles" "-v" "--remove-orphans"
    ;;
  status)
    validate_conventions
    load_env
    profiles=$(parse_profiles "$@")
    log header "Container Status ($profiles profile)" "blue"
    
    local containers=$(docker_compose "$profiles" ps -q)
    for container in $containers; do
      local container_name=$(docker inspect "$container" --format '{{.Name}}' | sed 's/^.//')
      local status_labels=$(docker inspect "$container" --format '{{range $key, $value := .Config.Labels}}{{if hasPrefix $key "status."}}{{$key}}={{$value}}{{"\n"}}{{end}}{{end}}')
      
      if [[ -n "$status_labels" ]]; then
        echo
        echo "$container_name:"
        echo "$status_labels"
      fi
    done
    ;;
  profiles)
    # Get available profiles from compose.yaml
    log header "Available Profiles"
    local available_profiles=($(get_profiles))
    
    for profile in "${available_profiles[@]}"; do
      case "$profile" in
        default)      echo "  default          # Services that run in default development environment" ;;
        all)          echo "  all              # All services (for integration testing)" ;;
        db)           echo "  db               # Database services (postgres, pgadmin)" ;;
        observability) echo "  observability    # Observability stack (grafana, tempo, prometheus, otel-collector)" ;;
        *)            echo "  $profile         # Custom profile" ;;
      esac
    done
    ;;
  *)
    show_usage
    exit 1
    ;;
esac
```

## File Structure After Implementation:
```
/
├── compose                    # New compose script
├── compose.yaml              # Docker compose file (with profiles and status.* labels)
├── .env                      # Environment variables
├── .env.local               # Local overrides (gitignored)
├── compose-design.md        # Design constraints
├── compose-implementation-plan.md  # This file
└── docker/                  # Service-specific files
    ├── service1/
    │   ├── Dockerfile
    │   └── config files...
    └── service2/
        ├── Dockerfile  
        └── config files...
```

## Testing Strategy:
1. **Convention Validation Testing**:
   - Missing `compose.yaml` file → should fail with clear message
   - Missing `.env` file → should fail with clear message
   - Services without profiles → should fail listing which services
   - Valid configuration → should pass validation
2. **Environment Loading Testing**:
   - Test `.env` file loading
   - Test `.env.local` overrides
   - Test shell environment precedence
3. **Profile System Testing**:
   - `compose up` → runs "default" profile only
   - `compose up all` → runs "all" profile only  
   - `compose up postgres mcp` → runs specified profiles
4. **Status/Label Testing**:
   - Test `status.*` label extraction
   - Test connection info formatting
5. **Integration Testing**:
   - Test with real compose.yaml having proper profiles and labels

## Migration from Current System:
1. Keep current `compose` and `simple` scripts during development
2. Update `compose.yaml` to add profiles to all services
3. Update `compose.yaml` to add `status.*` labels
4. Create `.env` file with proper naming conventions
5. Test new script alongside current system
6. Replace old scripts once validated

## Profile Configuration Philosophy

This implementation chooses **explicit over magic**:

### ✅ **Explicit Profile Configuration**
```yaml
services:
  db:
    image: postgres:17
    profiles: ["all", "default"]    # Explicit: runs in both scenarios
  cache:
    image: redis:7  
    profiles: ["all"]               # Explicit: only runs with "compose up all"
```

### ✅ **Benefits of Explicitness**
- **Zero Ambiguity**: Looking at compose.yaml shows exactly what runs when
- **Simple Script Logic**: No profile inheritance, expansion, or magic
- **Predictable Behavior**: `compose up` runs "default", `compose up all` runs "all"
- **Easy Debugging**: Service behavior is visible in the YAML

### ❌ **Accepted Trade-offs**
- **Repetitive YAML**: Most services need `profiles: ["all", "default"]`
- **More Verbose**: Extra lines in compose.yaml
- **Manual Management**: Must remember to add profiles to new services

**Conclusion**: The script prioritizes simplicity and maintainability over convenience, keeping complex logic out of bash and making all behavior explicit in the compose.yaml file.