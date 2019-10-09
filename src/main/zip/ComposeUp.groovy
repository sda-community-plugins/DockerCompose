// --------------------------------------------------------------------------------
// Starts all of the applications services that are defined in compose file(s)
// (docker-composer up). It checks what services are defined in the provided compose
// files and adds any compose override files that exist for DA components of the same name.
// --------------------------------------------------------------------------------

import com.serena.air.plugin.docker.*

import com.urbancode.air.AirPluginTool
import com.serena.air.StepFailedException
import com.serena.air.StepPropertiesHelper
import com.urbancode.air.CommandHelper
import com.urbancode.air.ExitCodeException

import org.yaml.snakeyaml.Yaml

//
// Create some variables that we can use throughout the plugin step.
// These are mainly for checking what operating system we are running on.
//
final def PLUGIN_HOME = System.getenv()['PLUGIN_HOME']
final String lineSep = System.getProperty('line.separator')
final String osName = System.getProperty('os.name').toLowerCase(Locale.US)
final String pathSep = System.getProperty('path.separator')
final boolean windows = (osName =~ /windows/)
final boolean vms = (osName =~ /vms/)
final boolean os9 = (osName =~ /mac/ && !osName.endsWith('x'))
final boolean unix = (pathSep == ':' && !vms && !os9)

//
// Initialise the plugin tool and retrieve all the properties that were sent to the step.
//
final def  apTool = new AirPluginTool(this.args[0], this.args[1])
final def  props  = new StepPropertiesHelper(apTool.getStepProperties(), true)

//
// Set a variable for each of the plugin steps's inputs.
// We can check whether a required input is supplied (the helper will fire an exception if not) and
// if it is of the required type.
//
File workDir = new File('.').canonicalFile
String composeFiles = props.optional('composeFiles', "docker-compose.yml")
String composeProjectName = props.optional('composeProjectName')
String composeOptions = props.optional('composeOptions')
String scriptPaths = props.optional("scriptPaths")
String commandPath = props.optional('commandPath', "docker-compose")
Boolean runDetached = props.optionalBoolean("runDetached", true)
String envPropValues = props.optional("envPropValues")
Boolean saveScript = props.optionalBoolean("saveScript", false)
Boolean debug = props.optionalBoolean("debug", false)

println "----------------------------------------"
println "-- STEP INPUTS"
println "----------------------------------------"

println "Working directory: ${workDir.canonicalPath}"
println "Compose Files: ${composeFiles}"
println "Project Name: ${composeProjectName}"
println "Compose Options: ${composeOptions}"
println "Script Paths: ${scriptPaths}"
println "Command Path: ${commandPath}"
println "Debug: ${debug}"
if (debug) { props.setDebugLoggingMode() }

println "----------------------------------------"
println "-- STEP EXECUTION"
println "----------------------------------------"

final String DOCKER_COMPOSE         = DockerUtils.findDockerComposeExecutable(commandPath)
final String DOCKER_COMPOSE_SUBCMD  = "up"

def exitCode = 0

Yaml yaml = new Yaml()

def ch = new CommandHelper(workDir)

