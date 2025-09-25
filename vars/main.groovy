import ci.Stages

def call(Map config) {
    pipeline {
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
