# Jenkins con Docker-in-Docker

Este proyecto levanta Jenkins en Docker con Docker CLI, Buildx, Docker Compose, `kubectl` y plugins base de Jenkins. Los comandos `docker` no usan el socket Docker del host; Jenkins habla con un daemon separado `docker:dind` dentro de la red privada de Compose.

Los pipelines de ejemplo pueden construir imagenes, publicarlas en un registry local y desplegarlas en Minikube.

## Configuracion local

Copia el archivo de ejemplo y ajusta las rutas a tu maquina:

```sh
cp .env.example .env
```

Variables utiles:

- `LOCAL_PROJECT_PATH`: ruta absoluta a un checkout local que Jenkins vera como `/workspace/project`.
- `SVN_REPO_PATH`: ruta absoluta al repositorio SVN local, es decir el directorio que contiene `conf/`, `db/`, `hooks/`, `locks/` y `format`.
- `SVN_REMOTE`: URL SVN que usara el job generado. Debe usar la ruta del contenedor, por ejemplo `file:///svn/external-repo/branches/development`.
- `REGISTRY_HOST_PORT`: puerto del host para exponer el registry local. Por defecto es `5001`.
- `MINIKUBE_DOCKER_NETWORK`: red Docker del perfil de Minikube. Por defecto es `minikube`.

Si tu repositorio SVN en el host se ve asi:

```text
file:///ruta/en/tu/maquina/svn-repo/repo/branches
```

no uses esa URL dentro de Jenkins, porque el contenedor no ve el filesystem del host con la misma ruta. Monta el repositorio con `SVN_REPO_PATH` y usa la ruta del contenedor:

```text
file:///svn/external-repo/branches/<nombre-de-rama>
```

Usa una rama concreta, por ejemplo:

```text
file:///svn/external-repo/branches/development
```

`branches` por si solo solo contiene carpetas de ramas; normalmente no es el proyecto que quieres compilar.

## Minikube

Inicia Minikube permitiendo que el cluster pueda descargar imagenes del registry local:

```sh
minikube start --driver=docker --insecure-registry=registry:5000
```

Genera el kubeconfig que funciona desde dentro del contenedor de Jenkins:

```sh
./scripts/write-minikube-kubeconfig.sh
```

Si usas otro perfil de Minikube:

```sh
MINIKUBE_PROFILE=mi-perfil KUBE_CONTEXT=mi-perfil ./scripts/write-minikube-kubeconfig.sh
MINIKUBE_DOCKER_NETWORK=mi-perfil docker compose up -d --build
```

## Levantar Jenkins

```sh
docker compose up -d --build
```

Abre Jenkins en:

```text
http://localhost:8080
```

Obtiene la primera password de admin:

```sh
docker compose exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```

Si el puerto `5000` del host esta ocupado, no afecta a Jenkins ni a Minikube: dentro de Docker el registry sigue siendo `registry:5000`. El puerto del host por defecto es `5001` y se puede cambiar con `REGISTRY_HOST_PORT`.

## Verificar Docker desde Jenkins

En cualquier shell step de Jenkins puedes validar:

```sh
docker version
docker run --rm hello-world
```

## Montar un proyecto local

Define `LOCAL_PROJECT_PATH` en `.env`:

```sh
LOCAL_PROJECT_PATH=/ruta/absoluta/a/tu/proyecto
```

Jenkins lo vera como:

```text
/workspace/project
```

Para builds con Docker-in-Docker, copia ese proyecto al workspace de Jenkins antes de usar `docker build` o `docker run -v "$PWD:..."`, porque el daemon DinD comparte con Jenkins el volumen `jenkins_home`, no cualquier ruta del host.

## Usar tu propio repositorio SVN

Define el repositorio SVN local en `.env`:

```sh
SVN_REPO_PATH=/ruta/absoluta/a/tu/svn-repo/repo
```

Ese directorio se monta dentro del contenedor como:

```text
/svn/external-repo
```

Entonces configura el job de Jenkins con una URL del contenedor:

```text
file:///svn/external-repo/trunk
```

o con una rama:

```text
file:///svn/external-repo/branches/mi-rama
```

Si quieres que el job generado `svn-dind-sample` use tu repositorio, define tambien:

```sh
SVN_REMOTE=file:///svn/external-repo/branches/mi-rama
```

Despues recrea Jenkins para que lea las variables y vuelva a generar la definicion del job:

```sh
docker compose up -d --force-recreate jenkins
```

Puedes probar desde dentro del contenedor:

```sh
docker compose exec jenkins svn list file:///svn/external-repo/branches
docker compose exec jenkins svn info file:///svn/external-repo/branches/mi-rama
```

## Job local de ejemplo

El directorio `sample-app` se monta en Jenkins como `/workspace/sample-app`.

Al arrancar Jenkins, `jenkins-init/create-local-build-job.groovy` crea un Pipeline llamado `local-dind-sample`. El job:

- copia `sample-app` al workspace de Jenkins;
- ejecuta `npm test` dentro de `node:22-alpine`;
- construye `registry:5000/local/jenkins-dind-sample:${BUILD_NUMBER}`;
- publica la imagen en el registry local;
- ejecuta la imagen como smoke test;
- despliega `sample-app/k8s` en Minikube;
- prueba el Service de Kubernetes con `kubectl port-forward` y `curl`.

Abre el job en:

```text
http://localhost:8080/job/local-dind-sample/
```

## Job SVN de ejemplo

El directorio `svn/basic-repo` es un repositorio SVN local de ejemplo. Jenkins lo monta como `/svn/basic-repo` y el job `svn-dind-sample` usa por defecto:

```text
file:///svn/basic-repo/trunk
```

El job hace checkout, corre tests en `node:22-alpine`, construye `registry:5000/local/svn-dind-sample:${BUILD_NUMBER}`, publica la imagen, ejecuta smoke test de imagen y despliega `k8s/` en Minikube.

Abre el job en:

```text
http://localhost:8080/job/svn-dind-sample/
```

Para probar otro commit sobre el repo de ejemplo:

```sh
svn checkout file://$PWD/svn/basic-repo/trunk svn-working-copy
printf '\n// another commit\n' >> svn-working-copy/src/index.js
svn commit svn-working-copy -m "Test Jenkins SVN polling"
```

El hook `post-commit` de ejemplo intenta avisar a Jenkins con `notifyCommit`. Si Jenkins no esta disponible, el commit no se bloquea y `pollSCM` queda como respaldo.

## Notas

- El daemon DinD escucha en `2375` solo dentro de la red de Compose. No publiques ese puerto al host.
- El servicio `docker` es privilegiado porque Docker-in-Docker necesita soporte para contenedores anidados.
- Los datos de Jenkins viven en el volumen `jenkins_home`.
- Las capas e imagenes del daemon DinD viven en `docker_data`.
- Las imagenes del registry local viven en `registry_data`.
- El volumen `jenkins_home` esta montado en Jenkins y en DinD para que los comandos `docker run -v "$PWD:/app"` funcionen cuando `$PWD` esta bajo `/var/jenkins_home/workspace`.
- Jenkins y el registry se conectan a la red Docker de Minikube para que Jenkins alcance el API server y Minikube pueda descargar imagenes `registry:5000/...`.
