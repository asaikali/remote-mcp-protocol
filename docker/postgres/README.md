# PostgreSQL Docker Configuration

Configuration files for the PostgreSQL service in our Docker Compose setup.

## Database Setup

**Primary Database: `dev`** - Created automatically by `POSTGRES_DB` environment variable. Spring Boot connects here by default.

**Additional Database: `test`** - Created by `init.sql` script as an example of adding extra databases.

## How It Works

PostgreSQL container initialization:
1. Creates database specified in `POSTGRES_DB` (our main `dev` database)
2. Runs `*.sql` files from `/docker-entrypoint-initdb.d/` (creates additional databases)
3. Spring Boot auto-detects and connects to the `dev` database

## Spring Boot Service Connections

Spring Boot automatically configures database connections when using Docker Compose:

**Auto-Detection**: Spring Boot scans running containers and recognizes PostgreSQL by image name (`postgres`, `bitnami/postgresql`).

**Automatic Configuration**: Creates `JdbcConnectionDetails` and `R2dbcConnectionDetails` beans by reading container environment variables:
- `POSTGRES_USER` → database username
- `POSTGRES_PASSWORD` → database password  
- `POSTGRES_DB` → database name
- Container host/port → connection URL

**Zero Configuration**: No need to configure `spring.datasource.*` properties in `application.yaml`. Spring Boot handles everything automatically.

**What This Means**:
- ✅ **No manual JDBC setup required** - Spring Boot auto-configures DataSource
- ✅ **No connection strings in config** - Extracted from container environment
- ✅ **Works out of the box** - Just run `docker compose up` then start your app
- ✅ **Development-only feature** - Disabled in production builds

**Requires**: `spring-boot-docker-compose` dependency in your `pom.xml`/`build.gradle`.

## Files

- **`init.sql`** - Creates additional `test` database. Shows pattern for multiple databases.
- **`pgadmin_servers.json`** - Pre-configures pgAdmin connection settings.

## Connection Details

- **Host**: `localhost`, **Port**: `15432`
- **Username**: `postgres`, **Password**: `password`
- **Main Database**: `dev` (Spring Boot connects here)
- **Test Database**: `test` (for manual connections)

## Customization

**Add more databases** - Edit `init.sql`:
```sql
CREATE DATABASE analytics WITH OWNER = postgres;
```

**Change main database** - Update `POSTGRES_DB` in `compose.yaml` and service labels.

## Key Environment Variables

- `POSTGRES_DB: "dev"` - Main database (Spring Boot connects here)
- `POSTGRES_USER: "postgres"` - Superuser name
- `POSTGRES_PASSWORD: "password"` - Required password

## Troubleshooting

**Scripts not running?** Init scripts only run on first startup. Force re-run: `docker compose down -v && docker compose up`

**Connection refused?** Wait a few seconds for PostgreSQL to fully start.