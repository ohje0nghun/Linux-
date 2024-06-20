def call(String PROJECT_NAME){

pipeline {

    agent any

    tools {
        maven 'maven-3.9.6'
    }


   environment {
        def BUILD_DATE = sh(script: "echo `date +%Y%m%d`", returnStdout: true).trim()
        def RELEASE_VERSION = sh(script: "echo ${BUILD_DATE}-${BUILD_ID}", returnStdout: true).trim()
        def BRANCH_NAME = 'dev'    
        def KUSTOMIZE_REPO = "http://sfmi-bitbucket.samsungfire.com/scm/id/sfmi-idm-pac.git"
        def KUSTOMIZE_DIR = "sfmi-idm-pac"
        def KUSTOMIZE_PATH = "dashboard-helm" // kustomize yaml 파일이 위치한 경로
        def ARGOCD_APP_NAME = "sfmi-idm-dashboard-app" // 실제 ArgoCD 앱 이름으로 변경     
        def NEW_IMAGE = "sfmi-dockerhub.samsungfire.com/sfmi/idm/dashboard:${env.RELEASE_VERSION}"       
    }

    stages {

        stage('prepare') {
            steps {
                sh "echo $PROJECT_NAME build start"                
                sh "java -version"
                sh "chmod +x mvnw"
                sh "mvn -ntp clean"
            }
        }


       stage('nohttp') {
            steps {
                sh "mvn -ntp checkstyle:check"
            }
        }

        stage('tests') {
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

        stage('packaging') {
            steps {
                sh "./mvnw -ntp verify -DskipTests"
                archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
            }
        }

 /*       stage('quality analysis') {
            steps {
                withSonarQubeEnv('sonar') {
                    sh "./mvnw -ntp initialize sonar:sonar"
                    sh "./mvnw clean verify sonar:sonar"
                }
            }
        }
*/

        stage('publish') {
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
                    kustomize edit set image sfmi-dockerhub.samsungfire.com/sfmi/idm/dashboard=sfmi-dockerhub.samsungfire.com/sfmi/idm/dashboard:${env.RELEASE_VERSION}
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
                                git commit -m "Update image to ${env.NEW_IMAGE}"
                                git push http://${GIT_USERNAME}:${GIT_PASSWORD}@sfmi-bitbucket.samsungfire.com/scm/id/sfmi-idm-pac.git HEAD:dev
                                """
                            }
                        }
                    }
                }
            }

    }

}

}




