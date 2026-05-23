package config

final case class ServerConfig(
                               host:       String,
                               port:       Int,
                               storageDir: String,
                               usersFile:  String,
                               jwtSecret:  String
                             )

object ServerConfig:
  val default: ServerConfig = ServerConfig(
    host       = "0.0.0.0",
    port       = 8080,
    storageDir = "./storage",
    usersFile  = "./users.json",
    jwtSecret  = "change-me-in-production-1234567890"
  )