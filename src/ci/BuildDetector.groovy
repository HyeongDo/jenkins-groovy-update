package ci
import ci.CIDecision

/**
 * BuildDetector = Dockerfile 기반 보조 탐지기
 *
 * 주요 역할:
 *  1. detectBuild      → Dockerfile 로 외부 CI 빌드 필요 여부 판별
 *  2. requiresSshKey   → Dockerfile 이 SSH key build-arg 요구하는지 판별
 *  3. detectBuildMode  → Dockerfile 내용만 보고 빌드모드 추측 (Checkout의 fallback 용도)
 *
 * 사용 위치:
 *  - Checkout:
 *      buildMode 기본은 pom.xml / build.gradle / package.json / go.mod 파일로 판별
 *      → 못 찾았을 때 detectBuildMode() 호출해 fallback
 *  - Builder:
 *      docker build 실행 전 detectBuild() 로 CI_REQUIRED / BUILT_IN 로깅
 *      requiresSshKey() 로 SSH key 전달 여부 결정
 *  - Stages.build:
 *      ❌ 사용 안 함 (buildMode 는 이미 Checkout에서 확정)
 */
class BuildDetector {
    def steps
    BuildDetector(steps) { this.steps = steps }

    /**
     * Dockerfile 기반 CI 필요 여부 탐지
     * - 멀티스테이지 빌드면 BUILT_IN
     * - 산출물 COPY (.jar 등) 있으면 CI_REQUIRED
     * - 빌드 명령 포함 시 BUILT_IN
     * - 없으면 NONE
     */
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

    /**
     * Dockerfile 이 SSH key build-arg 를 요구하는지 확인
     * - ARG SSH_PRIVATE_KEY 가 선언되어 있으면 true
     */
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

    /**
     * Dockerfile 내용 기반 빌드모드 추측
     * - Checkout 에서 파일 기반 탐지 실패했을 때 fallback 으로만 사용
     */
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
