# Drop JAR files here — they are auto-installed into the local Maven repo on compile.
# Local repo is checked FIRST; remote only used as fallback.

# Naming conventions:
#    <artifact>-<version>.jar
#        Uses default group: "local.dependency" (set local_repo_default_group in gradle.properties)
#        Example:  pigmentum-0.5.7.1.jar  →  local.dependency:pigmentum:0.5.7.1
#
#    <group>--<artifact>-<version>.jar
#        Double hyphen (--) separates the group from the artifact.
#        Example:  maven.modrinth--pigmentum-0.5.7.1.jar  →  maven.modrinth:pigmentum:0.5.7.1
#
#    ※ For version numbers that contain dashes (e.g. "1.21.1-neoforge-19.8.0"),
#      the LAST segment starting with a digit is treated as the version.
#      jei-1.21.1-neoforge-19.8.0.jar  →  artifact=jei-1.21.1-neoforge  version=19.8.0

# After dropping a JAR here, declare the dependency in build.gradle as usual:
#     implementation "maven.modrinth:pigmentum:0.5.7.1"
#
# Then just run ./gradlew build — the task autoInstallLocalDeps runs before compileJava.
