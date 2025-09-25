import ci.Stages

def call(Map config) {
    pipeline {
        agent {
            docker {
                image 'docker:24.0.7-dind'
                args '--privileged -v /var/run/docker.sock:/var/run/docker.sock'
            }
        }
        stages {
            stage('Checkout') {
                steps {
                    script { Stages.checkout(this, config) }
                }
            }
            stage('Build') {
                steps {
                    script { Stages.build(this, config) }
                }
            }
            stage('Image Build') {
                steps {
                    script { Stages.imageBuild(this, config) }
                }
            }
            stage('SSH Key Injection') {
                steps {
                    script { Stages.sshKey(this, config) }
                }
            }
        }
    }
}
