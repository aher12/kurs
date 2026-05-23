package main

import cats.effect.{IO, IOApp}
import cats.syntax.semigroupk.*
import config.ServerConfig
import interpreters.{AuthInterpreter, FileStorageInterpreter}
import routes.{AuthRoutes, FileRoutes, ViewRoutes}
import org.http4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger
import com.comcast.ip4s.*
import java.io.File

object Main extends IOApp.Simple:

  def run: IO[Unit] =
    val cfg = ServerConfig.default
    new File(cfg.storageDir).mkdirs()

    val auth    = AuthInterpreter(cfg.usersFile, cfg.jwtSecret)
    val storage = FileStorageInterpreter(cfg.storageDir, s"${cfg.storageDir}/meta.json")

    val viewRoutes  = ViewRoutes().routes
    val authRoutes  = AuthRoutes(auth).routes
    val fileRoutes  = FileRoutes(storage, auth).routes

    val app = Logger.httpApp(true, true)(
      (viewRoutes <+> authRoutes <+> fileRoutes).orNotFound
    )

    EmberServerBuilder.default[IO]
      .withHost(Host.fromString(cfg.host).get)
      .withPort(Port.fromInt(cfg.port).get)
      .withHttpApp(app)
      .build
      .use(_ => IO.never)