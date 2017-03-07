import com.urbancode.air.AirPluginTool
import com.serena.air.plugin.docker.DockerUtils

import org.yaml.snakeyaml.Yaml

final apTool = new AirPluginTool(this.args[0], this.args[1])
final props = apTool.getStepProperties()

final composeFiles = props['composeFiles']?.trim()
final composeProjectName = props['composeProjectName']?.trim()
final verbose = props["verbose"]?.trim().toBoolean()
final debug = props["debug"]?.trim().toBoolean()

if (debug) {
    DockerUtils.debug("composeFiles=${composeFiles}")
    DockerUtils.debug("composeProjectName=${composeProjectName}")
}

Yaml yaml = new Yaml()

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
    DockerUtils.info("Loading ${composeFile}")
    def composeYml = yaml.load(composeFile.text)
    def composeFileFormat
    try {
        composeFileFormat = composeYml.version
        if ('2'.equals(composeFileFormat.toString())) {
            DockerUtils.info("Found compose file format version ${composeFileFormat}")
            serviceList = composeYml.services
            DockerUtils.info("Found services: ${serviceList}")
        }
    }
    catch (MissingPropertyException e) {
        DockerUtils.info("Found compose file format version 1")
        serviceList = composeYml
        DockerUtils.info("Found services: ${serviceList}")
    }
}


List<Properties> selectList = new ArrayList<Properties>()
serviceList.each { service ->
    Properties p = new Properties()
    p.put("Name", service.key)
    selectList.add(p)

}
apTool.setOutputProperty("OUTPUT_DATA", selectList)
apTool.storeOutputProperties()
System.exit(0)


