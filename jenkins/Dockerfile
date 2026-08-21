# Jenkins controller image with the Docker CLI added.
#
# jenkins/jenkins:lts-jdk25 does NOT ship a docker binary. Mounting the host's
# /var/run/docker.sock into the container gives access to the daemon, but the
# "which docker" check in every Jenkinsfile's Build Docker Image stage still
# fails because there is nothing on PATH to find — that's why the stage is
# reported as "skipped due to when conditional".
#
# DOCKER_GID must match the GID of the "docker" group on the HOST running this
# container (find it with: getent group docker), since the bind-mounted socket
# enforces permissions by that numeric GID, not by group name.
FROM jenkins/jenkins:lts-jdk25
USER root

ARG DOCKER_GID=999

RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates curl gnupg && \
    install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc && \
    chmod a+r /etc/apt/keyrings/docker.asc && \
    . /etc/os-release && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian ${VERSION_CODENAME} stable" \
        > /etc/apt/sources.list.d/docker.list && \
    apt-get update && \
    apt-get install -y --no-install-recommends docker-ce-cli docker-buildx-plugin && \
    rm -rf /var/lib/apt/lists/*

# Create (or reuse) a group with the host's docker GID and add jenkins to it,
# so the jenkins user can read/write the bind-mounted socket.
RUN (getent group ${DOCKER_GID} || groupadd -g ${DOCKER_GID} docker-host) && \
    usermod -aG ${DOCKER_GID} jenkins

USER jenkins
