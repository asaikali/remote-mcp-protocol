# Compose Script Design Constraints

This document defines the design constraints for the `compose` script and associated Docker configuration system.

## Constraint 0 - Assumptions About Developers

**System Philosophy**: All developers want a simple experience getting going with a repo - checkout, import into IDE, launch dependency services, and get coding. This system achieves that simplicity through team conventions maintained by a few people on the team, allowing most developers to just run commands and follow established patterns without needing to understand the underlying implementation details.

### **Persona 1: Script Users (Majority of Developers)**
- **Basic Docker Compose Knowledge**: Understand fundamental Docker Compose concepts (services, volumes, ports, environment variables)
- **Script Conventions**: Learn the compose script conventions:
  - `compose up` starts all services
  - `compose info` shows connection information for all services
  - Environment configuration through `.env` and `.env.local` files

### **Persona 2: Script & Convention Maintainers (Few Developers per Team)**
- **Advanced Docker Compose Experience**: Deep understanding of labels, environment interpolation, and compose file structure
- **System Design Mindset**: Responsible for designing and maintaining the conventions, tooling, and documentation for team adoption
- **DevOps/Platform Orientation**: Comfortable establishing development infrastructure standards and troubleshooting convention violations
- **Script Modification**: Only this persona modifies the compose script itself or establishes new conventions

## Constraint 1 - File Structure & Dependencies

