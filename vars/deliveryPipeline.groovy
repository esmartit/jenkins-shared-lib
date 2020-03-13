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
                    sh "echo hello world"
                }
            }
        }
    }
}