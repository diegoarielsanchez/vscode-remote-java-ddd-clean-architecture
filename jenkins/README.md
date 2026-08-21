# Jenkins controller — Docker CLI setup

`jenkins/jenkins:lts-jdk25` has no `docker` binary, so mounting
`/var/run/docker.sock` alone is not enough to run the `Build Docker Image`
stage in each service's `Jenkinsfile` — that stage's `when` guard runs
`which docker` and skips itself when it's not found.

`Dockerfile` in this folder extends the base image with the Docker CLI and
grants the `jenkins` user access to the host's Docker socket.

## Rebuild and redeploy the controller

Run these **on the host where Jenkins runs**, not in this repo's dev
container.

1. Find the host's `docker` group GID (the bind-mounted socket enforces
   permissions by this number, not by group name):
   ```bash
   getent group docker
   # e.g. docker:x:999:
   ```

2. Build the image, passing that GID in:
   ```bash
   docker build --build-arg DOCKER_GID=<gid-from-step-1> \
       -t jenkins-docker:lts-jdk25 \
       jenkins/
   ```

3. Stop and remove the existing container. `jenkins-data` is a named volume,
   not tied to the container, so this does not lose any Jenkins state
   (jobs, credentials, build history):
   ```bash
   docker stop jenkins
   docker rm jenkins
   ```

4. Recreate it from the new image. Note the quote and line-continuation fix
   on `JENKINS_OPTS` versus the original command:
   ```bash
   docker run -d \
     --name jenkins \
     --network ddd-clean-net \
     -p 8080:8080 \
     -p 50000:50000 \
     -e JENKINS_OPTS="--httpPort=9696" \
     -v jenkins-data:/var/jenkins_home \
     -v /var/run/docker.sock:/var/run/docker.sock \
     jenkins-docker:lts-jdk25
   ```

5. Confirm the CLI is reachable from inside the container:
   ```bash
   docker exec jenkins which docker
   docker exec jenkins docker version
   ```
   `docker version` must print both a Client and a Server section — if only
   the Client section prints, the `jenkins` user still can't reach the
   socket (GID mismatch from step 1/2).

Once this is done, re-run any pipeline and the `Build Docker Image` stage
should execute instead of being skipped.