//
// The main body of the plugin step - wrap it in a try/catch statement for handling any exceptions.
//
try {
    def composeFilePaths = DockerUtils.toTrimmedList(composeFiles, '\n')
    if (!composeFilePaths) {
        composeFilePaths << 'docker-compose.yml'
    }
    def allServiceList = []
    composeFilePaths.each { path ->
        def composeFile = new File(path)
        if (!composeFile.exists()) {
            throw new StepFailedException("Could not find compose file, ${path}")
        }
        println "Loading ${composeFile}"
        def composeYml = yaml.load(composeFile.text)
        def composeFileFormat
        try {
            composeFileFormat = composeYml.version
            if ('2'.equals(composeFileFormat.toString()) || '3'.equals(composeFileFormat.toString())) {
                println "Found compose file format version ${composeFileFormat}"
                serviceList = composeYml.services
                println "Found services: ${serviceList}"
                allServiceList.add(serviceList)
            }
        }
        catch (MissingPropertyException e) {
            println "Found compose file format version 1"
            serviceList = composeYml
            println "Found services: ${serviceList}"
            allServiceList.add(serviceList)
        }
    }

    println "Found all services: ${allServiceList}"

    serviceList.each { service ->
        // add overrides for each service (if file exists)
        def overridesFiles = new File(".." + File.separatorChar + service.key +
                File.separatorChar + service.key + "-overrides.yml")
        if (!overridesFiles.exists()) {
            println "Could not find compose overrides file, ${overridesFiles.path}, ignoring..."
        } else {
            println "Adding overrides file: ${overridesFiles.path}"
            composeFilePaths << "${overridesFiles.path}"
        }
    }

    // compose config arguments
    List<String> composeArgs = [DOCKER_COMPOSE]
    if (composeOptions) {
        composeArgs << composeOptions
    }
    if (composeProjectName) {
        composeArgs << '-p'
        composeArgs << composeProjectName
    }
    composeFilePaths.each { path ->
        composeArgs << '-f'
        composeArgs << path
    }
    composeArgs << '--no-ansi'
    composeArgs << DOCKER_COMPOSE_SUBCMD
    if (runDetached) composeArgs << '-d'
    composeArgs << '--no-color'

    // environment properties
    def envVars = [:]
    if(envPropValues) {
        List tempVals = DockerUtils.toTrimmedList(envPropValues,'\n|,')
        tempVals.each{ it ->
            String[] parts = it.split("=", 2)*.trim()
            // replace "." with "_" for valid variable name
            def propName = (parts[0].contains(".") ? parts[0].replace(".","_") : parts[0])
            def propValue = parts.size() == 2 ? parts[1] : ''
            envVars.put(propName, propValue)
        }
    }

    // command helper arguments
    def args = []
    File script
    final def isWindows = System.getProperty('os.name').contains('Windows')
    if (isWindows) {
        script = File.createTempFile("script", ".bat", workDir)

        // add the execution of any extra scripts
        scriptPaths.each { filePath ->
            def envScript = new File(filePath)
            if (envScript.exists()) {
                envScript.setExecutable(true)
                script << "source \"${filePath}\"\n"
            }
            else {
                throw new RuntimeException("Could not find env file at ${filePath}")
            }
        }

        // write the Environment Variables
        envVars.each { var ->
            script << "set ${var.getKey()}=${var.getValue()}\n"
        }

        args << 'cmd'
        args << '/C'
    }
    else {
        script = File.createTempFile("script", ".sh", workDir)

        // add the execution of any extra scripts
        scriptPaths.each { filePath ->
            def envScript = new File(filePath)
            if (envScript.exists()) {
                envScript.setExecutable(true)
                script << "source \"${filePath}\"\n"
            }
            else {
                throw new RuntimeException("Could not find env file at ${filePath}")
            }
        }

        // write the Environment Variables
        envVars.each { var ->
            script << "export ${var.getKey()}=\"${var.getValue()}\"\n"
        }
    }

    if (!saveScript) {
        script.deleteOnExit()
    }
    script.setExecutable(true)

    args << script.getCanonicalPath()

    script << composeArgs.join(' ')

    // print script contents
    if (debug) {
        println "----------------------------------------"
        println "Generated script contents:"
        println "----------------------------------------"
        println script.getText()
        println "----------------------------------------"

    }

    ch.runCommand("Executing docker-compose ${DOCKER_COMPOSE_SUBCMD}...", args)

} catch (ExitCodeException e) {
    println "----------------------------------------"
    println "ERROR -  Unable to run docker-compose ${DOCKER_COMPOSE_SUBCMD}."
    println "Running the CLI's --help output for additional assistance."
    println "----------------------------------------"
    List<String> composeArgs = [DOCKER_COMPOSE]
    composeArgs << DOCKER_COMPOSE_SUBCMD
    composeArgs << '--help'
    ch.runCommand("Executing docker-compose ${DOCKER_COMPOSE_SUBCMD} --help", composeArgs)
    println "----------------------------------------"
    if (debug) {
        println "Stack trace:"
        e.printStackTrace()
        println "----------------------------------------"
    }
    System.exit 1
} catch (StepFailedException e) {
    //
    // Catch any exceptions we find and print their details out.
    //
    println "ERROR - ${e.message}"
    // An exit with a non-zero value will be deemed a failure
    System.exit 1
}

//
// An exit with a zero value means the plugin step execution will be deemed successful.
//
System.exit(exitCode)
