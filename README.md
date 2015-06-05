hazelcast-kubernetes-bootstrapper
=================================

Hazelcast (3.4.2) cluster discovery mechanism for Kubernetes.

## What is

[Kubernetes](http://kubernetes.io) is an open source orchestration system for Docker containers. It handles scheduling onto nodes in a compute cluster and actively manages workloads to ensure that their state matches the users declared intentions. Using the concepts of "labels" and "pods", it groups the containers which make up an application into logical units for easy management and discovery.

In order to cluster Hazelcast in Kubernetes - and harness full scale-up/down capabilities provided -, each instance must know beforehand which ```pods``` containing Hazelcast instances are already up & running, so that networking may be configured properly. This is achieved by means of looking-up ```pods``` with certain ```labels``` in [Kubernetes API](https://github.com/GoogleCloudPlatform/kubernetes/blob/master/docs/accessing_the_api.md).
The code provided does that and configures Hazelcast TCP clustering.

This is used in [pires/hazelcast-kubernetes](https://github.com/pires/hazelcast-kubernetes).

## Pre-requisites

* JDK 8
* Maven 3.0.5 or newer

## Build

```
mvn clean package
```

## Run

```
java -jar target/hazelcast-kubernetes-bootstrapper-0.3.1-SNAPSHOT.jar
```
