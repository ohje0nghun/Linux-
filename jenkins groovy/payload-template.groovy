def call() {
    pipeline {
        agent any
        triggers {
            GenericTrigger(
                genericVariables: [
                    [key: 'BRANCH_NAME', value: '$.changes[0].ref.displayId'],
                    [key: 'REPO_URL', value: '$.repository.links.clone[0].href']
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
            stage('Display Event Information') {
                steps {
                    script {
                        def branchName = env.BRANCH_NAME
                        def repoUrl = env.REPO_URL
                        echo "Branch: ${branchName}"
                        echo "Repository URL: ${repoUrl}"
                    }
                }
            }
        }
    }
}