- **Required Dependencies**: The compose script requires `docker`, `yq`, and `jq` to be installed and available in PATH
- There is only one script called `compose` at the root of the repo
- The compose script only works with `compose.yaml` (exact filename) and expects that file to be in the same location as the script itself. No other compose file variants are supported
- There is only one `compose.yaml` at the root of the repo, and developers cut and paste their containers into it
- At the same level as the compose script and compose.yaml, there is a folder called `docker`
- **Service-Based Directory Structure**: Under the `docker` folder, there can be directories organized by service type (e.g., `db/`, `api/`, `frontend/`, `observability/`)
- **Optional Service Subdirectories**: Under each service type directory, there can be optional subdirectories for individual services (e.g., `db/postgres/`, `api/backend/`)
- **Flexible File Organization**: Service configuration files, Dockerfiles, and mount files are organized by service type, with authors of compose.yaml referencing these files as needed
- **No Script Enforcement**: The compose script does not enforce this directory structure - it's a convention for developers to follow when organizing their Docker-related files
- We follow whatever policy is set in the docker compose yaml - no restrictions on whether services pull network images or build locally
- A service can reference an image pulled from the network, built locally, or both (developer's choice)
- Dependency management is handled via the docker compose `depends_on` field that developers bake into the compose.yaml

## Constraint 2 - Environment Loading

- `.env` file is **optional** - compose.yaml has sensible defaults built-in using `${VAR:-default}` syntax
- Devs can create `.env` to override defaults, and `.env.local` (in `.gitignore`) for personal overrides
- Precedence: shell environment > `.env.local` > `.env` > compose.yaml defaults
- The script loads environment files using shell `source` with `set -a` (auto-export):
  ```bash
  set -a                    # auto-export variables
  [[ -f .env ]] && source .env              # load defaults (if exists)
  [[ -f .env.local ]] && source .env.local  # load overrides (if exists)
  set +a                   # stop auto-export
  ```
- This approach leverages Docker Compose's built-in environment handling and default value syntax
- Variables are exported to the shell environment so Docker Compose picks them up automatically
- **Simple Onboarding**: `git clone && docker compose up` works immediately without requiring .env setup

## Constraint 3 - Environment Naming Conventions

- Service name is part of every .env variable (consistent naming pattern)
- Environment variable naming conventions:
  - **Images**: `<SERVICE_NAME>_IMAGE` (e.g., `POSTGRES_IMAGE=postgres:17`, `GRAFANA_IMAGE=grafana/grafana:12.0.0`)
  - **Single port**: `<SERVICE_NAME>_PORT` (e.g., `PG_PORT=15432`, `GRAFANA_PORT=3000`)  
  - **Multiple ports**: `<SERVICE_NAME>_PORT_<PORT_NAME>` (e.g., `MCP_INSPECTOR_WS_PORT=6277`)
  - **Credentials**: `<SERVICE_NAME>_CRED_<TYPE>` (e.g., `<SERVICE_NAME>_CRED_USERNAME`, `<SERVICE_NAME>_CRED_PASSWORD`, `<SERVICE_NAME>_CRED_API_KEY`)
  - **Note**: MCP services use locally built images and don't need `_IMAGE` variables
- **Usage in compose.yaml**: These environment variables are used via Docker Compose interpolation syntax:
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
- **No Script Enforcement**: The script does not validate that compose.yaml uses these environment variables correctly - convention maintainers (Persona 2) enforce this manually to keep the script simple

## Constraint 4 - Docker Compose File Conventions

**Philosophy**: Let Docker Compose handle standard functionality automatically - only specify when you need to override defaults.

### **Container Naming**
- **❌ DON'T**: Set explicit `container_name` in services
- **✅ DO**: Let Docker Compose auto-generate names using `<project>-<service>-<index>` pattern
- **Rationale**: Auto-generated names prevent conflicts and work well with scaling
- **Exception**: Only set `container_name` when external systems need a specific name

### **Service Field Ordering Convention**
Services should follow this field order for consistency and readability:

1. **`image` / `build`** - Base image or build context (choose one, image first if not building locally)  
2. **`depends_on`** - Service startup dependencies (startup order at a glance)
3. **`command` / `entrypoint`** - Runtime execution overrides (if needed)
4. **`environment`** - Configuration via environment variables 
5. **`ports`** - Network port mappings (often reference env vars, so after environment)
6. **`volumes`** - Data persistence and config file mounts
7. **`networks`** - Network configuration (if custom networks needed)
8. **`restart`** - Container restart policy
9. **`labels`** - Metadata for tooling and info command (grouped at end)

**Rationale**: This order follows information priority - essential identity first (image), then dependencies, configuration, persistence, and finally metadata.

### **Service Configuration Best Practices**
- **Use environment variable defaults**: `${VAR:-default}` for all configurable values
- **Info labels**: All services SHOULD have `info.*` labels for connection information
- **Resource naming**: Use descriptive service names that match their function
- **Volume naming**: Prefer named volumes over bind mounts for data persistence
- **Network isolation**: Let Docker Compose create default networks automatically

### **Restart Policy Recommendations**
- **Infrastructure approach**: Use `restart: unless-stopped` for stable services (databases, observability, established applications)
  - **Benefits**: Survives Docker daemon restarts, consistent behavior, reliable development environment
  - **Respects manual control**: Still allows `docker compose stop/down` to work normally
- **Development approach**: Omit restart policy for actively developed services
  - **Benefits**: Crashes are immediately visible, better for debugging, works well with `--abort-on-container-exit`
  - **Use case**: Services under active development where you want to notice failures immediately
- **Choose consistently**: Pick one approach per project based on development workflow needs

### **File Organization Conventions**
- **Single compose.yaml**: All services in one file at repository root
- **Service-based docker structure**: `docker/<service-type>/` directories for related files (e.g., `docker/db/`, `docker/api/`)
- **Environment precedence**: Shell → `.env.local` → `.env` → compose.yaml defaults

**Note**: These are development team conventions - the compose script does NOT enforce these rules.

## Constraint 5 - Service Management

- All services are started together with `compose up` - no selective service management
- Developers can use standard Docker Compose commands for selective operations if needed:
  - `docker compose up <service-name>` to start specific services
  - `docker compose logs <service-name>` to view specific service logs
- The compose script provides convenient wrappers for common operations across all services

## Constraint 6 - Service Labeling System

- The compose script functionality is completely generic - no service-specific functions
- Service connection info provided through labeling convention in YAML file
- **Labels are set on services in compose.yaml**: Docker Compose applies service labels to all container instances of that service
- Labels use environment variable interpolation from `.env`
- Label naming convention: `info.*` prefix for anything displayed by info command
  - `info.group` - Service group for organizing output (e.g., "Database Services", "API Services")
  - `info.title` - Human-readable service name displayed by info command (REQUIRED)  
  - `info.url.<url-type>` for different URL types (jdbc, ui, grpc, http, etc.)
  - `info.cred.<credential_type>` for credentials (username, password, api_key, etc.)
- **Label extraction**: The compose script extracts labels from service configuration using `docker compose config` to show available connection information
- **Complete service example**:
  ```yaml
  services:
    postgres:
      image: ${POSTGRES_IMAGE}
      ports:
        - "${POSTGRES_PORT}:5432"
      environment:
        POSTGRES_USER: ${POSTGRES_CRED_USERNAME}
        POSTGRES_PASSWORD: ${POSTGRES_CRED_PASSWORD}
      labels:
        - "info.group=Database Services"
        - "info.title=PostgreSQL Database"
        - "info.url.jdbc=jdbc:postgresql://localhost:${POSTGRES_PORT}/mydb"
        - "info.url.ui=http://localhost:${PGADMIN_PORT}"
        - "info.cred.username=${POSTGRES_CRED_USERNAME}"
        - "info.cred.password=${POSTGRES_CRED_PASSWORD}"
    
    pgadmin:
      image: dpage/pgadmin4:latest
      ports:
        - "${PGADMIN_PORT}:80"
      labels:
        - "info.group=Database Services"
        - "info.title=pgAdmin Web Interface"
        - "info.url.ui=http://localhost:${PGADMIN_PORT}"
        - "info.cred.username=admin@example.com"
        - "info.cred.password=admin"
  ```
- Makes the script service-agnostic while providing useful connection information

## Constraint 7 - Docker Compose Wrapper Commands

- Standard docker compose commands: `up`, `down`, `ps`, `logs`, `build`
- These do the standard docker compose operations with nicely formatted output
- `compose up` should auto-build if Dockerfile exists - let docker compose handle its default build behavior
- `compose build` command builds the images locally that have build setup configured
- The script should be true to docker compose behavior for known commands

## Constraint 8 - Custom Commands

- `clean` command has no docker compose equivalent - it stops containers and removes volumes and networks
- **Clean command scope**: Removes all volumes and networks defined in compose.yaml - Docker Compose automatically manages which resources belong to the project
- `info` command prints out all key connection information developers need to connect to services from their apps or access them via UI (uses the `info.*` label system)

## Constraint 9 - Script Behavior & Error Handling

- The compose script should fail fast on any errors
- The compose script only works with compose.yaml files that follow its own conventions
- Script should fail early if any of its conventions are violated:
  - Complain if `compose.yaml` is not found (only supported filename) - convention violation  
  - Note: `.env` file is optional - script loads it if present but doesn't require it
- For non-convention errors, let docker compose handle validation and show its error messages
- Keep the script simple: if docker compose supports features like `--env-file` for overrides, use those docker compose features rather than adding complexity to the script
- No assumptions about Docker Compose version requirements
- For the `info` command, print any labels that are prefixed with `info.*` to keep the script simple
- **Common Error Troubleshooting**: Detect common development errors (like port conflicts) and provide simple troubleshooting information without complex error handling logic
- **Scaled Container Instances Not Supported**: The script does not support scaled container instances (e.g., `docker compose up --scale service=2`) - this is an advanced feature beyond the scope of the current implementation

## Constraint 10 - Common Error Troubleshooting

While the script only validates convention violations and lets Docker Compose handle all other errors, it provides helpful troubleshooting information for common development errors that are difficult to diagnose from Docker Compose output alone.

**Common Errors We Help With:**
- **Port Conflicts**: When `docker compose up` fails, detect and report port conflicts
  - **Simple Detection**: Script detects failure and checks which containers are using ports
  - **No Port Tracking**: Script doesn't need to know which ports a profile should use
  - **Human Resolution**: Developers handle the actual conflict resolution
  - Show the Docker Compose project directory of the conflicting container
  - Provide exact command to stop the conflicting service
  - **No Dynamic Port Handling**: Script doesn't worry about dynamic ports or port ranges
  - Example output:
    ```
    ✗ Port 5432 is used by container: other_project_postgres
    > Project directory: /Users/dev/other-project  
    > Stop it with: cd /Users/dev/other-project && docker compose down
    ```

**What We Don't Handle:**
- Image pull failures
- Network connectivity issues  
- Volume mount problems
- Service dependency failures
- Invalid YAML syntax
- Missing environment variables
- Dynamic port conflicts or port ranges

**Implementation Approach:**
- Detect `docker compose up` failure
- Check running containers for port usage conflicts
- Extract helpful diagnostic information (container names, project directories)
- Display simple, actionable troubleshooting steps
- Keep logic minimal - just failure detection and information extraction

## Summary

These constraints define a simple, convention-based system that:

1. **Enforces explicit structure** - Fixed file locations and naming
2. **Promotes consistency** - Standardized environment variable and label naming
3. **Maintains simplicity** - Leverages docker compose features rather than reimplementing
4. **Enables generic functionality** - Service-agnostic through labeling conventions
5. **Provides developer experience** - Clear error messages and helpful commands
6. **Ensures predictability** - Fail-fast behavior and consistent conventions
7. **Prioritizes simplicity** - All services start together, no complex selection logic

