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

        if (dockerContent =~ /(?i)FROM .* AS builder/) {
            steps.echo "[BuildDetector] Detected multi-stage build → BUILT_IN"
            return CIDecision.BUILT_IN
        }

        if (dockerContent.contains("COPY /build/libs/") || dockerContent.contains(".jar")) {
            steps.echo "[BuildDetector] Detected artifact COPY → CI_REQUIRED"
            return CIDecision.CI_REQUIRED
        }

        if (dockerContent =~ /mvn|gradle|go build|yarn build|npm run build/) {
            steps.echo "[BuildDetector] Build command inside Dockerfile → BUILT_IN"
            return CIDecision.BUILT_IN
        }

        steps.echo "[BuildDetector] No indicators → NONE"
        return CIDecision.NONE
    }

    boolean requiresSshKey(String dockerfilePath) {
        if (!dockerfilePath || !steps.fileExists(dockerfilePath)) {
            steps.error "[BuildDetector] Dockerfile not found: ${dockerfilePath}"
        }
        def dockerContent = steps.readFile(file: dockerfilePath, encoding: "UTF-8")

        if ((dockerContent =~ /ARG\s+SSH_PRIVATE_KEY/) || dockerContent.contains("SSH_PRIVATE_KEY")) {
            steps.echo "[BuildDetector] Detected SSH_PRIVATE_KEY usage"
            return true
        }

        steps.echo "[BuildDetector] No ssh-key indicators"
        return false
    }

    String detectBuildMode(String dockerfilePath) {
        if (!dockerfilePath || !steps.fileExists(dockerfilePath)) {
            steps.error "[BuildDetector] Dockerfile not found: ${dockerfilePath}"
        }
        def dockerContent = steps.readFile(file: dockerfilePath, encoding: "UTF-8")

        if (dockerContent =~ /mvn/) return "maven"
        if (dockerContent =~ /gradle/) return "gradle"
        if (dockerContent =~ /go build/) return "go"
        if (dockerContent =~ /yarn build|npm run build/) return "front"

        // JAR 배포 패턴 추정
        if (dockerContent =~ /target\/.*\.jar/ || dockerContent.contains(".jar")) return "maven"
        // Gradle 배포 패턴
        if (dockerContent =~ /build\/libs\/.*\.jar/) return "gradle"

        return "unknown"
    }
}
