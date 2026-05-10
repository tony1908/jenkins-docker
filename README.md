# Jenkins with Docker-in-Docker

This project builds a custom Jenkins image with the Docker CLI, Buildx, Docker Compose, `kubectl`, and the Jenkins Docker Pipeline plugin installed. Docker commands run against a separate `docker:dind` daemon on the private Compose network.

The sample pipelines can also push images to a local registry and deploy them to a local Minikube cluster.

## Minikube setup

Start Minikube with access to the local Compose registry:

```sh
minikube start --driver=docker --insecure-registry=registry:5000
```

Generate a kubeconfig that works from inside the Jenkins container:

```sh
./scripts/write-minikube-kubeconfig.sh
```

If you use a non-default Minikube profile:

```sh
MINIKUBE_PROFILE=my-profile KUBE_CONTEXT=my-profile ./scripts/write-minikube-kubeconfig.sh
MINIKUBE_DOCKER_NETWORK=my-profile docker compose up -d --build
```

## Build and start

```sh
docker compose up -d --build
```

Open Jenkins at:

```text
http://localhost:8080
```

Get the first admin password:

```sh
docker compose exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

## Verify Docker from Jenkins

Create a Pipeline job and use the included `Jenkinsfile`, or run these commands in any Jenkins shell step:

```sh
docker version
docker run --rm hello-world
```

## Local sample build

The `sample-app` directory is a local Node app mounted into Jenkins at `/workspace/sample-app`.

On Jenkins startup, `jenkins-init/create-local-build-job.groovy` creates a Pipeline job named `local-dind-sample`. The job:

- copies the local app into the Jenkins workspace,
- runs `npm test` inside `node:22-alpine`,
- builds `registry:5000/local/jenkins-dind-sample:${BUILD_NUMBER}`,
- pushes the image to the local registry,
- runs the image as a smoke test,
- applies `sample-app/k8s` to Minikube and rolls out the new image.

Open the job at:

```text
http://localhost:8080/job/local-dind-sample/
```

## Local SVN build

The `svn/basic-repo` directory is a local SVN repository. Jenkins mounts it at `/svn/basic-repo` and the `svn-dind-sample` Pipeline polls this URL every minute:

```text
file:///svn/basic-repo/trunk
```

The job checks out the latest SVN revision, runs tests in `node:22-alpine`, builds `registry:5000/local/svn-dind-sample:${BUILD_NUMBER}`, pushes the image, runs it as a smoke test, and deploys `k8s/` to Minikube.

Open the job at:

```text
http://localhost:8080/job/svn-dind-sample/
```

To test another commit from the host:

```sh
svn checkout file://$PWD/svn/basic-repo/trunk svn-working-copy
printf '\n// another commit\n' >> svn-working-copy/src/index.js
svn commit svn-working-copy -m "Test Jenkins SVN polling"
```

Jenkins will build the new revision on the next SCM poll.

## Notes

- The DinD daemon listens on port `2375` only inside the Compose network. Do not publish that port to the host.
- The `docker` service is privileged because Docker-in-Docker needs nested container support.
- Jenkins data is stored in the `jenkins_home` Docker volume, DinD image/layer data is stored in `docker_data`, and local registry images are stored in `registry_data`.
- The `jenkins_home` volume is mounted into both Jenkins and the DinD sidecar so `docker run -v "$PWD:/app"` works from Jenkins jobs.
- Jenkins and the registry attach to Docker's Minikube network so Jenkins can reach the Kubernetes API and Minikube can pull `registry:5000/...` images. The default network name is `minikube`; set `MINIKUBE_DOCKER_NETWORK` when the Minikube profile uses another network name.
