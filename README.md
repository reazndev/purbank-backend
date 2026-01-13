# Purbank
The Purbank Core Backend Server. It exposes its API under /api. Please refer to https://github.com/reazndev/purbank-meta for complete Installation Instructions for all required services.

Setup in VScode:
- Install Extension Pack for Java from Microsoft or vscode team and Spring Boot Extension Pack by VMware

Make sure Java is set up in your VSCode settings.

To run:
- Go into VSCode, open Spring Boot dashboard and click run

## OpenAPI
Run it, go to http://localhost:8080/swagger-ui

## Environment Configuration

### Creating the .env file

Before running the application, you need to create a `.env` file in the project root:

1. Copy the `.env.example` file to `.env`:
```bash
   cp .env.example .env
```

2. **REQUIRED**: Fill in these critical values:
    - `MAIL_PASSWORD` - Password for the no-reply@purbank.ch account
        - Development password available at: https://cloud.hilfikers.com/f/1498293
        - Note: The server will start just fine but you won't be able to send emails to users.
    - `JWT_SECRET_KEY` - A secure random string (minimum 32 characters)
        - Generate one with: `openssl rand -base64 32`

3. **OPTIONAL**: Customize other values as needed for your environment

### Environment Variables

The application uses environment variables for configuration. All variables have sensible defaults for development except:

**Required Variables:**
- `MAIL_PASSWORD` - Mail server authentication password
- `JWT_SECRET_KEY` - Secret key for JWT token signing (32+ characters)

See `.env.example` for a complete list with descriptions.

## PostgreSQL (when building manually)
A docker compose file is provided for easy development and testing. To use it:
```bash
docker compose up -d
```

The DB is auto set up to work with the application, no configuration needed. Just run it and you're ready to go.

## Run the full backend using Docker Compose

For testing and development where building it yourself is not needed, you can use the docker compose file provided. It is set up to pull from the latest successful build of the main branch.

### Prerequisites

1. Copy the docker-compose.yml file from the repo.
2. **Create a .env file** (see Environment Configuration section above)


### Starting the Stack

The Docker Compose setup will start:
- PostgreSQL database
- Purbank backend API (latest main branch from GitHub Container Registry)
```bash
docker compose pull
docker compose up -d
```

### Accessing Services

- Backend API: http://localhost:8080
- OpenAPI/Swagger: http://localhost:8080/swagger-ui
- PostgreSQL: localhost:5432

### Stopping the Stack
```bash
docker compose down
```

To also remove volumes (clears database):
```bash
docker compose down -v
```

## Docker Builds

Docker images of the backend are automatically built for:
- The main branch (use `:main` and `:latest`)
- Any other branch (use `:{branch-name}`)
- Any PR with the preview label (use `:pr-{number}`)
- Version tags and specific tags as needed

## Email Service

The application sends emails to users throughout the registration process and for notifications:
- Email verification codes during mobile device registration
- Registration success confirmations

### Configuration

**Default Setup (Development)**

The application is pre-configured to use Hilfiker Cloud Mail (`mail.hilfikernet.ch`) with the `no-reply@purbank.ch` account.

To enable email functionality, set the mail password in your `.env` file:
```bash
MAIL_PASSWORD=your-password-here
```

**Development Credentials**

A test password for `no-reply@purbank.ch` is available at: https://cloud.hilfikers.com/f/1498293

### Email Templates

HTML email templates are located in `src/main/resources/templates/email/`:
- `verification.html` - Email verification code
- `registration-success.html` - Welcome email after successful registration

Templates use Thymeleaf for dynamic content rendering.