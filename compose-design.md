# Compose Script Design Constraints

This document defines the design constraints for the `compose` script and associated Docker configuration system.

## Constraint 0 - Assumptions About Developers

**System Philosophy**: All developers want a simple experience getting going with a repo - checkout, import into IDE, launch dependency services, and get coding. This system achieves that simplicity through team conventions maintained by a few people on the team, allowing most developers to just run commands and follow established patterns without needing to understand the underlying implementation details.

### **Persona 1: Script Users (Majority of Developers)**
- **Basic Docker Compose Knowledge**: Understand fundamental Docker Compose concepts (services, volumes, ports, environment variables)
- **Script Conventions**: Learn the compose script conventions:
  - `compose up` starts default development environment
  - `compose up all` starts everything for integration testing
  - `compose up <profiles>` starts specific service groups
  - `compose status` shows connection information for running services
  - Services are organized by profiles (`default`, `all`, custom profiles)
  - Environment configuration through `.env` and `.env.local` files

### **Persona 2: Script & Convention Maintainers (Few Developers per Team)**
- **Advanced Docker Compose Experience**: Deep understanding of profiles, labels, environment interpolation, and compose file structure
- **System Design Mindset**: Responsible for designing and maintaining the conventions, tooling, and documentation for team adoption
- **DevOps/Platform Orientation**: Comfortable establishing development infrastructure standards and troubleshooting convention violations
- **Script Modification**: Only this persona modifies the compose script itself or establishes new conventions

## Constraint 1 - File Structure & Dependencies

