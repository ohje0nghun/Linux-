def call() {
    pipeline {
        agent any
        stages {
            stage('Print SCM Changes') {
                steps {
                    script {
                        // 변경된 커밋 정보
                        def gitCommit = sh(script: "git rev-parse HEAD", returnStdout: true).trim()
                        // 변경된 브랜치 정보
                        def branchName = sh(script: "git rev-parse --abbrev-ref HEAD", returnStdout: true).trim()
                        // 변경된 파일 목록
                        def changedFiles = sh(script: "git diff-tree --no-commit-id --name-only -r ${gitCommit}", returnStdout: true).trim()
                        // 최근 커밋 메시지
                        def commitMessage = sh(script: "git log -1 --pretty=%B", returnStdout: true).trim()
                        // 커밋 작성자
                        def commitAuthor = sh(script: "git log -1 --pretty=%an", returnStdout: true).trim()

                        echo "BRANCH_NAME: ${branchName}"
                        echo "GIT_COMMIT: ${gitCommit}"
                        echo "CHANGED_FILES: ${changedFiles}"
                        echo "COMMIT_MESSAGE: ${commitMessage}"
                        echo "COMMIT_AUTHOR: ${commitAuthor}"

                        // Webhook으로 전달받는 정보는 SCM 폴링에서는 사용할 수 없음
                        echo "REPO_URL: Not available in polling"
                        echo "PROJECT_KEY: Not available in polling"
                        echo "REPO_SLUG: Not available in polling"
                        echo "EVENT_KEY: Not available in polling"
                        echo "CHANGE_TYPE: Not available in polling"
                        echo "ACTOR: Not available in polling"
                    }
                }
            }
        }
    }
}
