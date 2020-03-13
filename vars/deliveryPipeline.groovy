def call(body) {

    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    def label = "worker-${UUID.randomUUID().toString()}"

    podTemplate(label: label, serviceAccount: 'jenkins') {

        node(label) {
            container('jnlp') {
                stage('Checkout code') {
                    def p = toAlphanumeric(text: "a_B-c.1")
                    sh "echo hello world $p"
                }
            }
        }
    }
}



