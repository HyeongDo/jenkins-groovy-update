package ci

class Stages {
    static def checkout(steps, Map config) {
        def checkoutInfo = new Checkout(steps).run(config)
        config.checkoutInfo = checkoutInfo
    }

    static def build(steps, Map config) {
        steps.stage("Build") {
            def buildDetector = new BuildDetector(steps)
            // def workdir        = config.workdir ?: '.'
            // def dockerfilePath = config.dockerfilePath ?: "${workdir}/Dockerfile"

            def ciDecision = buildDetector.detectBuild(config.dockerfilePath)
            def buildMode  = config.buildMode ?: buildDetector.detectBuildMode(config.dockerfilePath)

            config.ciDecision = ciDecision
            config.buildMode  = buildMode

            if (ciDecision == CIDecision.CI_REQUIRED) {
                steps.echo "Running external build: ${buildMode}"
                if (buildMode == "gradle") {
                    steps.sh "cd ${config.workdir} && ./gradlew clean build -x test"
                } else if (buildMode == "maven") {
                    steps.sh "cd ${config.workdir} && mvn clean package -Dmaven.test.skip=true"
                } else if (buildMode == "go") {
                    steps.sh "cd ${config.  workdir} && go build ./..."
                } else if (buildMode == "front") {
                    steps.sh "cd ${config.workdir} && yarn install && yarn build"
                }
            } else {
                steps.echo "Build skipped (ciDecision=${ciDecision})"
            }
        }
    }

    static def imageBuild(steps, Map config) {
        new Builder(steps).run(config)
    }

    static def sshKey(steps, Map config) {
        def buildDetector = new BuildDetector(steps)
        def dockerfilePath = config.dockerfilePath ?: "${config.workdir ?: '.'}/Dockerfile"

        if (buildDetector.requiresSshKey(dockerfilePath)) {
            steps.echo "[Stage:SSH Key] Detected SSH_PRIVATE_KEY requirement"
        } else {
            steps.echo "[Stage:SSH Key] Not required"
        }
    }
}
