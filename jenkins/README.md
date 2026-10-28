# Jenkins controller — Docker CLI setup

`jenkins/jenkins:lts-jdk25` has no `docker` binary, so mounting
`/var/run/docker.sock` alone is not enough to run the `Build Docker Image`
stage in each service's `Jenkinsfile` — that stage's `when` guard runs
`which docker` and skips itself when it's not found.

`Dockerfile` in this folder extends the base image with the Docker CLI and
grants the `jenkins` user access to the host's Docker socket.

## Build and run the controller

Run these **on the host where Jenkins runs** (a terminal outside this repo's
dev container), from the repository root.

1. Find the GID of the Docker socket as containers see it (the bind-mounted
   socket enforces permissions by this number, not by group name). This also
   works with Docker Desktop on WSL, where the host has no `docker` group:
   ```bash
   DOCKER_GID=$(docker run --rm -v /var/run/docker.sock:/var/run/docker.sock alpine stat -c %g /var/run/docker.sock)
   echo "$DOCKER_GID"
   ```

2. Build the image, passing that GID in:
   ```bash
   docker build --build-arg DOCKER_GID=$DOCKER_GID \
       -t jenkins-docker:lts-jdk25 \
       jenkins/
   ```

3. If a `jenkins` container already exists, remove it. `jenkins-data` is a
   named volume, not tied to the container, so this does not lose any Jenkins
   state (jobs, credentials, build history):
   ```bash
   docker rm -f jenkins
   ```

4. Create it from the new image. Jenkins listens on **9696**, not 8080:
   the API gateway already publishes 8080 on the host. The published port
   must match `--httpPort`, or the web page never loads:
   ```bash
   docker network inspect ddd-clean-net >/dev/null 2>&1 || docker network create ddd-clean-net

   docker run -d \
     --name jenkins \
     --network ddd-clean-net \
     -p 9696:9696 \
     -p 50000:50000 \
     -e JENKINS_OPTS="--httpPort=9696" \
     -v jenkins-data:/var/jenkins_home \
     -v /var/run/docker.sock:/var/run/docker.sock \
     --restart unless-stopped \
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

6. On first start, print the unlock password and open
   **http://localhost:9696**:
   ```bash
   docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
   ```
   Choose **Install suggested plugins** and create the admin user, then follow
   [CI with Jenkins](../README.md#ci-with-jenkins) for plugins, tools and jobs.

Once this is done, re-run any pipeline and the `Build Docker Image` stage
should execute instead of being skipped.

Day to day, `docker stop jenkins` / `docker start jenkins`; everything is kept
in the `jenkins-data` volume.
