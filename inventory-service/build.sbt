name := """inventory-service"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.18"

Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "shared" / "src" / "main" / "java"

libraryDependencies += guice

// Consul Client - Service Discovery & Registration (Đợt 2)
libraryDependencies += "com.ecwid.consul" % "consul-api" % "1.4.5"

// Database
libraryDependencies += "com.microsoft.sqlserver" % "mssql-jdbc" % "12.2.0.jre11"
libraryDependencies += javaJdbc


// Unit Testing - Giả lập DB/Connection khi chạy test độc lập
libraryDependencies += "org.mockito" % "mockito-core" % "5.11.0" % Test



// Đợt 1: Cổng mặc định cho inventory-service (9003, instance 1), instance 2 chạy 9013
PlayKeys.playDefaultPort := 9003
