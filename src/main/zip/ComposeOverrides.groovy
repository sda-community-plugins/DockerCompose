// --------------------------------------------------------------------------------
// Create a compose override file for the specific component and version. The override
// file will contain the name of image (from the component) and version (from the component version).
// --------------------------------------------------------------------------------

import com.serena.air.plugin.docker.*

import com.urbancode.air.AirPluginTool
import com.serena.air.StepFailedException
import com.serena.air.StepPropertiesHelper

import org.yaml.snakeyaml.DumperOptions
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
String composeFile = props.notNull('composeFile')
String registryServer = props.optional('registryServer')
String componentName =  props.notNull('componentName')
String componentVersion = props.notNull('componentVersion')
Boolean debug = props.optionalBoolean("debug", false)


println "----------------------------------------"
println "-- STEP INPUTS"
println "----------------------------------------"

println "Working directory: ${workDir.canonicalPath}"
println "Compose File: ${composeFile}"
println "Registry Server: ${registryServer}"
println "Component Name: ${componentName}"
println "Component Version: ${componentVersion}"
println "Debug: ${debug}"
if (debug) { props.setDebugLoggingMode() }

println "----------------------------------------"
println "-- STEP EXECUTION"
println "----------------------------------------"

def exitCode = 0
def overrides = [:]
def servicesYaml
def composeOverrideFile

DumperOptions options = new DumperOptions();
options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
Yaml yaml = new Yaml(options);

//
// The main body of the plugin step - wrap it in a try/catch statement for handling any exceptions.
//
try {
    String version
    if (registryServer) {
        if (!registryServer.endsWith("/")) registryServer.concat("/")
        version = "${registryServer}${componentName}:${componentVersion}"
    }
    else {
        version = "${componentName}:${componentVersion}"
    }
    overrides.put(componentName, ['image' : version])
    servicesYaml = yaml.dump(['version': '3', 'services': overrides])

    if (composeFile) {
        composeOverrideFile = new File(componentName+'-overrides.yml')
    } else {
        composeOverrideFile = new File('da-version-overrides.yml')
    }
    if (composeOverrideFile.exists()) {
        composeOverrideFile.delete()
    }
    println "Creating compose overrides file: ${composeOverrideFile.toString()}"

    // print script contents
    if (debug) {
        println "----------------------------------------"
        println "Generated script contents:"
        println "----------------------------------------"
        println servicesYaml.toString()
        println "----------------------------------------"

    }
    composeOverrideFile << servicesYaml

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

