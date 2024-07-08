def call() {
    pipeline {
        agent any
        parameters {
            string(name: 'BRANCH_NAME', defaultValue: '', description: 'Branch name')
            string(name: 'REPO_URL', defaultValue: '', description: 'Repository URL')
        }
        stages {
            stage('Print Parameters') {
                steps {
                    script {
                        echo "BRANCH_NAME: ${params.BRANCH_NAME}"
                        echo "REPO_URL: ${params.REPO_URL}"
                    }
                }
            }
        }
    }
}
