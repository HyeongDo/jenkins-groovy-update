import ci.Builder

def call(Map config) {
    new Builder(this).run(config)
}