package com.serena.air.plugin.docker

import groovy.io.FileType

public class DockerUtils {

    /**
     * Check if a string is null, empty, or all whitespace
     * @param str The string whose value to check
     */
    public static boolean isEmpty(String str) {
        return (str == null) || str.trim().isEmpty();
    }

    /**
     *  @param list The list to trim whitespaces and remove null entries
     *  @param delimiter The string that separates each entry
     */
    public static def toTrimmedList(def list, String delimiter) {
        return list.split(delimiter).findAll{ it?.trim() }.collect{ it.trim() }
    }

    /**
     *  @param rawImageSpec The raw, unformatted image spec
     *  @return The specs image, registry and version of the image spec
     */
    public static def parseImageSpec(String rawImageSpec) {
        def result = new Expando()

        // qualified domain         registry.tld/org/namespace/image:1.0
        // -------- or --------     registry.tld/org/namespace/image
        // simple host and port     localhost:5000/namespace/image:1.0
        // -------- or --------     localhost:5000/namespace/image
        // implicit library         registry.tld/namespace/image:1.0
        // ------   or --------     registry.tld/namespace/image
        // no/implicit registry     namespace/image:1.0
        // ------   or --------     namesapce/image
        // implicit local library   image:1.0
        // ------   or --------     image
        // colon in repo spec       my:image:1.0
        // ------   or --------     my:image
        // Repo is everything before first slash, unless spec contains no dots and no colon before first slash.
        // If registry spec exists, remove it and trailing slash - called Repository Spec
        def registryImageArray = rawImageSpec.split("/", 2)

        //Break this string into a repo+tag and registry
        def repoAndTag
        def registrySpec
        def firstPart = registryImageArray[0]
        if (registryImageArray.length == 1 ) {
            //No slash - implicit registrySpec
            repoAndTag = firstPart
        }
        else if ( firstPart.contains(".") || firstPart.contains(":") ) {
            registrySpec = firstPart
            repoAndTag = registryImageArray[1]
        }
        else {
            repoAndTag = rawImageSpec
        }

        def imageSpec
        def versionSpec

        if (repoAndTag.contains(":")) {
            //is explicit version/tag
            def imageRefParts = repoAndTag.split(":")
            imageSpec = imageRefParts[0]
            versionSpec = imageRefParts[1]

        }
        else {
            //is implicit version
            imageSpec = repoAndTag
        }

        result.registry = registrySpec
        result.image = imageSpec
        result.version = versionSpec

        return result
    }

    /**
     *  @param input The file or directory of the docker-compose executable
     *  @return Canonical path String to the docker-compose executable
     */
    public static String findDockerComposeExecutable(String input) {
        return findDockerExecutable("docker-compose", input);
    }

    /**
     *  @param input The file or directory of the docker-compose executable
     *  @return Canonical path String to the docker-compose executable
     */
    public static String findDockerExecutable(String input) {
        return findDockerExecutable("docker", input);
    }

    /**
     *  @param executable The default exeutable docker/docker-compose to search for
     *  @param input The file or directory of the docker/docker-compose executable
     *  @return Canonical path String to the docker/docker-compose executable
     */
    public static String findDockerExecutable(String executable, String input) {
        String result = executable
        // If input is not the default executable value, continue
        if (input != executable) {
            File exe = new File(input)

            // If input is directory, look for the executable
            if (exe.isDirectory()) {
                boolean dcFound = false
                // Loop through all files looking for executable
                exe.eachFile(FileType.FILES) { file ->
                    if (file.getName().contains(executable)) {
                        dcFound = true
                    }
                }

                // Fail if executable is not found in directory
                if (!dcFound) {
                    throw new IOException("[ERROR] The specified folder '${input}' " +
                            "does not contain the ${executable} executable.")
                }

                // Construct path to executable
                result = exe.getCanonicalPath() + File.separator + executable

                // If input is file, confirm it's the executable
            } else if (exe.isFile()) {

                // If executable name does not exist in input file name, fail
                if (!exe.getName().contains(executable)) {
                    throw new FileNotFoundException("[ERROR] The specified file '${input}' " +
                            "is not a ${executable} executable.")
                }
                result = exe.getCanonicalPath()
            }

            // Fail if input is neither a file nor directory
            else {
                throw new IOException("[ERROR] The specified ${executable} input '${input}' does not exist.")
            }
        }

        // Escape spaces in the command path
        if (result.contains(" ")) {
            result = "\"" + result + "\""
        }

        DockerUtils.info("Using ${executable} executable: ${result}")
        return result
    }

    /**
     * @param proc The process to retrieve the standard output and standard error from
     * @return An array containing the standard output and standard error of the process
     */
    public String[] captureCommand(Process proc) {
        StringBuffer out = new StringBuffer()
        StringBuffer err = new StringBuffer()
        proc.waitForProcessOutput(out, err)
        proc.out.close()
        return [out.toString(), err.toString()]
    }

    // ----------------------------------------

    public static debug(String message) {
        println("[DEBUG] ${message}")
    }

    public static info(String message) {
        println("[INFO] ${message}")
    }

    public static error(String message) {
        println("[ERROR] ${message}")
    }
}
