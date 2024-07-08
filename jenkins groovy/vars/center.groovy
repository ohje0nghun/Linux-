def call() {
    pipeline {
        agent any
        triggers {
            GenericTrigger(
                genericVariables: [
                    [key: 'BRANCH_NAME', value: '$.changes[0].ref.displayId'],
                    [key: 'REPO_URL', value: '$.repository.links.clone[0].href'],
                    [key: 'PROJECT_KEY', value: '$.repository.project.key'],
                    [key: 'REPO_SLUG', value: '$.repository.slug'],
                    [key: 'EVENT_KEY', value: '$.eventKey'],
                    [key: 'ACTOR', value: '$.actor.name']
                ],
                causeString: 'Triggered by Bitbucket Webhook',
                token: 'sfmi-token', // optional, if you use a token in your webhook
                printContributedVariables: true,
                printPostContent: true,
                regexpFilterText: '',
                regexpFilterExpression: ''
            )
        }
        stages {
            stage('Print Environment Variables') {
                steps {
                    script {
                        echo "BRANCH_NAME: ${env.BRANCH_NAME}"
                        echo "REPO_URL: ${env.REPO_URL}"
                        echo "PROJECT_KEY: ${env.PROJECT_KEY}"
                        echo "REPO_SLUG: ${env.REPO_SLUG}"
                        echo "EVENT_KEY: ${env.EVENT_KEY}"
                        echo "ACTOR: ${env.ACTOR}"
                    }
                }
            }
            stage('Trigger Get Job') {
                steps {
                    script {
                        def jobName = "${env.PROJECT_KEY}/get" // 여기에 실제 job 경로를 입력하세요.
                        echo "Triggering Job: ${jobName}"
                        build job: jobName, wait: false, parameters: [
                            string(name: 'BRANCH_NAME', value: env.BRANCH_NAME),
                            string(name: 'REPO_URL', value: env.REPO_URL)
                        ]
                    }
                }
            }
        }
        post {
            always {
                echo 'ControlServer Job is now terminating.'
            }
        }
    }
}
