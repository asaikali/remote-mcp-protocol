# Compose Script Implementation Plan

Based on the design constraints in `compose-design.md`, here's the implementation plan for the new `compose` script.

## Phase 1: Core Infrastructure

### 1.1 Basic Script Structure
- Create new `compose` script with proper shebang and error handling (`set -Eeuo pipefail`)
- Implement basic logging functions with colors
- Add convention validation functions:
  - Check for `compose.yaml` existence
  - Check for `.env` existence
  - Validate all services have profiles set

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
- Group status information by service
- Format URLs and credentials nicely
- Show only information for services in specified profiles
- Use consistent formatting with colors and structure

## Phase 4: Error Handling & Validation

### 4.1 Convention Validation
- Validate `compose.yaml` exists and is the only compose file
- Validate `.env` exists (required by convention)
- Validate all services have profiles (parse compose.yaml to check)
- Fail fast with clear error messages for convention violations

### 4.2 Docker Compose Integration
- For non-convention errors, let docker compose handle validation
- Pass through docker compose error messages
- Maintain fail-fast behavior

## Implementation Details

### Core Functions Needed:
```bash
# Convention validation
validate_conventions()
check_compose_file()
check_env_file()  
check_service_profiles()

# Environment handling
load_env()

# Profile management
get_profiles()
parse_profiles()              # Handle "default", "all", and specific profiles (all are regular profiles)
build_profile_args()

# Docker compose wrapper
docker_compose_with_profiles()

# Status/label handling
extract_status_labels()
format_status_output()

# Utilities
log()
show_profile_divider()
```

### Command Structure:
```bash
case $cmd in
  up|down|ps|logs|build)
    validate_conventions
    load_env
    profiles=$(parse_profiles "$@")           # Handle "default", "all", specific profiles
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
1. Test convention validation (missing files, missing profiles)
2. Test environment loading precedence
3. Test profile filtering for each command
4. Test status label extraction and formatting
5. Test integration with existing compose.yaml

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