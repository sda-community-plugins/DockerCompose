import com.urbancode.air.AirPluginTool
import com.serena.air.plugin.docker.DockerUtils

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

final apTool = new AirPluginTool(this.args[0], this.args[1])
final props = apTool.getStepProperties()

final composeFile = props['composeFile']?.trim()
final componentName = props['componentName']?.trim()
final componentVersion = props['componentVersion']?.trim()
final verbose = props["verbose"]?.trim().toBoolean()
final debug = props["debug"]?.trim().toBoolean()

if (debug) {
    DockerUtils.debug("composeFile=${composeFile}")
    DockerUtils.debug("componentName=${componentName}")
    DockerUtils.debug("componentVersion=${componentVersion}")
}

def overrides = [:]
def servicesYaml
def composeOverrideFile

DumperOptions options = new DumperOptions();
options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
Yaml yaml = new Yaml(options);

overrides.put(componentName, ['image' : componentVersion])
servicesYaml = yaml.dump(['version': '2', 'services': overrides])

if (composeFile) {
    composeOverrideFile = new File(componentName+'-overrides.yml')
} else {
    composeOverrideFile = new File('da-version-overrides.yml')
}
if (composeOverrideFile.exists()) {
    composeOverrideFile.delete()
}
DockerUtils.info("Creating compose overrides file: ${composeOverrideFile.toString()}")
DockerUtils.info("============ Script Contents ============")
println servicesYaml.toString()
composeOverrideFile << servicesYaml

