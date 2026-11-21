name := """report-service"""
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

// Report Generation
libraryDependencies += "org.apache.poi" % "poi" % "5.2.3"
libraryDependencies += "org.apache.poi" % "poi-ooxml" % "5.2.3"
libraryDependencies += "com.itextpdf" % "itextpdf" % "5.5.13.3"

// Đợt 1: Cổng mặc định cho report-service (9006, instance 1), instance 2 chạy 9016
PlayKeys.playDefaultPort := 9006
