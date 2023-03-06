@Library('common') _

pipeline {
    agent any

    tools {
        jdk 'Java 17 Latest'
    }

    stages {
        stage('Prepare') {
            steps {
                sh "./gradlew clean"
            }
        }
    }
}