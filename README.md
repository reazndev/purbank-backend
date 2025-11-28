# Purbank

Setup in VScode:
- install Exteension Pack for Java from Microsoft or vscode team and Spring Boot Extension Pack by VMware

make sure java is set up in your vscode settings

To run:
- Go into vscode, open spring boot dashboard and click run

## OpenAPI 
run it, go to http://localhost:8080/swagger-ui

## PostgresSQL (when building manually)
A docker compose file is provided for easy development and testing. To use it just do:
cd dev-postgres
sudo docker-compose up -d
! More details in dev-postgres/SETUP.md

The DB is auto set up to just work with the application, no configuration needed. Just run it and you're ready to go.

## Run the full backend using Docker compose
For testing and development where building it yourself is not needed you can use the docker compose file provided. It is setup to pull from the latest successful build of the main branch. You can also use images from branches or PRs, see below for more details.

The easiest way to run the entire Purbank stack is using Docker Compose. This will start:

- PostgreSQL database
- PgAdmin (database management UI)
- Purbank backend API (latest main branch from GitHub Container Registry)

```bash
   export MAIL_PASSWORD="your-mail-password"
   sudo docker compose pull
   sudo docker compose up -d
```

NOTE: you need to authenticate using a github token to pull as the repo is currently private. See: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry (you only need to give read:packages permission)

## Docker builds
Docker images of the backend get automatically built for:
- the main branch (use `:main` and `:latest`)
- any other branch (use `:{branch-name}`)
- any PR with the preview label (use `:pr-{number}`)
- also version tags and specific tags as needed.

## Email Service

The application sends emails to users throughout the registration process and for notifications:
- Email verification codes during mobile device registration
- Registration success confirmations
- System notifications

### Configuration

**Default Setup (Development)**

The application is pre-configured to use Hilfiker Cloud Mail (`mail.hilfikernet.ch`) with the `no-reply@purbank.ch` account.

To enable email functionality, set the mail password as an environment variable:
```bash
export MAIL_PASSWORD="your-password-here"
```

**Development Credentials**

A test password for `no-reply@purbank.ch` is available at: https://cloud.hilfikers.com/f/1498293

### Email Templates

HTML email templates are located in `src/main/resources/templates/email/`:
- `verification.html` - Email verification code
- `registration-success.html` - Welcome email after successful registration

Templates use Thymeleaf for dynamic content rendering.
