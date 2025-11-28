# Purbank

Setup in VScode:
- install Exteension Pack for Java from Microsoft or vscode team and Spring Boot Extension Pack by VMware

make sure java is set up in your vscode settings

To run:
- Go into vscode, open spring boot dashboard and click run

## OpenAPI 
run it, go to http://localhost:8080/swagger-ui

### PostgresSQL
A docker compose file is provided for easy development and testing. To use it just do:
cd dev-postgres
sudo docker-compose up -d
! More details in dev-postgres/SETUP.md

The DB is auto set up to just work with the application, no configuration needed. Just run it and you're ready to go.


### Email Service

The application sends emails to users throughout the registration process and for notifications:
- Email verification codes during mobile device registration
- Registration success confirmations
- System notifications

#### Configuration

**Default Setup (Development)**

The application is pre-configured to use Hilfiker Cloud Mail (`mail.hilfikernet.ch`) with the `no-reply@purbank.ch` account.

To enable email functionality, set the mail password as an environment variable:
```bash
export MAIL_PASSWORD="your-password-here"
```

**Development Credentials**

A test password for `no-reply@purbank.ch` is available at: https://cloud.hilfikers.com/f/1498293

#### Email Templates

HTML email templates are located in `src/main/resources/templates/email/`:
- `verification.html` - Email verification code
- `registration-success.html` - Welcome email after successful registration

Templates use Thymeleaf for dynamic content rendering.