- There is only one script called `compose` at the root of the repo
- The compose script only works with `compose.yaml` (exact filename) and expects that file to be in the same location as the script itself. No other compose file variants are supported
- There is only one `compose.yaml` at the root of the repo, and developers cut and paste their containers into it
- At the same level as the compose script and compose.yaml, there is a folder called `docker`
- **Profile-Based Directory Structure**: Under the `docker` folder, there is a directory for each profile (e.g., `db/`, `mcp/`, `observability/`)
- **Optional Service Subdirectories**: Under each profile directory, there can be optional subdirectories for individual services (e.g., `db/postgres/`, `db/pgladmin/`)
- **Flexible File Organization**: Service configuration files, Dockerfiles, and mount files are organized by profile, with authors of compose.yaml referencing these files as needed
- **No Script Enforcement**: The compose script does not enforce this directory structure - it's a convention for developers to follow when organizing their Docker-related files
- We follow whatever policy is set in the docker compose yaml - no restrictions on whether services pull network images or build locally
- A service can reference an image pulled from the network, built locally, or both (developer's choice)
- Dependency management is handled via the docker compose `depends_on` field that developers bake into the compose.yaml

## Constraint 2 - Environment Loading

- There is an `.env` file at the root storing configuration for services
- Devs can create/edit `.env.local` which is in `.gitignore` to override settings from `.env`
- Precedence: shell environment > `.env.local` > `.env`
- The script loads environment files using shell `source` with `set -a` (auto-export):
  ```bash
  set -a                    # auto-export variables
  source .env              # load defaults
  source .env.local        # load overrides (if exists)
  set +a                   # stop auto-export
  ```
- This approach leverages Docker Compose's built-in environment handling rather than custom parsing
- Variables are exported to the shell environment so Docker Compose picks them up automatically

## Constraint 3 - Environment Naming Conventions

- Service name is part of every .env variable (consistent naming pattern)
- Environment variable naming conventions:
  - **Images**: `<SERVICE_NAME>_IMAGE` (e.g., `POSTGRES_IMAGE=postgres:17`)
  - **Single port**: `<SERVICE_NAME>_PORT` (e.g., `POSTGRES_PORT=15432`)  
  - **Multiple ports**: `<SERVICE_NAME>_PORT_<PORT_NAME>` (e.g., `EVERYTHING_MCP_PORT_SSE=3001`)
  - **Credentials**: `<SERVICE_NAME>_CRED_<TYPE>` (e.g., `<SERVICE_NAME>_CRED_USERNAME`, `<SERVICE_NAME>_CRED_PASSWORD`, `<SERVICE_NAME>_CRED_API_KEY`)
- **Usage in compose.yaml**: These environment variables are used via Docker Compose interpolation syntax:
  ```yaml
  services:
    postgres:
      image: ${POSTGRES_IMAGE}
      ports:
        - "${POSTGRES_PORT}:5432"
      environment:
        POSTGRES_USER: ${POSTGRES_CRED_USERNAME}
        POSTGRES_PASSWORD: ${POSTGRES_CRED_PASSWORD}
  ```
- **No Script Enforcement**: The script does not validate that compose.yaml uses these environment variables correctly - convention maintainers (Persona 2) enforce this manually to keep the script simple

## Constraint 4 - Profile System

- Every service in the compose file is required to have a profile explicitly set
- There are two reserved profile names by convention that must be explicitly configured in compose.yaml:
  - **"default"**: Must be explicitly set on services that should run in the default development environment
  - **"all"**: Must be explicitly set on services that should run when everything is needed
- Services are tagged with profiles explicitly in compose.yaml:
  ```yaml
  services:
    db:
      image: postgres:17
      profiles: ["all", "default"]    # Runs in both default and all scenarios
    cache:
      image: redis:7
      profiles: ["all"]               # Only runs with "compose up all"
  ```
- Most common services will need both profiles: `profiles: ["all", "default"]` (accepted trade-off for explicitness)
- When you type `compose up` (or any compose command without profile specification), it runs all services with the "default" profile
- When you type `compose up all`, it runs all services with the "all" profile
- Space-separated profile names can be given for specific profile targeting
- `compose up postgres mcp` means run it on postgres and mcp profiles
- No profile inheritance or magic - what you see in compose.yaml is exactly what runs
- If users type `docker compose up` directly in the root (without using the script), nothing will start because no profiles are specified - this forces them to use the script or be explicit about profiles

## Constraint 5 - Service Labeling System

- The compose script functionality is completely generic - no service-specific functions
- Service connection info provided through labeling convention in YAML file
- **Labels are set on services in compose.yaml**: Docker Compose applies service labels to all container instances of that service
- Labels use environment variable interpolation from `.env`
- Label naming convention: `status.*` prefix for anything displayed by status command
  - `status.url.<url-type>` for different URL types (jdbc, ui, grpc, http, etc.)
  - `status.cred.<credential_type>` for credentials (username, password, api_key, etc.)
- **Label extraction**: The compose script extracts labels from running containers using `docker inspect` to reflect current running state
- **Complete service example**:
  ```yaml
  services:
    postgres:
      image: ${POSTGRES_IMAGE}
      profiles: ["all", "default"]
      ports:
        - "${POSTGRES_PORT}:5432"
      environment:
        POSTGRES_USER: ${POSTGRES_CRED_USERNAME}
        POSTGRES_PASSWORD: ${POSTGRES_CRED_PASSWORD}
      labels:
        - "status.url.jdbc=jdbc:postgresql://localhost:${POSTGRES_PORT}/mydb"
        - "status.url.ui=http://localhost:${PGADMIN_PORT}"
        - "status.cred.username=${POSTGRES_CRED_USERNAME}"
        - "status.cred.password=${POSTGRES_CRED_PASSWORD}"
    
    pgadmin:
      image: dpage/pgadmin4:latest
      profiles: ["all", "default"]
      ports:
        - "${PGADMIN_PORT}:80"
      labels:
        - "status.url.ui=http://localhost:${PGADMIN_PORT}"
        - "status.cred.username=admin@example.com"
        - "status.cred.password=admin"
  ```
- Makes the script service-agnostic while providing useful connection information

## Constraint 6 - Docker Compose Wrapper Commands

- Standard docker compose commands: `up`, `down`, `ps`, `logs`, `build`
- These do the standard docker compose operations with:
  - Nicely formatted output by calling docker compose with filtering params
  - Visual dividers showing which profile is being operated on
- `compose up` should auto-build if Dockerfile exists - let docker compose handle its default build behavior
- `compose build` command builds the images locally that have build setup configured
- The script should be true to docker compose behavior for known commands

## Constraint 7 - Custom Commands

- `clean` command has no docker compose equivalent - it stops containers and removes volumes and networks
- `clean` follows same profile behavior as other commands:
  - `compose clean` applies to default profile only
  - `compose clean all` applies to all profiles
  - `compose clean postgres` applies to postgres profile only
- **Clean command scope**: Removes volumes and networks defined in compose.yaml for the specified profile(s) - Docker Compose automatically manages which resources belong to the project
- `status` command prints out all key connection information developers need to connect to services from their apps or access them via UI (uses the `status.*` label system)
- `profiles` command lists all available profiles (convenience command, same category as status)
- The reserved "all" profile works with all commands: `compose status all`, `compose clean all`, etc.

## Constraint 8 - Script Behavior & Error Handling

- The compose script should fail fast on any errors
- The compose script only works with compose.yaml files that follow its own conventions
- Script should fail early if any of its conventions are violated:
  - Complain if `compose.yaml` is not found (only supported filename) - convention violation
  - Complain if `.env` file is not found - required by convention, same as missing compose.yaml
  - Complain if any services don't have profiles set (convention violation)
- For non-convention errors, let docker compose handle validation and show its error messages
- Keep the script simple: if docker compose supports features like `--env-file` for overrides, use those docker compose features rather than adding complexity to the script
- No assumptions about Docker Compose version requirements
- For the `status` command, print any labels that are prefixed with `status.*` to keep the script simple
- **Common Error Troubleshooting**: Detect common development errors (like port conflicts) and provide simple troubleshooting information without complex error handling logic
- **Scaled Container Instances Not Supported**: The script does not support scaled container instances (e.g., `docker compose up --scale service=2`) - this is an advanced feature beyond the scope of the current implementation

## Constraint 9 - Common Error Troubleshooting

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
6. **Ensures predictability** - Explicit profiles and fail-fast behavior
7. **Prioritizes explicitness over convenience** - Accepts repetitive YAML (`profiles: ["all", "default"]`) to avoid complex script logic

## Profile Configuration Philosophy

The system chooses **explicit over magic**:
- ✅ **Explicit**: Every service clearly shows which profiles it belongs to
- ✅ **Predictable**: What you see in compose.yaml is exactly what runs  
- ✅ **Simple Script**: No complex inheritance or profile logic in bash
- ❌ **Repetitive**: Most services need `profiles: ["all", "default"]`
- ❌ **Verbose**: More YAML to write

This trade-off keeps the script simple and maintainable while making service behavior completely transparent to developers.