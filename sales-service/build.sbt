name := """sales-service"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.18"

Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "shared" / "src" / "main" / "java"

libraryDependencies += guice

// WS Client - For HTTP calls to other services
libraryDependencies += ws

// Testing - JUnit 5 & Mockito
libraryDependencies += "org.junit.jupiter" % "junit-jupiter-api" % "5.9.3" % Test
libraryDependencies += "org.junit.jupiter" % "junit-jupiter-engine" % "5.9.3" % Test
libraryDependencies += "org.junit.jupiter" % "junit-jupiter-params" % "5.9.3" % Test
libraryDependencies += "org.mockito" % "mockito-core" % "5.3.1" % Test
libraryDependencies += "org.mockito" % "mockito-junit-jupiter" % "5.3.1" % Test

// SBT Test Framework - Use JUnit 5 Platform Suite instead of JUnit 4
libraryDependencies += "org.junit.platform" % "junit-platform-suite" % "1.9.3" % Test
libraryDependencies += "org.junit.platform" % "junit-platform-engine" % "1.9.3" % Test
testFrameworks += new TestFramework("org.junit.platform.suite.engine.JUnitPlatformSuiteEngine")

// Consul Client - Service Discovery & Registration (Đợt 2)
libraryDependencies += "com.ecwid.consul" % "consul-api" % "1.4.5"

// Database
libraryDependencies += "com.microsoft.sqlserver" % "mssql-jdbc" % "12.2.0.jre11"
libraryDependencies += javaJdbc

// Đợt 1: Cổng mặc định cho sales-service (9005, instance 1), instance 2 chạy 9015
PlayKeys.playDefaultPort := 9005
