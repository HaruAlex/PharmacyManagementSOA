name := """catalog-service"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.18"

libraryDependencies += guice

// Consul Client - Service Discovery & Registration
libraryDependencies += "com.ecwid.consul" % "consul-api" % "1.4.5"

// Database
libraryDependencies += "com.microsoft.sqlserver" % "mssql-jdbc" % "12.2.0.jre11"
libraryDependencies += javaJdbc
libraryDependencies += "com.h2database" % "h2" % "2.3.232" % Test

// Excel/CSV Processing
libraryDependencies += "org.apache.poi" % "poi" % "5.2.3"
libraryDependencies += "org.apache.poi" % "poi-ooxml" % "5.2.3"

// Đợt 1: Cổng mặc định cho catalog-service (9002, instance 1), instance 2 chạy 9012
PlayKeys.playDefaultPort := 9002
