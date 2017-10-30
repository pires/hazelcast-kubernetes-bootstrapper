.PHONY: build push all

ECR=674466932943.dkr.ecr.ap-southeast-2.amazonaws.com

ARTIFACT_ID=hazelcast-kubernetes-bootstrapper
ARTIFACT_VERSION=3.8.5

DOCKER_NAME=hazelcast-kubernetes-bootstrapper
DOCKER_TAG=3.8.5

build:
		cd .. \
		&& mvn --batch-mode package \
		&& cp target/$(ARTIFACT_ID)-$(ARTIFACT_VERSION).jar build/app.jar \
		&& cd build \
		&& docker build -t $(ECR)/$(DOCKER_NAME):$(DOCKER_TAG) .

push:
		$$(aws ecr get-login --no-include-email) \
		&& docker push $(ECR)/$(DOCKER_NAME):$(DOCKER_TAG)

all: build push
