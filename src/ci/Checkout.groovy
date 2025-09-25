package ci

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
                [$class: 'WipeWorkspace'],          // 워크스페이스 강제 삭제
                [$class: 'CleanBeforeCheckout']     // 체크아웃 전 git clean 실행
            ],
            userRemoteConfigs: [[
                credentialsId: 'jenkins-test',
                url: "https://bitbucket.org/okestrolab/${repo}.git"
            ]]
        ])

        // Dockerfile path 탐색
        def workdir = config.workdir ?: '.'
        def dockerfilePath = steps.sh(
            script: "find ${workdir} -maxdepth 2 -type f -iname 'Dockerfile' | head -n 1",
            returnStdout: true
        ).trim()
        if (!dockerfilePath) {
            steps.error "Dockerfile not found in ${workdir} or subdirectories"
        }
        config.dockerfilePath = dockerfilePath   // 🔥 확정 저장


        // Commit info with fallback
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

        // Registry/project 고정
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
            pullImage  : pullImage
        ]
    }
}
