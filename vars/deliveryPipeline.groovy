def call(body) {

    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

    withCredentials([usernamePassword(
            credentialsId: 'github',
            usernameVariable: 'username', passwordVariable: 'gitToken')]) {

        def gitUrl = body.delegate.gitRepoUrl
        def helmRepoUrl = body.delegate.helmRepoUrl
        def artifactName = body.delegate.artifactName

        def repoUrl = "https://$gitUrl"

        def label = "worker-${UUID.randomUUID().toString()}"

        podTemplate(label: label, serviceAccount: 'jenkins',
                containers: [
                        containerTemplate(name: 'semantic-release', image: 'esmartit/semantic-release:1.0.3', ttyEnabled: true, command: 'cat',
                                envVars: [
                                        envVar(key: 'GITHUB_TOKEN', value: gitToken),
                                        envVar(key: 'DOCKER_HOST', value: 'tcp://dind.devops:2375')])
                ]
        ) {

            node(label) {

                try {

                    notifySlack()

                    container('semantic-release') {

                        stage('Checkout code') {
                            checkout scm
                        }

                        stage('Prepare release') {

                            withCredentials([usernamePassword(
                                    credentialsId: 'docker-credentials',
                                    usernameVariable: 'dockerUser', passwordVariable: 'dockerPass')]) {
                                env.DOCKER_USER = dockerUser
                                env.DOCKER_PASS = dockerPass
                            }

                            sh "npx semantic-release"
                        }

                        if (env.BRANCH_NAME == 'master') {
                            stage('Deploy Helm Package') {
                                def exists = fileExists 'version.txt'
                                if (exists) {
                                    def version = readFile('version.txt').toString().replaceAll("[\\n\\t ]", "")
                                    sh "rm version.txt"
                                    git branch: 'gh-pages', credentialsId: 'github', url: repoUrl
                                    sh "git config --global user.email 'tech@esmartit.es'"
                                    sh "git config --global user.name 'esmartit'"
                                    def versionedArtifactName = "$artifactName-${version}.tgz"
                                    sh "mv ${versionedArtifactName} docs"
                                    sh "helm repo index docs --merge docs/index.yaml --url $helmRepoUrl"
                                    sh "git add ."
                                    sh "git status"
                                    sh "git commit -m \"adding new artifact version: $version\""
                                    withCredentials([usernamePassword(
                                            credentialsId: 'esmartit-github-username-pass',
                                            usernameVariable: 'username', passwordVariable: 'password')]) {
                                        sh "git push https://$username:$password@$gitUrl"
                                    }
                                }
                            }
                        }
                    }

                } catch (e) {
                    // If there was an exception thrown, the build failed
                    currentBuild.result = "FAILED"
                    throw e
                } finally {
                    // Success or failure, always send notifications
                    notifySlack(currentBuild.result)
                }
            }
        }
    }
}



