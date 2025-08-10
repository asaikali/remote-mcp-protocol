# Compose Script Implementation Plan

Based on the design constraints in `compose-design.md`, here's the implementation plan for the new `compose` script.

## Impact of Developer Persona Constraints

**Constraint 0** keeps implementation simple:

### **Script Simplicity Focus**
- **Basic Error Messages**: Simple, direct messages - no advanced troubleshooting output
- **Convention Maintainers Use Docker Compose Directly**: They can run `docker compose config`, `docker compose ps`, etc. for debugging
- **Script Users Get Simple Commands**: Just the essential commands with basic output
- **No Advanced Features**: Keep the script focused on core functionality only

### **Implementation Priorities**
1. **Simplicity Over Features**: Resist adding complex output or advanced debugging
2. **Basic Validation**: Simple pass/fail convention checking
3. **Standard Docker Compose Output**: Let docker compose provide the detailed output
4. **Minimal Script Logic**: Keep bash code simple and maintainable

## Phase 1: Core Infrastructure

### 1.1 Basic Script Structure
- Create new `compose` script with proper shebang and error handling (`set -Eeuo pipefail`)
- Implement basic logging functions with colors
- Add convention validation functions:
  - `check_compose_file()` - Check for `compose.yaml` existence (exact filename required)
  - `check_env_file()` - Check for `.env` existence (required by convention)
  - `check_service_profiles()` - Validate all services have profiles set (using yq)

### 1.2 Environment Loading
- Implement the `load_env()` function using the `set -a` / `source` approach:
  ```bash
  load_env() {
    set -a
    [[ -f .env ]] && source .env
    [[ -f .env.local ]] && source .env.local  
    set +a
  }
  ```
- This leverages Docker Compose's built-in environment handling

### 1.3 Profile Management
- Implement `get_profiles()` function using `docker compose config --profiles`
- Implement profile parsing to handle:
  - No profiles specified → use "default" profile
  - Specific profiles specified → use those profiles (including "all" and "default")
  - Space-separated profiles → use specified profiles
- Handle reserved profile names (both are real profiles in compose.yaml):
  - "default": Explicit profile that developers set on services for default development environment
  - "all": Explicit profile that developers set on services that should run when everything is needed
- Most services will have both profiles: `profiles: ["all", "default"]` (explicit, no inheritance)
- Add profile validation in service check (ensure all services have profiles)

## Phase 2: Command Implementation

### 2.1 Docker Compose Wrapper Commands
Implement commands that wrap docker compose with formatting:
- `up [profiles...]` - Start services with profile filtering
- `down [profiles...]` - Stop services with profile filtering  
- `ps [profiles...]` - Show container status with profile filtering
- `logs [profiles...]` - Show logs with profile filtering
- `build [profiles...]` - Build images with profile filtering

Each command should:
- Load environment via `load_env()`
- Parse profiles (default to "default" profile, "all" is treated as a regular profile)
- Add visual dividers showing which profile is being operated on
- Call `docker compose` with appropriate `--profile` flags
- **For `up` command**: Add port conflict detection if docker compose fails

### 2.2 Custom Commands
- `clean [profiles...]` - Remove volumes and networks for specified profiles
- `profiles` - List all available profiles from compose.yaml (explain "default" and "all" conventions)
- `status [profiles...]` - Show connection information using `status.*` labels
- All custom commands treat "all" and "default" as regular profiles (no special expansion logic)

## Phase 3: Status Command Implementation

### 3.1 Label Extraction
- Use `docker compose config` to extract service labels
- Filter for labels starting with `status.`
- Parse label types:
  - `status.url.*` - Connection URLs
  - `status.cred.*` - Credential information

### 3.2 Status Display
- Extract `status.*` labels from running containers
- Display connection information grouped by service
- Simple formatting - no complex output
- Show only information for services in specified profiles

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
  local profile="$1"
  # Get ports that should be used by this profile
  local profile_ports=($(get_profile_ports "$profile"))
  
  for port in "${profile_ports[@]}"; do
    local container=$(docker ps --filter "publish=$port" --format "{{.Names}}" | head -n1)
    if [[ -n "$container" ]]; then
      # Get compose project directory from container labels
      local project_dir=$(docker inspect "$container" -f '{{index .Config.Labels "com.docker.compose.project.working_dir"}}' 2>/dev/null || true)
      
      log error "Port $port is used by container: $container"
      if [[ -n "$project_dir" ]]; then
        log info "Project directory: $project_dir"
        log info "Stop it with: cd $project_dir && docker compose down"
      else
        log info "Stop it with: docker stop $container"
      fi
    fi
  done
}
```

**`get_profile_ports()` helper**:
```bash
get_profile_ports() {
  local profile="$1"
  # Extract port mappings from compose.yaml for specified profile
  # Implementation: parse compose config output for services with specified profile
  docker compose config --profile "$profile" | yq '.services.*.ports[]' | grep -o '[0-9]*:' | cut -d: -f1
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
get_profile_ports()        # Extract port mappings for a profile

# Status/label handling
extract_status_labels()    # Pull status.* labels from running containers
format_status_output()     # Format connection info nicely

# Utilities
log()                      # Colored logging function
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
    # Custom implementation for volume/network cleanup 
    ;;
  status)
    # Custom implementation using label extraction
    ;;
  profiles)
    # List available profiles + explain "default"/"all" conventions
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