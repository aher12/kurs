package config

final case class ServerConfig(
                               host:            String,
                               port:            Int,
                               storageDir:      String,
                               usersFile:       String,
                               metaFile:        String,
                               jwtSecret:       String,
                               keystorePath:    String,
                               keystorePass:    String,
                               keyPass:         String,
                               cookieName:      String,
                               defaultFileName: String,
                               fileFieldName:   String,
                               passwordFieldName: String
                             )

object ServerConfig:
  val default: ServerConfig = ServerConfig(
    host              = "0.0.0.0",
    port              = 8080,
    storageDir        = "./storage",
    usersFile         = "./users.json",
    metaFile          = "./storage/meta.json",
    jwtSecret         = "change-me-in-production-1234567890",
    keystorePath      = "keystore.p12",
    keystorePass      = "123456",
    keyPass           = "123456",
    cookieName        = "jwt",
    defaultFileName   = "unnamed",
    fileFieldName     = "file",
    passwordFieldName = "password"
  )