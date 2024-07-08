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
            string(name: 'GIT_COMMIT', defaultValue: '', description: 'Git commit hash')
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

            stage('NoHttp') {
                steps {
                    sh "mvn -ntp checkstyle:check"
                }
            }

            stage('Tests') {
                steps {
                    script {
                        try {
                            sh "./mvnw -ntp verify"
                        } catch(err) {
                            throw err
                        } finally {
                            junit '**/target/surefire-reports/TEST-*.xml,**/target/failsafe-reports/TEST-*.xml'
                        }
                    }
                }
            } 

            stage('Packaging') {
                steps {
                    sh "./mvnw -ntp verify -DskipTests"
                    archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
                }
            }

            stage('Publish') {
                steps {
                    sh "./mvnw -ntp verify jib:build"
                }
            }
            stage('Clone Kustomize Repo') {
                steps {
                    dir(env.KUSTOMIZE_DIR) {
                        git url: env.KUSTOMIZE_REPO, branch: 'dev', credentialsId: 'bitbucket'
                     }
                }
            }        

            stage('Update Image with Kustomize') {
                steps {
                    dir(env.KUSTOMIZE_DIR + '/' + env.KUSTOMIZE_PATH) {
                        sh """
                        docker image tag sfmi-dockerhub.samsungfire.com/sfmi/idm/dashboard sfmi-dockerhub.samsungfire.com/sfmi/idm/dashboard:${RELEASE_VERSION}
                        kustomize edit set image sfmi-dockerhub.samsungfire.com/sfmi/idm/dashboard=sfmi-dockerhub.samsungfire.com/sfmi/idm/dashboard:${RELEASE_VERSION}
                        """
                    }
                }
            }

            stage('Commit and Push Changes') {
                steps {
                    script {
                        withCredentials([usernamePassword(credentialsId: 'bitbucket', usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
                            dir(env.KUSTOMIZE_DIR + '/' + env.KUSTOMIZE_PATH) {
                                sh """
                                git config user.email "jeonghun@samsungfire.com"
                                git config user.name "jeonghun"
                                git add .
                                git commit -m "Update image to ${NEW_IMAGE}"
                                git push http://${GIT_USERNAME}:${GIT_PASSWORD}@sfmi-bitbucket.samsungfire.com/scm/id/sfmi-idm-pac.git HEAD:dev
                                """
                            }
                        }
                    }
                }
            }
        }

        post {
            success {
                script {
                    def bitbucketWebhookUrl = "http://sfmi-bitbucket.samsungfire.com/rest/build-status/1.0/commits/${params.GIT_COMMIT}"
                    def payload = """{
                        "state": "SUCCESSFUL",
                        "key": "${env.JOB_NAME}",
                        "name": "${env.JOB_NAME}",
                        "url": "${env.BUILD_URL}",
                        "description": "Build completed successfully",
                        "logUrl": "${env.BUILD_URL}console"
                    }"""
                    withCredentials([usernamePassword(credentialsId: 'bitbucket', usernameVariable: 'BITBUCKET_USERNAME', passwordVariable: 'BITBUCKET_PASSWORD')]) {
                        sh """
                        curl -v -u ${BITBUCKET_USERNAME}:${BITBUCKET_PASSWORD} -X POST -H "Content-Type: application/json" -d '${payload}' ${bitbucketWebhookUrl}
                        """
                    }
                }
            }
            failure {
                script {
                    def bitbucketWebhookUrl = "http://sfmi-bitbucket.samsungfire.com/rest/build-status/1.0/commits/${params.GIT_COMMIT}"
                    def payload = """{
                        "state": "FAILED",
                        "key": "${env.JOB_NAME}",
                        "name": "${env.JOB_NAME}",
                        "url": "${env.BUILD_URL}",
                        "description": "Build failed",
                        "logUrl": "${env.BUILD_URL}console"
                    }"""
                    withCredentials([usernamePassword(credentialsId: 'bitbucket', usernameVariable: 'BITBUCKET_USERNAME', passwordVariable: 'BITBUCKET_PASSWORD')]) {
                        sh """
                        curl -v -u ${BITBUCKET_USERNAME}:${BITBUCKET_PASSWORD} -X POST -H "Content-Type: application/json" -d '${payload}' ${bitbucketWebhookUrl}
                        """
                    }
                }
            }
        }
    }
}
