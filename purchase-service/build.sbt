name := """purchase-service"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.18"

libraryDependencies += guice

// Consul Client - Service Discovery & Registration (Đợt 2)
libraryDependencies += "com.ecwid.consul" % "consul-api" % "1.4.5"

// Database
libraryDependencies += "com.microsoft.sqlserver" % "mssql-jdbc" % "12.2.0.jre11"
libraryDependencies += javaJdbc

// Đợt 1: Cổng mặc định cho purchase-service (9004, instance 1), instance 2 chạy 9014
PlayKeys.playDefaultPort := 9004
