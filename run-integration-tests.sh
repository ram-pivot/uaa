#!/bin/bash

set -xeu
UAA_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

CONTAINER_UAA_DIR='/root/uaa'
CONTAINER_GRADLE_LOCK_DIR="${CONTAINER_UAA_DIR}.gradle/"

case "$1" in
    hsqldb)
        DB_IMAGE_NAME=postgresql # we don't have a container image for hsqldb, and can use any image
        DB=hsqldb
        PROFILE_NAME=hsqldb
        ;;

    percona)
        DB_IMAGE_NAME=percona
        DB=percona
        PROFILE_NAME=mysql
        ;;

    postgresql|mysql)
        DB_IMAGE_NAME=$1
        DB=$1
        PROFILE_NAME=$1
        ;;

    *)
        echo $"ERROR: $1 is not a known database type. Supported types are: hsqldb, percona, postgresql, mysql"
        exit 1
esac

if [[ -z "${DOCKER_IMAGE+x}" ]]; then
    DOCKER_IMAGE="cfidentity/uaa-${DB_IMAGE_NAME}"
fi
echo "Using docker image: ${DOCKER_IMAGE}"
docker pull ${DOCKER_IMAGE}
docker run --privileged -t -i --shm-size=1G \
  -v "${UAA_DIR}":"${CONTAINER_UAA_DIR}" \
  -v "${CONTAINER_GRADLE_LOCK_DIR}" \
  --env DB=${DB} \
  "${DOCKER_IMAGE}" \
  /root/uaa/scripts/integration-tests.sh "${PROFILE_NAME}",default "${CONTAINER_UAA_DIR}"
