# Compose Script Design Constraints

This document defines the design constraints for the `compose` script and associated Docker configuration system.

## Constraint 1 - File Structure & Dependencies

- There is only one script called `compose` at the root of the repo
- The compose script only works with `compose.yaml` (exact filename) and expects that file to be in the same location as the script itself. No other compose file variants are supported
- There is only one `compose.yaml` at the root of the repo, and developers cut and paste their containers into it
- At the same level as the compose script and compose.yaml, there is a folder called `docker`
- Under the `docker` folder, there is a directory for each service name that needs to be configured with files that might need to be mounted, etc.
- If a service is locally buildable, a Dockerfile can be found in that service's directory under `docker/`
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

## Constraint 4 - Profile System

- Every service in the compose file is required to have a profile explicitly set
- There are two reserved profile names by convention:
  - **"default"**: Must be explicitly set by developers for services they want in the default profile
  - **"all"**: Reserved meta-profile that means all available profiles (not set on services directly)
- When you type `compose up` (or any compose command without profile specification), it runs all services with the "default" profile
- When you type `compose up all`, it runs all services across all available profiles
- Space-separated profile names can be given for specific profile targeting
- `compose up postgres mcp` means run it on postgres and mcp profiles
- If users type `docker compose up` directly in the root (without using the script), nothing will start because no profiles are specified - this forces them to use the script or be explicit about profiles

## Constraint 5 - Service Labeling System

- The compose script functionality is completely generic - no service-specific functions
- Service connection info provided through labeling convention in YAML file
- Labels use environment variable interpolation from `.env`
- Label naming convention: `status.*` prefix for anything displayed by status command
  - `status.url.<url-type>` for different URL types (jdbc, ui, grpc, http, etc.)
  - `status.cred.<credential_type>` for credentials (username, password, api_key, etc.)
- The compose script uses `docker compose` to pull out labels for formatted output
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

- `clean` command has no docker compose equivalent - it cleans all volumes and networks associated with the compose file
- `clean` follows same profile behavior: if you run `clean` it applies to the default profile, otherwise it applies to whatever profiles you pass in
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

## Summary

These constraints define a simple, convention-based system that:

1. **Enforces explicit structure** - Fixed file locations and naming
2. **Promotes consistency** - Standardized environment variable and label naming
3. **Maintains simplicity** - Leverages docker compose features rather than reimplementing
4. **Enables generic functionality** - Service-agnostic through labeling conventions
5. **Provides developer experience** - Clear error messages and helpful commands
6. **Ensures predictability** - Explicit profiles and fail-fast behavior

The system is designed to be easy to understand, maintain, and extend while providing powerful functionality through well-defined conventions.