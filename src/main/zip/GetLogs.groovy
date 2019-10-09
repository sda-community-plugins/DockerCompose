// --------------------------------------------------------------------------------
// Retrieves the logs for a specified service in a docker-compose file
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

final workdir = new File('.')
//
// Set a variable for each of the plugin steps's inputs.
// We can check whether a required input is supplied (the helper will fire an exception if not) and
// if it is of the required type.
//
File workDir = new File('.').canonicalFile
String composeFiles = props.optional('composeFiles', "docker-compose.yml")
String composeProjectName = props.optional('composeProjectName')
String composeOptions = props.optional('composeOptions')
String serviceName = props.notNull('serviceName')
String outputFile = props.optional("outputFile")
String commandPath = props.optional('commandPath', "docker-compose")
String envPropValues = props.optional("envPropValues")
Boolean showTimestamps = props.optionalBoolean("showTimestamps", false)
Integer logLines = props.optionalInt("logLines", 0)
Boolean saveScript = props.optionalBoolean("saveScript", false)
Boolean debug = props.optionalBoolean("debug", false)

println "----------------------------------------"
println "-- STEP INPUTS"
println "----------------------------------------"

println "Working directory: ${workDir.canonicalPath}"
println "Compose Files: ${composeFiles}"
println "Project Name: ${composeProjectName}"
println "Compose Options: ${composeOptions}"
println "Service Name: ${serviceName}"
println "Output File: ${outputFile}"
println "Command Path: ${commandPath}"
println "Show Timestamps: ${showTimestamps}"
println "Log Lines: ${logLines}"
println "Debug: ${debug}"
if (debug) { props.setDebugLoggingMode() }

println "----------------------------------------"
println "-- STEP EXECUTION"
println "----------------------------------------"

final String DOCKER_COMPOSE         = DockerUtils.findDockerComposeExecutable(commandPath)
final String DOCKER_COMPOSE_SUBCMD  = "logs"

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
    def serviceList = []
    composeFilePaths.each { path ->
        def composeFile = new File(path)
        if (!composeFile.exists()) {
            throw new RuntimeException("Could not find compose file, ${path}")
        }
        println("Loading ${composeFile}")
        def composeYml = yaml.load(composeFile.text)
        def composeFileFormat
        try {
            composeFileFormat = composeYml.version
            if ('2'.equals(composeFileFormat.toString()) || '3'.equals(composeFileFormat.toString())) {
                println("Found compose file format version ${composeFileFormat}")
                serviceList = composeYml.services
                println("Found services: ${serviceList}")
            }
        }
        catch (MissingPropertyException e) {
            println("Found compose file format version 1")
            serviceList = composeYml
            println("Found services: ${serviceList}")
        }
    }
    if (!serviceList.contains(serviceName))
        throw new StepFailedException("Service name \"${serviceName}\" not found in compose fiile(s)")

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

    composeArgs << DOCKER_COMPOSE_SUBCMD

    if (showTimestamps) {
        composeArgs << '-t'
    }
    if (logLines <= 0) {
        composeArgs << '--tail="all"'
    } else {
        composeArgs << '--tail="' + logLines + '"'
    }
    composeArgs << '--no-color'

    composeArgs << serviceName

    if (outputFile) {
        composeArgs << '>'
        composeArgs << outputFile
    }

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
        script = File.createTempFile("script", ".bat", workdir)

        // write the Environment Variables
        envVars.each { var ->
            script << "set ${var.getKey()}=${var.getValue()}\n"
        }

        args << 'cmd'
        args << '/C'
    }
    else {
        script = File.createTempFile("script", ".sh", workdir)

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

    println "----------------------------------------"
    println "-- STEP OUTPUTS"
    println "----------------------------------------"

    apTool.setOutputProperty("serviceName", serviceName)
    println "Setting \"serviceName\" output property to \"${serviceName}\""
    apTool.setOutputProperty("outputFile", outputFile)
    println "Setting \"outputFile\" output property to \"${outputFile}\""
    apTool.storeOutputProperties()

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

