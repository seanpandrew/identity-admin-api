lazy val `identity-admin-api` = (project in file("identity-admin-api")).enablePlugins(PlayScala, BuildInfoPlugin)

lazy val root = (project in file(".")).aggregate(`identity-admin-api`)
