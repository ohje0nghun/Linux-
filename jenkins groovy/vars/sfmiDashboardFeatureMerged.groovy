def call() {
    pipeline {
        agent any

        tools {
            maven 'maven-3.9.6'
        }

        environment {
            BUILD_DATE = sh(script: "echo `date +%Y%m%d`", returnStdout: true).trim()
            RELEASE_VERSION = sh(script: "echo ${BUILD_DATE}-${BUILD_ID}", returnStdout: true).trim()
            KUSTOMIZE_REPO = "http://sfmi-bitbucket.samsungfire.com/scm/id/sfmi-idm-pac.git"
            KUSTOMIZE_DIR = "sfmi-idm-pac"
            KUSTOMIZE_PATH = "dashboard-helm" // kustomize yaml 파일이 위치한 경로
            ARGOCD_APP_NAME = "sfmi-idm-dashboard-app" // 실제 ArgoCD 앱 이름으로 변경     
            NEW_IMAGE = "sfmi-dockerhub.samsungfire.com/sfmi/idm/dashboard:${RELEASE_VERSION}"       
        }

        parameters {
            string(name: 'GIT_URL', defaultValue: '', description: 'Git repository URL')
            string(name: 'BRANCH', defaultValue: 'develop', description: 'Branch to build')
        }

        stages {
            stage('Checkout') {
                steps {
                    script {
                        def gitUrl = params.GIT_URL
                        def branch = params.BRANCH
                        checkout([$class: 'GitSCM', branches: [[name: branch]], userRemoteConfigs: [[url: gitUrl, credentialsId: 'bitbucket']]])
                    }
                }
            }

            stage('Prepare') {
                steps {          
                    sh "java -version"
                    sh "chmod +x mvnw"
                    sh "mvn -ntp clean"
                }
            }


        




        }
    }
}
