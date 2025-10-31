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