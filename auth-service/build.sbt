name := """auth-service"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.18"
javacOptions ++= Seq("-source", "17", "-target", "17")

libraryDependencies += guice

// Thư viện Consul để tìm kiếm và đăng ký dịch vụ
libraryDependencies += "com.ecwid.consul" % "consul-api" % "1.4.5"

// Cơ sở dữ liệu
libraryDependencies += "com.microsoft.sqlserver" % "mssql-jdbc" % "12.2.0.jre11"
libraryDependencies += javaJdbc

// BCrypt để băm mật khẩu
libraryDependencies += "at.favre.lib" % "bcrypt" % "0.10.2"

// JWT
libraryDependencies += "com.auth0" % "java-jwt" % "4.4.0"

// Bộ nhớ đệm
libraryDependencies += caffeine

// HTTP client để giao tiếp giữa các dịch vụ
libraryDependencies += javaWs

// Validation - Dùng Hibernate Validator 6.x (javax.validation) để tương thích Play
libraryDependencies += "org.hibernate.validator" % "hibernate-validator" % "6.2.5.Final"
libraryDependencies += "javax.validation" % "validation-api" % "2.0.1.Final"
libraryDependencies += "org.glassfish" % "javax.el" % "3.0.0"

// Các thư viện kiểm thử
libraryDependencies += "org.junit.jupiter" % "junit-jupiter" % "5.10.0" % Test
libraryDependencies += "org.junit.jupiter" % "junit-jupiter-api" % "5.10.0" % Test
libraryDependencies += "org.junit.jupiter" % "junit-jupiter-engine" % "5.10.0" % Test
libraryDependencies += "org.junit.platform" % "junit-platform-launcher" % "1.10.0" % Test
libraryDependencies += "org.junit.vintage" % "junit-vintage-engine" % "5.10.0" % Test
libraryDependencies += "org.mockito" % "mockito-core" % "5.5.0" % Test
libraryDependencies += "org.mockito" % "mockito-junit-jupiter" % "5.5.0" % Test
libraryDependencies += "org.assertj" % "assertj-core" % "3.24.2" % Test

// Cổng mặc định cho auth-service (9001, instance 1), instance 2 chạy 9011
PlayKeys.playDefaultPort := 9001

jacocoIncludes := Seq("services.*")
jacocoReportSettings := JacocoReportSettings()
  .withTitle("Auth Service - Service Coverage")
  .withThresholds(JacocoThresholds(line = 80))
