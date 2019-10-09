// --------------------------------------------------------------------------------
// Lists the services defined in a docker-compose file for selection and use in other steps
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
Boolean debug = props.optionalBoolean("debug", false)

println "----------------------------------------"
println "-- STEP INPUTS"
println "----------------------------------------"

println "Working directory: ${workDir.canonicalPath}"
println "Compose Files: ${composeFiles}"
println "Project Name: ${composeProjectName}"
println "Project Name: ${composeProjectName}"
if (debug) { props.setDebugLoggingMode() }

println "----------------------------------------"
println "-- STEP EXECUTION"
println "----------------------------------------"

def exitCode = 0

Yaml yaml = new Yaml()

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


    List<Properties> selectList = new ArrayList<Properties>()
    serviceList.each { service ->
        Properties p = new Properties()
        p.put("Name", service.key)
        selectList.add(p)

    }

    println "----------------------------------------"
    println "-- STEP OUTPUTS"
    println "----------------------------------------"

    apTool.setOutputProperty("OUTPUT_DATA", selectList)
    println "Setting \"OUTPUT_DATA\" output property to \"${selectList}\""
    apTool.storeOutputProperties()

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


