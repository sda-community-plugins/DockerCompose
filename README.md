# Docker Compose plugin

The _Docker Compose_ plugin is a configuration management plugin. It is run during development and deployment process 
to orchestrate the creation and management of Docker containers through compose files. It interacts directly with the
[docker compose](https://docs.docker.com/compose/) program and parses compose files.

This plugin is a work in progress but it is intended to provide the following steps:

* [x] **Docker Compose Up** - Starts all of the applications services that are defined in compose file(s). It checks what 
services are defined in the provided compose files and adds any compose override files that exist for DA components of 
the same name.
* [x] **Docker Compose Down** - Stops all of the applications services that are defined in compose file(s).  
* [x] **Docker Compose Config** - Validates all of the applications services that are defined in compose file(s).  
* [x] **Docker Compose (Generic)** - Runs a generic docker compose command.  
* [x] **Create Compose Overrides** - Creates a compose override file for the specific component and version. The override
file will contain the name of the image (from the component) and version (from the component version).  
* [x] **List Services** - Lists the services defined in a compose file for selection and use in other steps.  
* [x] **Get Logs** - Retrieves the logs for a specified service in a docker-compose file.                                            
                                        
The plugin requires that both the [Docker Engine](https://docs.docker.com/engine/) and [docker compose](https://docs.docker.com/compose/) have been 
installed on the target server where the steps will be executed.

### Installing the plugin
 
Download the latest version from the _release_ directory and install into Deployment Automation from the 
**Administration\Automation\Plugins** page.

### Using the plugin

The plugin works by managing a central, templated `docker-compose.yml` file and then creating compose override files for
each of the component versions which are selected to be deployed. These map onto docker images so the component names
and version names will have to be the same as the images. To make use of the plugin the following approach is recommended.

 1. Create a DA application and components that map on to the services (docker images) that are to be deployed.
 2. Create an additional component, e.g. **"compose"** and store your `docker-compose.yml` in this component. 
    Note: you could store it as a versioned file or as a template in this component.
 3. Replace any hard-coded environment specific values in the `docker-compose.yml` file with *nix environment variables, e.g. ${HTTP_PORT}
 4. Add component environment variables to the **compose** component for each of these values.
 5. Add a component process to each of the components that invokes **Create Compose Overrides** so that a compose override
    file is created with the version of the component (docker image) to be deployed.
 6. Create a process in the **compose** component that downloads the `docker-compose.yml` file and replaces any environment
    specific values and then calls **Docker Compose Up**.
 7. Finally create an application process that calls the **Create Compose Overrides** step for each of the components and 
    then the invokes the component process containing the **Docker Compose Up** step to bring up all the services.

The plugin creates temporary scripts (containing the **docker-compose** commands that are executed). For troubleshooting
you can turn on "debugging" and select the option to not delete these files. These settings are all under "Hidden Properties" 
for each step. See the [examples](examples) directory for some example component and application processes..
         
### Building the plugin

To build the plugin you will need to clone the following repositories (at the same level as this repository):

 - [mavenBuildConfig](https://github.com/sda-community-plugins/mavenBuildConfig)
 - [plugins-build-parent](https://github.com/sda-community-plugins/plugins-build-parent)
 - [air-plugin-build-script](https://github.com/sda-community-plugins/air-plugin-build-script)
 
 and then compile using the following command
 ```
   mvn clean package
 ```  

This will create a _.zip_ file in the `target` directory when you can then install into Deployment Automation
from the **Administration\Automation\Plugins** page.

If you have any feedback or suggestions on this template then please contact me using the details below.

Kevin A. Lee

kevin.lee@microfocus.com

**Please note: this plugins is provided as a "community" plugin and is not supported by Micro Focus in any way**.
