package ci
import ci.CIDecision

class BuildDetector {
    def steps
    BuildDetector(steps) { this.steps = steps }


    CIDecision detectBuild(String dockerfilePath) {
        if (!dockerfilePath || !steps.fileExists(dockerfilePath)) {
            steps.error "[BuildDetector] Dockerfile not found: ${dockerfilePath}"
        }
        def dockerContent = steps.readFile(file: dockerfilePath, encoding: "UTF-8")

        if (dockerfile =~ /(?i)FROM .* AS builder/) {
            steps.echo "[BuildDetector] Detected multi-stage build → BUILT_IN"
            return CIDecision.BUILT_IN
        }

        if (dockerfile.contains("COPY /build/libs/") || dockerfile.contains(".jar")) {
            steps.echo "[BuildDetector] Detected artifact COPY → CI_REQUIRED"
            return CIDecision.CI_REQUIRED
        }

        if (dockerfile =~ /mvn|gradle|go build|yarn build|npm run build/) {
            steps.echo "[BuildDetector] Build command inside Dockerfile → BUILT_IN"
            return CIDecision.BUILT_IN
        }

        steps.echo "[BuildDetector] No indicators → NONE"
        return CIDecision.NONE
    }

    boolean requiresSshKey(String dockerfilePath) {
        def dockerfile = steps.readFile(dockerfilePath)

        if ((dockerfile =~ /ARG\s+SSH_PRIVATE_KEY/) || dockerfile.contains("SSH_PRIVATE_KEY")) {
            steps.echo "[BuildDetector] Detected SSH_PRIVATE_KEY usage"
            return true
        }

        steps.echo "[BuildDetector] No ssh-key indicators"
        return false
    }

    String detectBuildMode(String dockerfilePath) {
        def dockerfile = steps.readFile(dockerfilePath)

        if (dockerfile =~ /mvn/) return "maven"
        if (dockerfile =~ /gradle/) return "gradle"
        if (dockerfile =~ /go build/) return "go"
        if (dockerfile =~ /yarn build|npm run build/) return "front"

        // JAR 배포 패턴 추정
        if (dockerfile =~ /target\/.*\.jar/ || dockerfile.contains(".jar")) return "maven"
        // Gradle 배포 패턴
        if (dockerfile =~ /build\/libs\/.*\.jar/) return "gradle"


        return "unknown"
    }
}
