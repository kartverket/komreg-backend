pipeline {
    agent any

    tools {
        jdk 'Java 17 Latest'
    }

    environment {
        MAVEN_PUBLISH = credentials('MAVEN_DEPLOY_RELEASES')
    }

    stages {
        stage('Build') {
            steps {
                sh "./gradlew clean build -p core-api"
            }
        }
        stage('Publish') {
            steps {
                sh "./gradlew publish -p core-api --init-script ../gradle/mavenPublish.gradle"
            }
        }
    }
}
