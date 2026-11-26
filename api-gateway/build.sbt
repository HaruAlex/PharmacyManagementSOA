name := """api-gateway"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.18"

Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "shared" / "src" / "main" / "java"

libraryDependencies += guice

// WS Client for HTTP proxy
libraryDependencies += ws

// Consul Client - Service Discovery (Đợt 2)
libraryDependencies += "com.ecwid.consul" % "consul-api" % "1.4.5"
libraryDependencies += "com.auth0" % "java-jwt" % "4.4.0"

libraryDependencies += "org.junit.jupiter" % "junit-jupiter" % "5.10.0" % Test
libraryDependencies += "org.junit.platform" % "junit-platform-launcher" % "1.10.0" % Test
libraryDependencies += "org.mockito" % "mockito-core" % "5.5.0" % Test
libraryDependencies += "org.assertj" % "assertj-core" % "3.24.2" % Test

// Đợt 1: Cổng mặc định cho api-gateway (9000), tránh trùng các service
PlayKeys.playDefaultPort := 9000
