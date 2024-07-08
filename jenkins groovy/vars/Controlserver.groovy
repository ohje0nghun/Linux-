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
                    [key: 'CHANGE_TYPE', value: '$.changes[0].type'],
                    [key: 'ACTOR', value: '$.actor.name'],
                    [key: 'GIT_COMMIT', value: '$.changes[0].toHash']
                ],
                causeString: 'Triggered by Bitbucket Webhook',
                token: 'sfmi-token',
                printContributedVariables: true,
                printPostContent: true,
                regexpFilterText: '',
                regexpFilterExpression: ''
            )
        }
        environment {
            JENKINS_URL = 'http://192.168.10.9:19005'
            USERNAME = 'devadmin'
            API_TOKEN = '1180815ceb17b2d7f3354051edf12cbb41'
            JENKINS_CLI = '/var/jenkins_home/jenkins_cli/jenkins-cli.jar'
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
                        echo "CHANGE_TYPE: ${env.CHANGE_TYPE}"
                        echo "ACTOR: ${env.ACTOR}"
                        echo "GIT_COMMIT: ${env.GIT_COMMIT}"
                    }
                }
            }
            stage('Check Folder Existence') {
                steps {
                    script {
                        def folderName = "${env.PROJECT_KEY}/${env.REPO_SLUG}"
                        def folderUrl = "${env.JENKINS_URL}/job/${env.PROJECT_KEY}/job/${env.REPO_SLUG}/api/json"
                        def folderExists = false

                        echo "Checking if folder ${folderName} exists..."

                        def response = sh(
                            script: "curl -L -s -o /dev/null -w \"%{http_code}\" -u ${env.USERNAME}:${env.API_TOKEN} ${folderUrl}",
                            returnStdout: true
                        ).trim()

                        if (response == "200") {
                            echo "Folder ${folderName} exists."
                            folderExists = true
                        } else {
                            echo "Folder ${folderName} does not exist. Response code: ${response}"
                            folderExists = false
                        }
                        env.FOLDER_EXISTS = folderExists.toString()
                    }
                }
            }
            stage('Create Folder if Needed') {
                when {
                    expression { env.FOLDER_EXISTS == "false" }
                }
                steps {
                    script {
                        def folderName = "${env.PROJECT_KEY}/${env.REPO_SLUG}"
                        def configXmlPath = "${env.WORKSPACE}/${env.REPO_SLUG}_folder.xml"
                        
                        // Get the config.xml for the folder
                        sh "java -jar ${env.JENKINS_CLI} -s ${env.JENKINS_URL} -http -auth ${env.USERNAME}:${env.API_TOKEN} get-job ID > ${configXmlPath}"
                        
                        // Create new folder with the fetched config.xml
                        sh "java -jar ${env.JENKINS_CLI} -s ${env.JENKINS_URL} -http -auth ${env.USERNAME}:${env.API_TOKEN} create-job ${folderName} < ${configXmlPath}"
                        
                        echo "Created new folder: ${folderName}"
                    }
                }
            }
            stage('Check Job Existence') {
                steps {
                    script {
                        def jobName = "${env.PROJECT_KEY}/${env.REPO_SLUG}/${env.BRANCH_NAME}"
                        def jobUrl = "${env.JENKINS_URL}/job/${env.PROJECT_KEY}/job/${env.REPO_SLUG}/job/${env.BRANCH_NAME}/api/json"
                        def jobExists = false

                        echo "Checking if job ${jobName} exists..."

                        def response = sh(
                            script: "curl -L -s -o /dev/null -w \"%{http_code}\" -u ${env.USERNAME}:${env.API_TOKEN} ${jobUrl}",
                            returnStdout: true
                        ).trim()

                        if (response == "200") {
                            echo "Job ${jobName} exists."
                            jobExists = true
                        } else {
                            echo "Job ${jobName} does not exist. Response code: ${response}"
                            jobExists = false
                        }
                        env.JOB_EXISTS = jobExists.toString()
                    }
                }
            }
            stage('Create Job if Needed') {
                when {
                    expression { env.JOB_EXISTS == "false" }
                }
                steps {
                    script {
                        def jobName = "${env.PROJECT_KEY}/${env.REPO_SLUG}/${env.BRANCH_NAME}"
                        def configXmlPath = "${env.WORKSPACE}/${env.REPO_SLUG}.xml"
                        
                        // Get the config.xml from an existing job
                        sh "java -jar ${env.JENKINS_CLI} -s ${env.JENKINS_URL} -http -auth ${env.USERNAME}:${env.API_TOKEN} get-job ID/sfmi-idm-dashboard-app/${env.BRANCH_NAME} > ${configXmlPath}"
                        
                        // Create new job with the fetched config.xml
                        sh "java -jar ${env.JENKINS_CLI} -s ${env.JENKINS_URL} -http -auth ${env.USERNAME}:${env.API_TOKEN} create-job ${jobName} < ${configXmlPath}"
                        
                        echo "Created new job: ${jobName}"
                    }
                }
            }
            stage('Determine Job to Run') {
                steps {
                    script {
                        def jobName = "${env.PROJECT_KEY}/${env.REPO_SLUG}/${env.BRANCH_NAME}"
                        
                        echo "Determined Job Name: ${jobName}"
                        
                        // Convert SSH URL to HTTP URL if needed
                        def repoUrl = env.REPO_URL
                        if (repoUrl.startsWith('ssh://git@')) {
                            repoUrl = repoUrl.replace('ssh://git@', 'http://').replace(':7999/', '/scm/')
                        }
                        
                        echo "Converted Repo URL: ${repoUrl}"

                        // Trigger the Jenkins job based on projectKey, repoSlug, and branch
                        if (jobName != "" && env.CHANGE_TYPE != "DELETE") {
                            echo "Triggering Job: ${jobName}"
                            build job: jobName, wait: false, parameters: [
                                string(name: 'GIT_URL', value: repoUrl),
                                string(name: 'BRANCH', value: env.BRANCH_NAME),
                                string(name: 'GIT_COMMIT', value: env.GIT_COMMIT)
                            ]
                        } else {
                            echo "No matching job found for projectKey: ${env.PROJECT_KEY}, repoSlug: ${env.REPO_SLUG}, and branch: ${env.BRANCH_NAME}, or branch was deleted."
                        }
                    }
                }
            }
        }
    }
}
