name := """api-gateway"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.18"

libraryDependencies += guice

// WS Client for HTTP proxy
libraryDependencies += ws

// Đợt 1: Cổng mặc định cho api-gateway (9000), tránh trùng các service
PlayKeys.playDefaultPort := 9000
