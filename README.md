hazelcast-kubernetes-bootstrapper
=================================

Hazelcast (3.8.5) cluster discovery mechanism for Kubernetes.

## What is

[Kubernetes](http://kubernetes.io) is an open source orchestration system for Docker containers. It handles scheduling onto nodes in a compute cluster and actively manages workloads to ensure that their state matches the users declared intentions. Using the concepts of "labels" and "pods", it groups the containers which make up an application into logical units for easy management and discovery.

In order to cluster Hazelcast in Kubernetes - and harness full scale-up/down capabilities provided -, each instance must know beforehand which ```pods``` containing Hazelcast instances are already up & running, so that networking may be configured properly. This is achieved by means of looking-up ```pods``` with certain ```labels``` in [Kubernetes API](https://github.com/GoogleCloudPlatform/kubernetes/blob/master/docs/accessing_the_api.md).
The code provided does that and configures Hazelcast TCP clustering.

To configure Hazelcast inside of the Kubernetes cluster the following environment options can be used:

* `HAZELCAST_SERVICE` - name of the Hazelcast service, declared in the Kubernetes service configuration. Default: `hazelcast`.
* `DNS_DOMAIN` - domain name used inside of the cluster. Default: `cluster.local`.
* `POD_NAMESPACE` - namespace in which hazelcast should be running. Default: `default`. Use the [Downward API](https://github.com/GoogleCloudPlatform/kubernetes/blob/master/docs/downward_api.md) to set it automatically.
* `HC_GROUP_NAME` - Hazelcast group name. Default: `someGroup`.
* `HC_GROUP_PASSWORD` - Hazelcast group password. Default: `someSecret`.
* `HC_PORT` - Port on which Hazelcast should be running.
* `HC_REST_ENABLED` - Whether to enable Hazelcast REST API. Default: `false`.

This is used in [pires/hazelcast-kubernetes](https://github.com/pires/hazelcast-kubernetes).

## Pre-requisites

* JDK 8
* Maven 3.0.5 or newer

## Build

```
mvn clean package
```
