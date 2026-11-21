name := """auth-service"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.18"

libraryDependencies += guice

// Database
libraryDependencies += "com.microsoft.sqlserver" % "mssql-jdbc" % "12.2.0.jre11"
libraryDependencies += javaJdbc

// Đợt 1: Cổng mặc định cho auth-service (9001, instance 1), instance 2 chạy 9011
PlayKeys.playDefaultPort := 9001
