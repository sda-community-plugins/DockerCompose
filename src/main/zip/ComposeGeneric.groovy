import com.urbancode.air.AirPluginTool
import com.serena.air.plugin.docker.DockerUtils
import com.urbancode.air.CommandHelper
import com.urbancode.air.ExitCodeException

final apTool = new AirPluginTool(this.args[0], this.args[1])
final props = apTool.getStepProperties()
final workdir = new File('.')

final composeFiles = props['composeFiles']?.trim()
final composeProjectName = props['composeProjectName']?.trim()
final composeOptions = props['composeOptions']?.trim()
final composeCommand = props['command']?.trim()
final scriptPaths = props["scriptPaths"]?.trim()
final commandPath = props['commandPath']?:"docker-compose"
final envPropValues = props["envPropValues"]?.trim()
final saveScript = props["saveScript"]?.trim()
final verbose = props["verbose"]?.trim().toBoolean()
final debug = props["debug"]?.trim().toBoolean()

if (debug) {
    DockerUtils.debug("composeFiles=${composeFiles}")
    DockerUtils.debug("composeProjectName=${composeProjectName}")
    DockerUtils.debug("composeOptions=${composeOptions}")
    DockerUtils.debug("command=${command}")
    DockerUtils.debug("scriptPaths=${scriptPaths}")
    DockerUtils.debug("commandPath=${commandPath}")
    DockerUtils.debug("envPropValues=${envPropValues}")
}

def ch = new CommandHelper(workdir)

def composeFilePaths = DockerUtils.toTrimmedList(composeFiles, '\n')
if (!composeFilePaths) {
    composeFilePaths << 'docker-compose.yml'
}

// compose arguments
final String DOCKER_COMPOSE = DockerUtils.findDockerComposeExecutable(commandPath)
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
composeArgs << composeCommand
composeArgs << '-d'

// environment properties
def envVars = [:]
if(envPropValues) {
    List tempVals = DockerUtils.toTrimmedList(envPropValues,'\n|,')
    tempVals.each{ it ->
        String[] parts = it.split("=", 2)*.trim()
        def propName = parts[0]
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

    // environment files
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
if (verbose) {
    DockerUtils.info("============ Script Contents ============")
    println script.getText()
}

try {
    ch.runCommand("Executing Compose ${command}...", args)
} catch (ExitCodeException ex) {
    DockerUtils.info("\n\n[Error] Unable to run docker-compose ${command}. Running the CLI's --help output for additional assistance.")
    DockerUtils.info("============ Compose --help Output ============")
    List<String> helpArgs = [DOCKER_COMPOSE, command, "--help"]
    ch.runCommand("Executing Compose ${command} --help", helpArgs)
    DockerUtils.info("\n============ Stack Trace Output ===============")
    ex.printStackTrace()
    System.exit(1)
}


