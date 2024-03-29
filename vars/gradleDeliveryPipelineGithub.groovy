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

        def label = "worker-${UUID.randomUUID().toString()}"

        podTemplate(label: label, serviceAccount: 'jenkins',
                containers: [
                        containerTemplate(name: 'gradle', image: 'gradle:6.4.1-jdk8', ttyEnabled: true, command: 'cat'),
                        containerTemplate(name: 'semantic-release', image: 'esmartit/semantic-release:1.0.3', ttyEnabled: true, command: 'cat',
                                envVars: [
                                        envVar(key: 'GITHUB_TOKEN', value: gitToken),
                                        envVar(key: 'DOCKER_HOST', value: 'tcp://dind.devops:2375')])
                ],
                volumes: [
                        hostPathVolume(mountPath: '/home/gradle/.gradle', hostPath: '/tmp/jenkins/.gradle'),
                        emptyDirVolume(mountPath: '/var/lib/docker', memory: false)
                ]
        ) {

            node(label) {

                try {

                    notifySlack()
                    container('semantic-release') {
                        stage('Checkout code') {

                            sh "git config --global user.email 'tech@esmartit.es'"
                            sh "git config --global user.name 'esmartit'"

                            checkout([$class: 'GitSCM',
                                      branches: [[name: '*/${env.BRANCH_NAME}'],
                                                 [name: '*/gh-pages']],
                                      extensions: [],
                                      userRemoteConfigs: [[credentialsId: 'esmartit-github-ssh', url: gitUrl]]])
                        }
                    }

                    container('gradle'){
                        stage('Test') {
                            try {
                                sh "git checkout ${env.BRANCH_NAME}"
                                sh 'gradle build'
                            }finally {
                                junit '**/build/test-results/test/*.xml'
                            }
                        }
                    }

                    container('semantic-release') {

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
                                    sh "git checkout gh-pages"
                                    def versionedArtifactName = "$artifactName-${version}.tgz"
                                    sh "mv ${versionedArtifactName} docs"
                                    sh "helm repo index docs --merge docs/index.yaml --url $helmRepoUrl"
                                    sh "git add ."
                                    sh "git status"
                                    sh "git commit -m \"adding new artifact version: $version\""
                                    withEnv(['GIT_SSH_COMMAND=ssh -o StrictHostKeyChecking=no']) {
                                        sshagent(credentials: ['esmartit-github-ssh']) {
                                            sh "git push"
                                        }
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
