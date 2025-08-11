# PostgreSQL Docker Configuration

This directory contains configuration files for the PostgreSQL service used in our Docker Compose setup.

## How PostgreSQL Container Initialization Works

The official PostgreSQL Docker image has specific initialization behavior that's important to understand:

### Environment Variables Control Database Creation

The PostgreSQL container automatically creates databases based on environment variables:

```yaml
postgres:
  environment:
    POSTGRES_USER: "postgres"       # Creates superuser (default: postgres)
    POSTGRES_PASSWORD: "password"   # Required - sets superuser password
    POSTGRES_DB: "dev"              # Creates database named 'dev'
```

**Key Point**: If `POSTGRES_DB` is not specified, PostgreSQL creates a database with the same name as `POSTGRES_USER`.

### Initialization Order

When the container starts with an empty data directory:

1. **PostgreSQL initdb**: Creates the database specified in `POSTGRES_DB`
2. **Init scripts**: Runs any `*.sql` files from `/docker-entrypoint-initdb.d/`
3. **Service starts**: PostgreSQL is ready to accept connections

### Spring Boot Integration

Spring Boot's Docker Compose support automatically:
- Detects PostgreSQL containers by image name (`postgres`, `bitnami/postgresql`)
- Reads environment variables to determine connection details
- Connects to the database specified in `POSTGRES_DB`

## Database Setup

Our PostgreSQL configuration creates multiple databases:

### Primary Database: `dev` (Automatic)
- **Created by**: `POSTGRES_DB` environment variable
- **Purpose**: Main application database
- **Spring Boot**: Automatically connects here
- **No configuration needed**: Works out of the box

### Additional Database: `test` (Via Script)
- **Created by**: `init.sql` initialization script  
- **Purpose**: Example of creating additional databases
- **Use cases**: Testing, multiple services, data separation

## Files in This Directory

### `init.sql`
Initialization script that demonstrates creating **additional** databases beyond the main one.

**Important**: The main `dev` database is created automatically by `POSTGRES_DB: "dev"`. This script shows how to create extra databases when you need multiple databases in the same PostgreSQL instance.

### `pgadmin_servers.json`
Pre-configures pgAdmin with connection settings for our PostgreSQL instance.

## Environment Variables Reference

| Variable | Purpose | Default | Required |
|----------|---------|---------|----------|
| `POSTGRES_USER` | Superuser name | `postgres` | No |
| `POSTGRES_PASSWORD` | Superuser password | - | **Yes** |
| `POSTGRES_DB` | Default database name | `$POSTGRES_USER` | No |
| `PGDATA` | Data directory location | `/var/lib/postgresql/data` | No |

## Connection Details

### Main Application Database (`dev`)
- **Database**: `dev` (Spring Boot connects here automatically)
- **Username**: `postgres`  
- **Password**: `password`
- **Host**: `localhost` (from host machine)
- **Port**: `15432` (mapped from container port 5432)

### Additional Test Database (`test`)
- **Database**: `test` (for manual connections/testing)
- **Connection details**: Same as above, just change database name

## Customization

### Changing the Database Name

To use a different database name:

1. Update `POSTGRES_DB` in `compose.yaml`:
   ```yaml
   environment:
     POSTGRES_DB: "myapp"
   ```

2. Update `init.sql` to match:
   ```sql
   CREATE DATABASE myapp
   ```

3. Update service labels in `compose.yaml`:
   ```yaml
   labels:
     - "info.url.jdbc=jdbc:postgresql://localhost:${PG_PORT:-15432}/myapp"
   ```

### Adding More Databases

To add additional databases beyond `dev` and `test`, edit `init.sql`:

```sql
-- Add more databases as needed
CREATE DATABASE analytics
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;
```

### Additional Initialization Scripts

Add more `*.sql` files to this directory and mount them in `compose.yaml`:

```yaml
volumes:
  - ./postgres/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
  - ./postgres/users.sql:/docker-entrypoint-initdb.d/02-users.sql:ro
```

Scripts run in alphabetical order, so use prefixes like `01-`, `02-` to control execution sequence.

## Important Notes

- **One-time execution**: Init scripts only run when the container starts with an empty data directory
- **No re-runs**: If initialization fails, you must remove the volume and restart to re-run scripts
- **Order matters**: Scripts execute alphabetically by filename
- **User context**: Scripts run as the `POSTGRES_USER` (superuser)

## Troubleshooting

### "Database already exists" errors
This is normal and harmless when both `POSTGRES_DB` and `init.sql` create the same database.

### Scripts not running
Init scripts only run on first startup with empty data. To force re-initialization:
```bash
docker compose down -v  # Removes volumes
docker compose up       # Fresh start
```

### Connection refused
Ensure the container is fully started. PostgreSQL takes a few seconds to accept connections after the container starts.

## References

- [PostgreSQL Official Docker Image](https://hub.docker.com/_/postgres)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Spring Boot Docker Compose Support](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.docker-compose)