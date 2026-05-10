pipeline {
    agent any

    stages {
        stage('Docker access') {
            steps {
                sh 'docker version'
                sh 'docker run --rm hello-world'
            }
        }
    }
}
