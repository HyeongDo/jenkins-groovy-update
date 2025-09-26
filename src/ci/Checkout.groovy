package ci
import ci.BuildDetector

class Checkout {
    def steps
    Checkout(steps) { this.steps = steps }

    Map run(Map config) {
        def repo   = config.repo
        def branch = config.env

        if (!repo || !branch) {
            steps.error "Checkout requires 'repo' and 'env' arguments"
        }

        def scmInfo = steps.checkout([
            $class: 'GitSCM',
            branches: [[name: branch]],
            extensions: [
                steps.submodule(
                    parentCredentials: true,
                    reference: '',
                    recursiveSubmodules: true,
                    trackingSubmodules: true
                ),
                [$class: 'WipeWorkspace'],
                [$class: 'CleanBeforeCheckout']
            ],
            userRemoteConfigs: [[
                credentialsId: 'jenkins-test',
                url: "https://bitbucket.org/okestrolab/${repo}.git"
            ]]
        ])

        // Dockerfile 탐색
        def dockerfilePath = steps.sh(
            script: "find . -maxdepth 2 -type f -iname 'Dockerfile' | head -n 1",
            returnStdout: true
        ).trim()
        if (!dockerfilePath) {
            steps.error "Dockerfile not found in repository"
        }
        config.dockerfilePath = dockerfilePath

        // Dockerfile 디렉토리를 workdir 로 지정
        def dockerfileDir = steps.sh(
            script: "dirname ${dockerfilePath}",
            returnStdout: true
        ).trim()
        config.workdir = dockerfileDir

        // 1차: repo 실제 파일 기반 buildMode 탐지
        def buildMode = ""
        if (steps.fileExists("${dockerfileDir}/pom.xml")) {
            buildMode = "maven"
        } else if (steps.fileExists("${dockerfileDir}/build.gradle") || steps.fileExists("${dockerfileDir}/build.gradle.kts")) {
            buildMode = "gradle"
        } else if (steps.fileExists("${dockerfileDir}/package.json")) {
            buildMode = "front"
        } else if (steps.fileExists("${dockerfileDir}/go.mod")) {
            buildMode = "go"
        }

        // 2차: fallback → Dockerfile 내용 기반 추측
        if (!buildMode) {
            def detector = new BuildDetector(steps)
            buildMode = detector.detectBuildMode(dockerfilePath)
            steps.echo "[Checkout] buildMode fallback from Dockerfile: ${buildMode}"
        }

        config.buildMode = buildMode

        // Commit info
        def fullCommitId = scmInfo.GIT_COMMIT ?: steps.sh(
            returnStdout: true,
            script: "git rev-parse HEAD"
        ).trim()
        def shortCommitId = fullCommitId.take(7)

        def author = steps.sh(
            script: "git log -1 --pretty=format:'%an'",
            returnStdout: true
        ).trim()
        def commitTime = steps.sh(
            script: "git log -1 --pretty=format:'%ci'",
            returnStdout: true
        ).trim()

        def today = new Date().format('yy.MM.dd.HH.mm', TimeZone.getTimeZone('Asia/Seoul'))

        def buildInfo = """
        ############### BUILD INFO #################
        #  Build Time   : ${today}
        #  Git Branch   : ${branch}
        #  Git Author   : ${author}
        #  Git Commit ID: ${fullCommitId}
        #  Git Commit Time : ${commitTime}
        ############################################
        """.stripIndent()

        steps.writeFile file: 'build_info.txt', text: buildInfo, encoding: 'UTF-8'

        def registryBase = "nexus.okestro-k8s.com"
        def project      = "maestro"

        def pushRegistry = "${registryBase}:55000"
        def pullRegistry = "${registryBase}:50000"

        def tag = "${branch}-${today}-${shortCommitId}"

        def pushImage = "${pushRegistry}/${project}/${repo}:${tag}"
        def pullImage = "${pullRegistry}/${project}/${repo}:${tag}"

        return [
            repo       : repo,
            branch     : branch,
            commit     : fullCommitId,
            shortCommit: shortCommitId,
            author     : author,
            commitTime : commitTime,
            buildInfo  : buildInfo,
            today      : today,
            pushImage  : pushImage,
            pullImage  : pullImage,
            workdir    : dockerfileDir,
            dockerfilePath: dockerfilePath,
            buildMode  : buildMode
        ]
    }
}
