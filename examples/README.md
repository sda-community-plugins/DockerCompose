# Docker Compose Example Application

This directory contains the component and application processes for deploying the
 [Ring2Park Microservices](https://github.com/ring2park-microservices) application
 using **docker-compose**.

Files
-----
 - [docker.json](docker.json) - component template for deploying docker images
 - [eureka-service.json](eureka-service.json) - registration service component
 - [locations-service.json](eureka-service.json) -  locations service component
 - [users-service](users-service.json) - users service component
 - [web-ui](web-ui.json) - web user interface component
 - [compose](compose.json) - component containing docker-compose processes
 - [Ring2Park Microservices](Ring2Park%20Microservices.json) - application containing all components
 
You can either load all of the individual components or the application. You will need to have installed
the docker component template first. The [compose](compose.json) component contains an
example `docker-compose.yml` file as a template. 
