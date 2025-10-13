package ci
import ci.CIDecision

class Builder {
    def steps
    Builder(steps) { this.steps = steps }

    def run(Map config) {
        def repo      = config.repo
        def branch    = config.branch
        def pushImage = config.pushImage
        def workdir   = config.workdir
        def dockerfilePath = config.dockerfilePath

        if (!steps.fileExists(dockerfilePath)) {
            steps.error "[Builder] Dockerfile not found: ${dockerfilePath}"
        }

        // Build decision (로그용, 실행 자체는 Stages.build에서 이미 함)
        def buildDetector = new BuildDetector(steps)
        def ciDecision    = buildDetector.detectBuild(dockerfilePath)
        steps.echo "[Builder] Build decision from Dockerfile: ${ciDecision}"

        // SSH key 필요 여부 확인
        def sshArgs = ""
        if (buildDetector.requiresSshKey(dockerfilePath)) {
            def keyFile = '/var/lib/jenkins/id_rsa'
            if (!new File(keyFile).exists()) {
                steps.error "[Builder] SSH key file not found: ${keyFile}"
            }
            def rawKey  = new File(keyFile).text.trim()
            def escKey  = rawKey.replace("'", "'\"'\"'")
            sshArgs = "--build-arg SSH_PRIVATE_KEY='${escKey}'"
            steps.echo "[Builder] SSH key injected into docker build"
        }

        // Docker build & push
        // steps.sh """
        //     cd ${workdir}
        //     docker build --force-rm --no-cache \
        //         --build-arg BUILD_ENV=${branch} \
        //         ${sshArgs} \
        //         -t ${pushImage} \
        //         -f \$(basename ${dockerfilePath}) .
        //     docker push ${pushImage}
        // """.stripIndent()

        withCredentials([[$class: 'StringBinding', credentialsId: 'DOCKERHUB_PASS', variable: 'DOCKERHUB_PASS']]) {
            steps.sh """
                cd ${workdir}
                docker build --force-rm --no-cache \
                    --build-arg BUILD_ENV=${branch} \
                    -t bright93/okestro-${repo}:${branch} \
                    -f \$(basename ${dockerfilePath}) .
                echo ${DOCKERHUB_PASS} | docker login -u bright93 --password-stdin
                docker push bright93/okestro-${repo}:${branch}
            """.stripIndent()
        }

    }
}
