package ci

import ci.BuildDetector.CIDecision

class Builder {
    def steps
    Builder(steps) { this.steps = steps }

    def run(Map config) {
        // Checkout first
        def checkoutInfo = new Checkout(steps).run(config)

        def repo     = checkoutInfo.repo
        def branch   = checkoutInfo.branch
        def pushImage= checkoutInfo.pushImage

        def workdir        = config.workdir ?: '.'
        def dockerfilePath = config.dockerfilePath ?: "${workdir}/Dockerfile"

        def buildDetector = new BuildDetector(steps)
        def ciDecision    = buildDetector.detectBuild(dockerfilePath)
        def buildMode     = config.buildMode ?: buildDetector.detectBuildMode(dockerfilePath)

        switch (ciDecision) {
            case CIDecision.CI_REQUIRED:
                steps.echo "[Builder] External CI build required, mode=${buildMode}"
                if (buildMode == "gradle") {
                    steps.sh "cd ${workdir} && ./gradlew clean build -x test"
                } else if (buildMode == "maven") {
                    steps.sh "cd ${workdir} && mvn clean package -Dmaven.test.skip=true"
                } else if (buildMode == "go") {
                    steps.sh "cd ${workdir} && go build ./..."
                } else if (buildMode == "front") {
                    steps.sh "cd ${workdir} && yarn install && yarn build"
                }
                break
            case CIDecision.BUILT_IN:
                steps.echo "[Builder] Build handled inside Dockerfile → skip external build"
                break
            case CIDecision.NONE:
                steps.echo "[Builder] No build needed"
                break
        }

        // SSH key 여부 확인 (필요할 경우만 추가가)
        def sshArgs = ""
        if (buildDetector.requiresSshKey(dockerfilePath)) {
            def keyFile = '/var/lib/jenkins/id_rsa'
            def rawKey  = new File(keyFile).text.trim()
            def escKey  = rawKey.replace("'", "'\"'\"'")
            sshArgs = "--build-arg SSH_PRIVATE_KEY='${escKey}'"
            steps.echo "[Builder] SSH key required for docker build"
        }

        // Docker build 실행
        steps.sh """
            cd ${workdir}
            docker build --force-rm --no-cache \
                        --build-arg BUILD_ENV=${branch} \
                        ${sshArgs} \
                        -t ${pushImage} \
                        -f ${dockerfilePath} .
            docker push ${pushImage}
        """.stripIndent()
    }
}
