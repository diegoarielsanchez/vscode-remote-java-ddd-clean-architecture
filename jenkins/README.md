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

## Start pipelines from a GitHub push (webhook)

Every `Jenkinsfile` declares `triggers { githubPush() }`, so a push can start
the pipelines without any change to them. GitHub cannot reach
`localhost:9696`, though, so a relay is needed in between. These steps use
[smee.io](https://smee.io), GitHub's relay for development: the `smee`
container connects out to smee.io and forwards only the webhook messages to
Jenkins, so Jenkins itself is never reachable from the internet.

1. Open https://smee.io, click **Start a new channel** and copy its address
   (e.g. `https://smee.io/AbC123xyz`). Treat it like a password: anyone who
   has it can watch the messages (commit messages, authors, file paths).

2. Run the relay next to Jenkins, on the same network so it can reach the
   `jenkins` container by name:
   ```bash
   docker run -d --name smee \
     --network ddd-clean-net \
     --restart unless-stopped \
     node:22-alpine \
     npx --yes smee-client \
       --url https://smee.io/AbC123xyz \
       --target http://jenkins:9696/github-webhook/

   docker logs smee   # should show "Connected"
   ```

3. In the GitHub repository, **Settings → Webhooks → Add webhook**:
   - **Payload URL:** the smee address
   - **Content type:** `application/json`
   - **Secret:** a long random string, e.g. from `openssl rand -hex 32`
   - **Events:** Just the push event

4. Give Jenkins the same secret, so nobody else who learns the smee address
   can start builds:
   - **Manage Jenkins → Credentials → System → Global → Add credentials**:
     **Secret text**, ID `github-webhook-secret`, the string from step 3.
   - **Manage Jenkins → System → GitHub → Advanced → Shared secrets**: add
     that credential.

5. Run each job once with **Build Now**. Jenkins only registers the
   `githubPush()` trigger after a pipeline has run; afterwards the job's
   configuration shows **GitHub hook trigger for GITScm polling** ticked.

6. Push a commit to `main` and check each job's **GitHub Hook Log** and build
   list. GitHub's **Recent Deliveries** shows 200 as soon as smee receives
   the message, even if Jenkins rejected it or never got it, so the Jenkins
   side is the one to trust.

Good to know:

- Every push builds all the jobs, even if only one service changed: the
  pipelines have no path filter.
- Pushes made while the `smee` container is stopped are not replayed.
  Redeliver them from **Recent Deliveries**, or use **Build Now**.
- smee.io is a free community service with no uptime promise; keep it for
  local development.
- **Why not ngrok?** ngrok (or Cloudflare Tunnel) publishes the whole Jenkins
  — login page, script console, CLI — not just the webhook. It does show
  Jenkins' real reply in Recent Deliveries and lets you open Jenkins from
  other devices, but then lock it down: strong passwords, sign-up off, and a
  tunnel policy that only allows `/github-webhook/` or adds basic auth or an
  IP allow-list. The target is the same: `http://localhost:9696/github-webhook/`
  from the host, with the trailing slash.
