# Project Dependencies - Docker Setup

This project's dependencies are managed centrally via Docker Compose in `compose.yaml`. The `compose` wrapper script saves you time and eliminates the need to remember longer Docker Compose commands.

---

## Section 1: Using the System 🚀

### Philosophy & Design

**Simple Development Experience**: One command starts all your dependencies (databases, observability stack, APIs) so you can focus on coding rather than infrastructure setup.

**Everything Together**: Rather than managing complex service selection, we start all dependencies together. Modern machines handle this easily, and it eliminates decision fatigue about "which services do I need?"

**Universal Access**: The compose script works from any directory in the repository - you don't need to navigate to the docker folder every time.

**Connection Information**: The `./compose info` command shows you all the URLs and credentials you need, grouped by service type with clickable links.

### Quick Start

```bash
# Start all project dependencies
compose up

# View connection URLs and credentials
compose info  

# Stop all dependencies
compose down
```

That's it! All dependencies are running and ready for development.

### What Services Are Available?

**Quick Overview**: Run `compose info` to see all connection URLs and credentials for available services.

**Detailed Information**: Each service directory contains a README.md with specific details for example:

- **`postgres/README.md`** - PostgreSQL database setup, Spring Boot integration, multi-database configuration
- **`observability/README.md`** - Grafana, Prometheus, Loki monitoring stack setup

These service READMEs explain what each service does, how to use them, and customization options.

### Requirements

- **Docker Desktop** - Running and accessible
- **jq** - JSON processor (`brew install jq` on macOS)
- **yq** - YAML processor (`brew install yq` on macOS)

### Essential Commands

The wrapper script commands mirror Docker Compose commands - they're just shorter versions that **work from any directory** in your repository.

| Wrapper Command | Description | Equivalent Docker Compose |
|----------------|-------------|---------------------------|
| `compose up` | Start all project dependencies | `cd docker && docker compose up -d` |
| **`compose info`** | Show connection URLs and credentials | *(custom feature)* |
| `compose down` | Stop all dependencies | `cd docker && docker compose down` |
| **`compose clean`** | Stop dependencies + remove volumes | `cd docker && docker compose down -v --remove-orphans` |
| `compose logs` | View dependency logs | `cd docker && docker compose logs` |
| `compose logs -f` | Follow dependency logs | `cd docker && docker compose logs -f` |
| `compose ps` | Show running containers | `cd docker && docker compose ps` |

#### Key Commands Explained

**`compose info`** - This is a custom command that groups your project dependencies and shows clickable connection URLs, credentials, and important endpoints. Run it after `compose up` to see what's available.

**`compose clean`** - Use this for a fresh start. It stops everything and removes all data volumes, giving you a completely clean slate for the next `compose up`.

### Making It Even Easier

The script already works from any directory in the repository, but you can make it even more convenient:

**Option 1: Add to your PATH**
```bash
export PATH="$PATH:/path/to/your/project/docker"
```

**Option 2: Use direnv (Recommended)**

