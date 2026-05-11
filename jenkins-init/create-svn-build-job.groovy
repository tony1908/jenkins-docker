import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition
import org.jenkinsci.plugins.workflow.job.WorkflowJob

def jenkins = Jenkins.get()
def jobName = 'svn-dind-sample'
def svnRemote = System.getenv('SVN_REMOTE') ?: 'file:///svn/basic-repo/trunk'
def pipelineScript = """
pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
    }

    triggers {
        pollSCM('* * * * *')
    }

    environment {
        IMAGE_NAME = "registry:5000/local/svn-dind-sample:\${BUILD_NUMBER}"
        KUBE_NAMESPACE = 'jenkins-dind'
    }

    stages {
        stage('Checkout SVN') {
            steps {
                checkout([
                    \$class: 'SubversionSCM',
                    additionalCredentials: [],
                    excludedCommitMessages: '',
                    excludedRegions: '',
                    excludedRevprop: '',
                    excludedUsers: '',
                    filterChangelog: false,
                    ignoreDirPropChanges: false,
                    includedRegions: '',
                    locations: [[
                        cancelProcessOnExternalsFail: true,
                        credentialsId: '',
                        depthOption: 'infinity',
                        ignoreExternalsOption: true,
                        local: '.',
                        remote: '${svnRemote}'
                    ]],
                    quietOperation: true,
                    workspaceUpdater: [\$class: 'UpdateUpdater']
                ])
                sh 'ls -la && test -f package.json && test -f Dockerfile'
            }
        }

        stage('Test in Node container') {
            steps {
                sh 'docker run --rm -v "\$PWD":/app -w /app node:22-alpine npm test'
            }
        }

        stage('Build Docker image') {
            steps {
                sh 'docker build -t "\$IMAGE_NAME" .'
            }
        }

        stage('Push Docker image') {
            steps {
                sh 'docker push "\$IMAGE_NAME"'
            }
        }

        stage('Smoke test image') {
            steps {
                sh 'docker run --rm "\$IMAGE_NAME"'
            }
        }

        stage('Deploy to Minikube') {
            steps {
                sh '''
                    test -f "\$KUBECONFIG"
                    kubectl get nodes
                    kubectl create namespace "\$KUBE_NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
                    kubectl -n "\$KUBE_NAMESPACE" apply -f k8s/
                    kubectl -n "\$KUBE_NAMESPACE" set image deployment/svn-dind-sample svn-dind-sample="\$IMAGE_NAME"
                    kubectl -n "\$KUBE_NAMESPACE" rollout status deployment/svn-dind-sample --timeout=180s
                    kubectl -n "\$KUBE_NAMESPACE" get deploy,svc,pods -l app=svn-dind-sample -o wide
                '''
            }
        }
    }
}
"""

def job = jenkins.getItem(jobName)
def shouldSchedule = false
if (job == null) {
    job = jenkins.createProject(WorkflowJob, jobName)
    shouldSchedule = true
} else if (!(job.getDefinition() instanceof CpsFlowDefinition) || job.getDefinition().getScript() != pipelineScript) {
    shouldSchedule = true
}

job.setDescription("Builds ${svnRemote} when SVN changes. pollSCM stays enabled so the repository post-commit hook can notify Jenkins.")
job.setDefinition(new CpsFlowDefinition(pipelineScript, true))
job.save()

if ((shouldSchedule || job.getLastSuccessfulBuild() == null) && !job.isInQueue() && !job.isBuilding()) {
    job.scheduleBuild2(0)
}
