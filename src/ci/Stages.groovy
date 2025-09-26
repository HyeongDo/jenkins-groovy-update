package ci

class Stages {
    static def checkout(steps, Map config) {
        def checkoutInfo = new Checkout(steps).run(config)
        config.checkoutInfo = checkoutInfo
    }

    static def sshKey(steps, Map config) {
        def buildDetector = new BuildDetector(steps)
        def dockerfilePath = config.dockerfilePath

        if (buildDetector.requiresSshKey(dockerfilePath)) {
            steps.echo "[Stage:SSH Key] Detected SSH_PRIVATE_KEY requirement"
        } else {
            steps.echo "[Stage:SSH Key] Not required"
        }
    }

    static def build(steps, Map config) {
        steps.stage("Build") {
            def buildMode = config.buildMode ?: "unknown"
            def workdir   = config.workdir ?: '.'

            if (buildMode == "maven") {
                steps.echo "[Stage:Build] Running Maven build"
                steps.sh "cd ${workdir} && mvn clean package -Dmaven.test.skip=true"
            } else if (buildMode == "gradle") {
                steps.echo "[Stage:Build] Running Gradle build"
                steps.sh "cd ${workdir} && ./gradlew clean build -x test"
            } else if (buildMode == "front") {
                steps.echo "[Stage:Build] Running Frontend build"
                steps.echo "Build Mode: ${buildMode}, Skip."
                // steps.sh "cd ${workdir} && yarn install && yarn build"
            } else if (buildMode == "go") {
                steps.echo "[Stage:Build] Running Go build"
                steps.sh "cd ${workdir} && go build ./..."
            } else {
                steps.echo "[Stage:Build] Skipped (no build required, buildMode=${buildMode})"
            }
        }
    }

    static def imageBuild(steps, Map config) {
        new Builder(steps).run(config)
    }
}
