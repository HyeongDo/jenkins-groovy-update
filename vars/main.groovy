import ci.Stages

def call(Map config) {
    Stages.checkout(this, config)
    Stages.sshKey(this, config)
    Stages.build(this, config)
    Stages.imageBuild(this, config)
}

