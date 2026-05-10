import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def jenkins = Jenkins.get()
def jobName = 'local-dind-sample'
def pipelineScript = '''
pipeline {
    agent any

    environment {
        IMAGE_NAME = "registry:5000/local/jenkins-dind-sample:${BUILD_NUMBER}"
        KUBE_NAMESPACE = 'jenkins-dind'
    }

    stages {
        stage('Prepare local code') {
            steps {
                sh """
                    rm -rf sample-app
                    cp -R /workspace/sample-app sample-app
                    chmod -R u+w sample-app
                """
            }
        }

        stage('Inspect code') {
            steps {
                dir('sample-app') {
                    sh 'pwd && ls -la && git rev-parse --short HEAD'
                }
            }
        }

        stage('Test in Node container') {
            steps {
                dir('sample-app') {
                    sh 'docker run --rm -v "$PWD":/app -w /app node:22-alpine npm test'
                }
            }
        }

        stage('Build Docker image') {
            steps {
                dir('sample-app') {
                    sh 'docker build -t "$IMAGE_NAME" .'
                }
            }
        }

        stage('Push Docker image') {
            steps {
                sh 'docker push "$IMAGE_NAME"'
            }
        }

        stage('Smoke test image') {
            steps {
                sh 'docker run --rm "$IMAGE_NAME"'
            }
        }

        stage('Deploy to Minikube') {
            steps {
                dir('sample-app') {
                    sh """
                        test -f "\\$KUBECONFIG"
                        kubectl get nodes
                        kubectl create namespace "\\$KUBE_NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
                        kubectl -n "\\$KUBE_NAMESPACE" apply -f k8s/
                        kubectl -n "\\$KUBE_NAMESPACE" set image deployment/jenkins-dind-sample jenkins-dind-sample="\\$IMAGE_NAME"
                        kubectl -n "\\$KUBE_NAMESPACE" rollout status deployment/jenkins-dind-sample --timeout=180s
                        kubectl -n "\\$KUBE_NAMESPACE" get deploy,svc,pods -l app=jenkins-dind-sample -o wide
                    """
                }
            }
        }
    }
}
'''

def job = jenkins.getItem(jobName)
def shouldSchedule = false
if (job == null) {
    job = jenkins.createProject(WorkflowJob, jobName)
    shouldSchedule = true
} else if (!(job.getDefinition() instanceof CpsFlowDefinition) || job.getDefinition().getScript() != pipelineScript) {
    shouldSchedule = true
}

job.setDescription('Builds the local sample app mounted from /workspace/sample-app and verifies Docker-in-Docker.')
job.setDefinition(new CpsFlowDefinition(pipelineScript, true))
job.save()

if ((shouldSchedule || job.getLastSuccessfulBuild() == null) && !job.isInQueue() && !job.isBuilding()) {
    job.scheduleBuild2(0)
}
