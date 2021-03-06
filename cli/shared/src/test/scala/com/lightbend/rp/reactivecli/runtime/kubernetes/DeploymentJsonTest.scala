/*
 * Copyright 2017 Lightbend, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lightbend.rp.reactivecli.runtime.kubernetes

import argonaut._
import com.lightbend.rp.reactivecli.annotations.kubernetes._
import com.lightbend.rp.reactivecli.annotations._
import com.lightbend.rp.reactivecli.argparse._
import com.lightbend.rp.reactivecli.concurrent._
import scala.collection.immutable.Seq
import utest._

import Argonaut._

object DeploymentJsonTest extends TestSuite {
  import Deployment._

  val endpoints = Map(
    "ep1" -> HttpEndpoint(0, "ep1", 0, Seq(HttpIngress(Seq(80, 443), Seq.empty, Seq("^/.*")))),
    "ep2" -> TcpEndpoint(1, "ep2", 1234),
    "ep3" -> UdpEndpoint(2, "ep3", 0))

  val annotations = Annotations(
    namespace = Some("chirper"),
    appName = Some("friendimpl"),
    appType = Some("basic"),
    configResource = Some("my-config.conf"),
    diskSpace = Some(65536L),
    memory = Some(8192L),
    cpu = Some(0.5D),
    endpoints = endpoints,
    secrets = Seq(Secret("acme.co", "my-secret")),
    privileged = true,
    environmentVariables = Map(
      "testing1" -> LiteralEnvironmentVariable("testingvalue1")),
    version = Some("3.2.1-SNAPSHOT"),
    modules = Set.empty,
    akkaClusterBootstrapSystemName = None)

  val imageName = "my-repo/my-image"

  val tests = this{
    "json serialization" - {
      "deployment" - {
        "deploymentType" - {
          "Canary" - {
            Deployment
              .generate(annotations, "apps/v1beta2", imageName, Deployment.ImagePullPolicy.Never, noOfReplicas = 1, Map.empty, CanaryDeploymentType, None)
              .toOption
              .get
              .payload
              .map(j =>
                assert(
                  (j.hcursor --\ "metadata" --\ "name")
                    .focus
                    .contains(jString("friendimpl-v3-2-1-snapshot"))))
          }

          "BlueGreen" - {
            Deployment
              .generate(annotations, "v1", imageName, Deployment.ImagePullPolicy.Never, noOfReplicas = 1, Map.empty, BlueGreenDeploymentType, None)
              .toOption
              .get
              .payload
              .map(j =>
                assert(
                  (j.hcursor --\ "metadata" --\ "name")
                    .focus
                    .contains(jString("friendimpl-v3-2-1-snapshot"))))
          }

          "Rolling" - {
            Deployment
              .generate(annotations, "v1", imageName, Deployment.ImagePullPolicy.Never, noOfReplicas = 1, Map.empty, RollingDeploymentType, None)
              .toOption
              .get
              .payload
              .map(j =>
                assert(
                  (j.hcursor --\ "metadata" --\ "name")
                    .focus
                    .contains(jString("friendimpl"))))
          }
        }

        "K8" - {
          val expectedJson =
            """
              |{
              |  "apiVersion": "apps/v1beta2",
              |  "kind": "Deployment",
              |  "metadata": {
              |    "labels": {
              |      "appName": "friendimpl",
              |      "appNameVersion": "friendimpl-v3.2.1-SNAPSHOT"
              |    },
              |    "name": "friendimpl-v3.2.1-SNAPSHOT",
              |    "namespace": "chirper"
              |  },
              |  "spec": {
              |    "replicas": 1,
              |    "serviceName": "friendimpl",
              |    "template": {
              |      "appName": "friendimpl",
              |      "appNameVersion": "friendimpl-v3.2.1-SNAPSHOT"
              |    },
              |    "spec": {
              |      "containers": [
              |        {
              |          "name": "friendimpl",
              |          "image": "my-repo/my-image",
              |          "imagePullPolicy": "Never",
              |          "ports": [
              |            {
              |              "containerPort": 10000,
              |              "name": "ep1"
              |            },
              |            {
              |              "containerPort": 1234,
              |              "name": "ep2"
              |            },
              |            {
              |              "containerPort": 10001,
              |              "name": "ep3"
              |            }
              |          ],
              |          "env": [
              |            {
              |              "name": "RP_APP_NAME",
              |              "value": "friendimpl"
              |            },
              |            {
              |              "name": "RP_JAVA_OPTS",
              |              "value": "-Dakka.cluster.bootstrap.contact-point-discovery.discovery-method=akka.discovery.reactive-lib-kubernetes -Dconfig.resource=my-config.conf -Dakka.cluster.bootstrap.contact-point-discovery.required-contact-point-nr=1"
              |            },
              |            {
              |              "name": "RP_ENDPOINTS",
              |              "value": "EP1,EP2,EP3"
              |            },
              |            {
              |              "name": "RP_ENDPOINTS_COUNT",
              |              "value": "3"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_BIND_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_0_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_BIND_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_1_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_BIND_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_2_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1_BIND_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP1_PORT",
              |              "value": "10000"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2_BIND_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP2_PORT",
              |              "value": "1234"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_BIND_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_BIND_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_HOST",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_ENDPOINT_EP3_PORT",
              |              "value": "10001"
              |            },
              |            {
              |              "name": "RP_KUBERNETES_POD_IP",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "status.podIP"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_KUBERNETES_POD_NAME",
              |              "valueFrom": {
              |                "fieldRef": {
              |                  "fieldPath": "metadata.name"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_NAMESPACE",
              |              "value": "chirper"
              |            },
              |            {
              |              "name": "RP_PLATFORM",
              |              "value": "kubernetes"
              |            },
              |            {
              |              "name": "RP_SECRETS_ACME_CO_MY_SECRET",
              |              "valueFrom": {
              |                "secretKeyRef": {
              |                  "name": "acme.co",
              |                  "key": "my-secret"
              |                }
              |              }
              |            },
              |            {
              |              "name": "RP_APP_VERSION",
              |              "value": "3.2.1-SNAPSHOT"
              |            },
              |            {
              |              "name": "testing1",
              |              "value": "testingvalue1"
              |            }
              |          ]
              |        }
              |      ]
              |    }
              |  }
              |}
            """.stripMargin.parse.right.get

          val result = Deployment.generate(annotations, "apps/v1beta2", imageName,
            Deployment.ImagePullPolicy.Never, noOfReplicas = 1, Map.empty, CanaryDeploymentType, None).toOption.get

          // @TODO uncomment this test when we actually have the right format generated
          // @TODO i am proposing keeping them updated for now is counter-productive
          //assert(result == Deployment("friendimpl-v3.2.1-SNAPSHOT", expectedJson))
        }

        "should fail if application name is not defined" - {
          val invalid = annotations.copy(appName = None)
          assert(Deployment.generate(invalid, "apps/v1beta2", imageName,
            Deployment.ImagePullPolicy.Never, 1, Map.empty, CanaryDeploymentType, None).toOption.isEmpty)
        }

        "jq" - {
          Deployment
            .generate(annotations, "apps/v1beta2", imageName, Deployment.ImagePullPolicy.Never, 1, Map.empty, CanaryDeploymentType, Some(".jqTest = \"test\""))
            .toOption
            .get
            .payload
            .map(j => assert((j.hcursor --\ "jqTest").focus.contains(jString("test"))))
        }

        "resources" - {
          val expectedJson =
            """
              |{
              |  "limits": {
              |    "cpu": 0.500000,
              |    "memory": 8192
              |  },
              |  "request": {
              |    "cpu": 0.500000,
              |    "memory": 8192
              |  }
              |}
            """.stripMargin.parse.right.get

          val generatedJson =
            Deployment
              .generate(annotations, "apps/v1beta2", imageName, Deployment.ImagePullPolicy.Never, 1, Map.empty, CanaryDeploymentType, None)
              .toOption
              .get
              .payload
              .map(j =>
                assert(expectedJson == (j.hcursor --\ "spec" --\ "template" --\ "spec" --\ "containers")
                  .downArray
                  .first
                  .downField("resources")
                  .focus
                  .get))
        }
      }

      "environment" - {
        "literal" - {
          val env = LiteralEnvironmentVariable("hey")
          val expectedJson =
            """
              |{
              |  "value": "hey"
              |}
            """.stripMargin.parse.right.get
          val generatedJson = env.asJson

          assert(expectedJson == generatedJson)
        }

        "field ref" - {
          val env = FieldRefEnvironmentVariable("metadata.name")
          val expectedJson =
            """
              |{
              |  "valueFrom": {
              |    "fieldRef": {
              |      "fieldPath": "metadata.name"
              |    }
              |  }
              |}
            """.stripMargin.parse.right.get
          val generatedJson = env.asJson

          assert(expectedJson == generatedJson)
        }

        "config map" - {
          val env = ConfigMapEnvironmentVariable(mapName = "special-config", key = "s3.bucket")
          val expectedJson =
            """
              |{
              |  "valueFrom": {
              |    "configMapKeyRef": {
              |      "name": "special-config",
              |      "key": "s3.bucket"
              |    }
              |  }
              |}
            """.stripMargin.parse.right.get
          val generatedJson = env.asJson

          assert(expectedJson == generatedJson)
        }

      }

      "assigned endpoint" - {
        "http" - {
          val endpoint = HttpEndpoint(0, "ep1", 0, ingress = Seq.empty)
          val assigned = AssignedPort(
            endpoint = endpoint,
            port = 9999)
          val expectedJson =
            """
              |{
              |  "containerPort": 9999,
              |  "name": "ep1"
              |}
            """.stripMargin.parse.right.get
          val generatedJson = assigned.asJson

          assert(expectedJson == generatedJson)
        }

        "tcp" - {
          val endpoint = TcpEndpoint(0, "ep1", 0)
          val assigned = AssignedPort(
            endpoint = endpoint,
            port = 9999)
          val expectedJson =
            """
              |{
              |  "containerPort": 9999,
              |  "name": "ep1"
              |}
            """.stripMargin.parse.right.get
          val generatedJson = assigned.asJson

          assert(expectedJson == generatedJson)
        }

        "udp" - {
          val endpoint = UdpEndpoint(0, "ep1", 0)
          val assigned = AssignedPort(
            endpoint = endpoint,
            port = 9999)
          val expectedJson =
            """
              |{
              |  "containerPort": 9999,
              |  "name": "ep1"
              |}
            """.stripMargin.parse.right.get
          val generatedJson = assigned.asJson

          assert(expectedJson == generatedJson)
        }

      }

      "resource limits" - {
        "all" - {
          val limits = ResourceLimits(Some(0.1), Some(200L))
          val expectedJson =
            """
              |{
              |  "resources": {
              |    "limits": {
              |      "cpu":0.100000,
              |      "memory":200
              |    },
              |    "request": {
              |      "cpu":0.100000,
              |      "memory":200
              |    }
              |   }
              |}
            """.stripMargin.parse.right.get

          val generatedJson = limits.asJson

          assert(expectedJson == generatedJson)
        }

        "memory only" - {
          val limits = ResourceLimits(None, Some(200L))
          val expectedJson =
            """
              |{
              |  "resources": {
              |    "limits": {
              |      "memory":200
              |    },
              |    "request": {
              |      "memory":200
              |    }
              |   }
              |}
            """.stripMargin.parse.right.get

          val generatedJson = limits.asJson

          assert(expectedJson == generatedJson)
        }

        "cpu only" - {
          val limits = ResourceLimits(Some(2.5), None)
          val expectedJson =
            """
              |{
              |  "resources": {
              |    "limits": {
              |      "cpu":2.50000
              |    },
              |    "request": {
              |      "cpu":2.50000
              |    }
              |   }
              |}
            """.stripMargin.parse.right.get

          val generatedJson = limits.asJson

          assert(expectedJson == generatedJson)
        }

        "none" - {
          val limits = ResourceLimits(None, None)
          val expectedJson = "{}".parse.right.get

          val generatedJson = limits.asJson

          assert(expectedJson == generatedJson)
        }
      }
    }

    "RP environment variables" - {
      "namespace" - {
        "when present" - {
          val result = RpEnvironmentVariables.namespaceEnv(Some("ns"))
          val expectedResult = Map(
            "RP_NAMESPACE" -> LiteralEnvironmentVariable("ns"))
          assert(result == expectedResult)
        }

        "when not present" - {
          val result = RpEnvironmentVariables.namespaceEnv(None)
          assert(result.isEmpty)

        }
      }

      "app name" - {
        "when present" - {
          val result = RpEnvironmentVariables.appNameEnvs(Some("app"))
          val expectedResult = Map(
            "RP_APP_NAME" -> LiteralEnvironmentVariable("app"))
          assert(result == expectedResult)
        }

        "when not present" - {
          val result = RpEnvironmentVariables.appNameEnvs(None)
          assert(result.isEmpty)
        }
      }

      "versions" - {
        "all fields" - {
          val result = RpEnvironmentVariables.versionEnvs("3.2.1-SNAPSHOT")
          val expectedResult = Map(
            "RP_APP_VERSION" -> LiteralEnvironmentVariable("3.2.1-SNAPSHOT"))
          assert(result == expectedResult)
        }

        "major + minor + patch" - {
          val result = RpEnvironmentVariables.versionEnvs("3.2.1")
          val expectedResult = Map(
            "RP_APP_VERSION" -> LiteralEnvironmentVariable("3.2.1"))
          assert(result == expectedResult)
        }
      }

      "endpoints" - {
        "when present" - {
          val endpoints = Map(
            "ep1" -> HttpEndpoint(0, "ep1", 0, Seq.empty),
            "ep2" -> TcpEndpoint(1, "ep2", 1234),
            "ep3" -> UdpEndpoint(2, "ep3", 1234))

          "envs" - {
            val result = RpEnvironmentVariables.endpointEnvs(endpoints)
            val expectedResult = Map(
              "RP_ENDPOINTS_COUNT" -> LiteralEnvironmentVariable("3"),
              "RP_ENDPOINTS" -> LiteralEnvironmentVariable("EP1,EP2,EP3"),

              "RP_ENDPOINT_EP1_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP1_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_EP1_BIND_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_EP1_PORT" -> LiteralEnvironmentVariable("10000"),

              "RP_ENDPOINT_EP2_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP2_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_EP2_BIND_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_EP2_PORT" -> LiteralEnvironmentVariable("1234"),

              "RP_ENDPOINT_EP3_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP3_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_EP3_BIND_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_EP3_PORT" -> LiteralEnvironmentVariable("1234"),

              "RP_ENDPOINT_0_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_0_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_0_BIND_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_0_PORT" -> LiteralEnvironmentVariable("10000"),

              "RP_ENDPOINT_1_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_1_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_1_BIND_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_1_PORT" -> LiteralEnvironmentVariable("1234"),

              "RP_ENDPOINT_2_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_2_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_2_BIND_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_2_PORT" -> LiteralEnvironmentVariable("1234"))

            assert(result == expectedResult)
          }

          "endpoints list should be ordered based on endpoint index" - {
            val endpoints = Map(
              "ep1" -> HttpEndpoint(2, "ep1", 0, Seq.empty),
              "ep2" -> TcpEndpoint(0, "ep2", 1234),
              "ep3" -> UdpEndpoint(1, "ep3", 1234))

            val result = RpEnvironmentVariables.endpointEnvs(endpoints)

            assert(result("RP_ENDPOINTS") == LiteralEnvironmentVariable("EP2,EP3,EP1"))
          }

          "auto port should be allocated for all undeclared ports" - {
            val endpoints = Map(
              "ep1" -> HttpEndpoint(0, "ep1", 0, Seq.empty),
              "ep2" -> TcpEndpoint(1, "ep2", 1234),
              "ep3" -> UdpEndpoint(2, "ep3", 0))

            val result = RpEnvironmentVariables.endpointEnvs(endpoints)

            val expectedResult = Map(
              "RP_ENDPOINTS_COUNT" -> LiteralEnvironmentVariable("3"),
              "RP_ENDPOINTS" -> LiteralEnvironmentVariable("EP1,EP2,EP3"),

              "RP_ENDPOINT_EP1_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP2_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP3_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_0_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_1_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_2_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_EP1_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP2_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_EP3_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_0_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_1_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),
              "RP_ENDPOINT_2_BIND_HOST" -> FieldRefEnvironmentVariable("status.podIP"),

              "RP_ENDPOINT_EP1_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_EP2_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_EP3_PORT" -> LiteralEnvironmentVariable("10001"),
              "RP_ENDPOINT_0_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_1_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_2_PORT" -> LiteralEnvironmentVariable("10001"),

              "RP_ENDPOINT_EP1_BIND_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_EP2_BIND_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_EP3_BIND_PORT" -> LiteralEnvironmentVariable("10001"),
              "RP_ENDPOINT_0_BIND_PORT" -> LiteralEnvironmentVariable("10000"),
              "RP_ENDPOINT_1_BIND_PORT" -> LiteralEnvironmentVariable("1234"),
              "RP_ENDPOINT_2_BIND_PORT" -> LiteralEnvironmentVariable("10001"))

            assert(result == expectedResult)
          }
        }

        "when empty" - {
          val result = RpEnvironmentVariables.endpointEnvs(Map.empty)
          val expectedResult = Map(
            "RP_ENDPOINTS_COUNT" -> LiteralEnvironmentVariable("0"))

          assert(result == expectedResult)
        }
      }

      "mergeEnvs" - {
        val result =
          RpEnvironmentVariables.mergeEnvs(
            Map("PATH" -> LiteralEnvironmentVariable("/bin")),
            Map("PATH" -> LiteralEnvironmentVariable("/usr/bin")),
            Map("RP_JAVA_OPTS" -> LiteralEnvironmentVariable("-Dmy.arg=hello")),
            Map("RP_JAVA_OPTS" -> LiteralEnvironmentVariable("-Dmy.other.arg=hello2")))

        val expectedResult = Map(
          "PATH" -> LiteralEnvironmentVariable("/usr/bin"),
          "RP_JAVA_OPTS" -> LiteralEnvironmentVariable("-Dmy.arg=hello -Dmy.other.arg=hello2"))

        assert(result == expectedResult)
      }
    }
  }
}