[Direnv](https://direnv.net) is a tool that automatically loads environment variables and PATH modifications when you enter a directory. It's perfect for project-specific tooling.

1. **Install direnv**: `brew install direnv` or see [installation guide](https://direnv.net/docs/installation.html)
2. **Create `.envrc`** in your project root:
   ```bash
   # .envrc
   PATH_add docker
   ```
3. **Allow it**: `direnv allow`

Now `compose up` works from anywhere in your project without typing the full path.

### Environment Customization

#### Changing Default Ports

If you have port conflicts, create a `.env` file in the docker/ directory:

```bash
# docker/.env
PG_PORT=5432         # PostgreSQL (default: 15432)
PGADMIN_PORT=5433    # pgAdmin (default: 15433)
MCP_SSE_PORT=3001    # MCP SSE Server (default: 3001)
GRAFANA_PORT=8080    # Grafana (default: 3000)
```

#### Personal Overrides

Create `.env.local` (ignored by git) for personal settings:

```bash  
# docker/.env.local
PG_PORT=25432        # Avoid conflict with local PostgreSQL
GRAFANA_PORT=23000   # Avoid conflict with local Grafana
```

#### Precedence

Environment variables are loaded in this order:
1. **Shell environment** (highest priority)
2. **`.env.local`** (personal overrides)
3. **`.env`** (team defaults)  
4. **compose.yaml defaults** (lowest priority)

Example:
```bash
# Override just for this command
PG_PORT=5432 compose up
```

### FAQ

#### Can I run specific services only?

Yes, but use Docker Compose directly:
```bash
cd docker
docker compose up postgres              # Just PostgreSQL
docker compose up everything-sse        # Just MCP SSE server
docker compose up grafana prometheus    # Just monitoring
```

The wrapper script starts everything by design for simplicity.

#### How do I see what's running?

```bash
./compose info    # Connection information
./compose ps      # Container status
docker ps         # All containers on system
```

#### How do I update service configurations?

```bash
compose down         # Stop services
# Edit compose.yaml or service configs in docker/
compose up           # Restart with new config
```

#### What if I don't need all the services?

The philosophy is "start everything, ignore what you don't need." Modern machines handle extra containers well, and it's simpler than managing service selection.

If you really need selective startup, use `docker compose up <service-name>` directly.

### Spring Boot Integration ⚡

If you're building a **Spring Boot application**, this Docker setup provides seamless integration with Spring Boot's Docker Compose support.

#### What Spring Boot Does Automatically

When you add the `spring-boot-docker-compose` dependency to your Spring Boot project:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-docker-compose</artifactId>
    <optional>true</optional>
</dependency>
```

Spring Boot automatically:
- **Finds our compose.yaml** and starts the containers when your app starts
- **Auto-configures connections** to PostgreSQL, Redis, and other services
- **Creates connection beans** (`DataSource`, `RedisTemplate`, etc.) with the correct ports and credentials
- **Stops containers** when your application shuts down

#### Spring Boot Development Workflows

**Option 1: Let Spring Boot Manage Everything**
```bash
# Spring Boot automatically starts containers and connects to them
mvn spring-boot:run
# or
./gradlew bootRun
```

**Option 2: Manual Container Management**
```bash
# Start containers first, then run your app
compose up
mvn spring-boot:run    # Spring Boot detects running containers
# Your app connects automatically, containers keep running after app stops
```

**Option 3: Hybrid Approach**
```bash
compose up           # Start dependencies
mvn spring-boot:run  # Spring Boot uses existing containers
compose info         # See all connection details
compose down         # Clean shutdown when done
```

#### Configuration in Your Spring Boot App

**Point to our compose file** (if your app isn't in the repository root):
```yaml
# src/main/resources/application.yaml
spring:
  docker:
    compose:
      file: "docker/compose.yaml"
```

**That's it!** No manual database URLs, Redis configuration, or connection strings needed. Spring Boot discovers everything from the running containers.

#### What This Means for Spring Boot Developers

✅ **Zero Configuration** - No database URLs or connection strings in your application.properties  
✅ **Automatic Service Discovery** - Spring Boot finds PostgreSQL on port 15432, connects automatically  
✅ **Consistent Environments** - Same container setup works across your entire team  
✅ **Fast Development** - Start coding immediately, dependencies are handled  
✅ **Best Practices** - Health checks, proper networking, and observability built-in

---

## Section 2: Adding Services & Customizing 🔧

### Adding New Services

#### 1. Add Service to compose.yaml

```yaml
services:
  my-service:
    image: nginx:alpine
    ports:
      - "${MY_SERVICE_PORT:-8080}:80"
    labels:
      - "info.group=Web Services"
      - "info.title=My Service"
      - "info.url.ui=http://localhost:${MY_SERVICE_PORT:-8080}"
```

#### 2. Add Default Environment Variables

```bash
# Add to docker/.env
MY_SERVICE_PORT=8080
```

#### 3. Test Your Service

```bash
./compose up
./compose info    # Your service appears grouped under "Web Services"
```

### Labels Convention

The system uses Docker Compose labels to provide connection information through the `compose info` command. All services should include these labels:

#### Required Labels
- **`info.title`** - Human-readable service name (REQUIRED for service to appear in `compose info`)

#### Optional Labels
- **`info.group`** - Groups services in the output (e.g., "Database Services", "API Services", "Web Services")
- **`info.url.<type>`** - Connection URLs with descriptive type names:
  - `info.url.ui` - Web interface URL
  - `info.url.api` - API endpoint URL
  - `info.url.jdbc` - Database connection string
  - `info.url.grpc` - gRPC endpoint
  - Custom types as needed
- **`info.cred.<type>`** - Credential information:
  - `info.cred.username` - Username
  - `info.cred.password` - Password
  - `info.cred.api_key` - API key
  - Custom credential types as needed

#### Complete Service Example

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

### Directory Structure & File Organization

#### Project Structure

```
your-project/
├── docker/                  # Self-contained Docker setup
│   ├── compose              # Universal wrapper script
│   ├── compose.yaml         # All service definitions
│   ├── .env                 # Team default environment variables (optional)
│   ├── .env.local          # Personal overrides (gitignored, optional)
│   ├── postgres/           # PostgreSQL-specific configurations
│   │   ├── init.sql        # Database initialization
│   │   └── pgadmin_servers.json
│   ├── observability/      # Monitoring stack configurations
│   │   ├── config/         # Service config files
│   │   └── grafana/        # Grafana-specific files
│   └── [service-type]/     # Other service directories
├── src/                    # Your application source code
└── README.md
```

#### Service-Based Organization

- **Group by function**: Organize service configurations by their logical function (databases, APIs, monitoring, etc.)
- **Self-contained directories**: Each service type gets its own directory under docker/
- **Mount configuration files**: Reference service configs from their directories using relative paths in compose.yaml
- **Named volumes**: Use Docker named volumes for persistent data rather than bind mounts when possible

### Environment Variable Conventions

Follow these naming patterns for consistency:

#### Port Variables
- **Single port**: `<SERVICE_NAME>_PORT` (e.g., `PG_PORT=15432`, `GRAFANA_PORT=3000`)
- **Multiple ports**: `<SERVICE_NAME>_PORT_<PORT_NAME>` (e.g., `MCP_INSPECTOR_WS_PORT=6277`)

#### Image Variables
- **Format**: `<SERVICE_NAME>_IMAGE` (e.g., `POSTGRES_IMAGE=postgres:17`, `GRAFANA_IMAGE=grafana/grafana:12.0.0`)

#### Credential Variables
- **Format**: `<SERVICE_NAME>_CRED_<TYPE>` 
- **Examples**: `POSTGRES_CRED_USERNAME`, `GRAFANA_CRED_PASSWORD`, `API_CRED_TOKEN`

#### Usage in compose.yaml
```yaml
services:
  my-service:
    image: ${MY_SERVICE_IMAGE:-nginx:alpine}
    ports:
      - "${MY_SERVICE_PORT:-8080}:80"
    environment:
      USERNAME: ${MY_SERVICE_CRED_USERNAME:-admin}
      PASSWORD: ${MY_SERVICE_CRED_PASSWORD:-password}
```

### Using in Other Projects

This Docker Compose wrapper is designed to be universal and can be dropped into any project.

#### Installation for New Projects

1. **Copy the docker/ folder** to your project root
2. **Make executable**: `chmod +x docker/compose`  
3. **Customize compose.yaml** for your project's services
4. **Install requirements**: `jq` and `yq`

#### Best Practices for New Projects

- **Keep it simple**: Start everything, no complex service selection
- **Use info labels**: Always add `info.group` and `info.title` to services
- **Document connections**: Include URLs and credentials in labels  
- **Environment defaults**: Use `${VAR:-default}` syntax in compose.yaml
- **Test universally**: Ensure `compose up` works from any directory

---

## Section 3: System Design & Deep Dive 🏗️

### Design Philosophy

This system is built on the principle of **convention over configuration** with two distinct user personas:

#### Persona 1: Script Users (Majority of Developers)
- **Goal**: Simple experience - checkout repo, start dependencies, get coding
- **Knowledge**: Basic Docker Compose understanding
- **Usage**: Run `compose up`, `compose info`, follow established patterns
- **Responsibility**: Use the system, don't modify it

#### Persona 2: Script & Convention Maintainers (Few Developers per Team) 
- **Goal**: Establish and maintain team development infrastructure standards
- **Knowledge**: Advanced Docker Compose, system design, DevOps orientation
- **Usage**: Design conventions, modify the compose script, troubleshoot violations
- **Responsibility**: Make the system work for Persona 1

### Core Design Constraints

#### Constraint 1: File Structure & Dependencies
- **Single script**: Only one `compose` script in docker/ directory
- **Single compose file**: Only `compose.yaml` (exact filename) in docker/ directory
- **Required dependencies**: `docker`, `yq`, and `jq` must be available in PATH
- **Service-based directories**: Optional subdirectories organized by service type
- **No enforcement**: Script doesn't enforce directory structure - it's convention only

#### Constraint 2: Environment Loading
- **Optional .env**: compose.yaml has built-in defaults using `${VAR:-default}` syntax
- **Precedence order**: shell environment > `.env.local` > `.env` > compose.yaml defaults
- **Shell source loading**: Uses `set -a` / `source` / `set +a` pattern
- **Simple onboarding**: `git clone && cd docker && ./compose up` works immediately

#### Constraint 3: Service Management
- **All services together**: `compose up` starts everything, no selective management
- **Docker Compose for selection**: Use `docker compose up <service>` for specific services
- **Wrapper convenience**: Script provides convenient commands for common all-service operations

#### Constraint 4: Labels System
- **Generic functionality**: Script is completely service-agnostic
- **Convention-based info**: Service connection info provided through `info.*` labels
- **Label extraction**: Script uses `docker compose config` to extract label information
- **Required for visibility**: Services need `info.title` label to appear in `compose info`

#### Constraint 5: Universal Access
- **Script location detection**: Uses `${BASH_SOURCE[0]}` and symlink resolution
- **Relative file operations**: All file lookups relative to script location
- **Works from anywhere**: Can be executed from any directory in repository
- **Environment loading**: `.env` files loaded relative to script location

### Script Behavior & Error Handling

#### Fast Failure
- **Convention violations**: Fail immediately if compose.yaml not found
- **Docker Compose errors**: Let Docker Compose handle validation and show its errors
- **Simple troubleshooting**: Detect common issues (port conflicts) with helpful information
- **No complex logic**: Keep error handling minimal and focused

#### Commands

**Standard Docker Compose Commands**: `up`, `down`, `ps`, `logs`, `build`
- Mirror standard Docker Compose behavior with formatted output
- `up` includes auto-build behavior and port conflict detection

**Custom Commands**:
- **`clean`**: Stops containers and removes volumes (`docker compose down -v --remove-orphans`)
- **`info`**: Shows grouped service connection information using `info.*` labels

---

**Ready to get started?** Run `compose up` and then `compose info` to see your services!
