package routes

import cats.effect.IO
import config.ServerConfig
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.implicits.uri
import org.http4s.headers.Location
import algebras.Auth
import domain.{LoginRequest, RegisterRequest, User, AppError}

class AuthRoutes(auth: Auth[IO], cfg: ServerConfig):

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ POST -> Root / "login"    => handleLogin(req)
    case req @ POST -> Root / "register" => handleRegister(req)
  }

  private def handleLogin(req: Request[IO]): IO[Response[IO]] =
    extractLogin(req).flatMap(auth.login).flatMap(loginResponse)

  private def handleRegister(req: Request[IO]): IO[Response[IO]] =
    extractRegister(req).flatMap(auth.register).flatMap(registerResponse)

  private def extractLogin(req: Request[IO]): IO[LoginRequest] =
    req.as[UrlForm].map { form =>
      LoginRequest(
        form.getFirstOrElse("login", ""),
        form.getFirstOrElse(cfg.passwordFieldName, "")
      )
    }

  private def extractRegister(req: Request[IO]): IO[RegisterRequest] =
    req.as[UrlForm].map { form =>
      RegisterRequest(
        form.getFirstOrElse("login", ""),
        form.getFirstOrElse(cfg.passwordFieldName, "")
      )
    }

  private def loginResponse(result: Either[AppError, String]): IO[Response[IO]] =
    result match
      case Left(error) => html.loginPage(Some(error.message))
      case Right(token) =>
        SeeOther(Location(uri"/files"))
          .map(_.addCookie(ResponseCookie(cfg.cookieName, token, path = Some("/"))))

  private def registerResponse(result: Either[AppError, User]): IO[Response[IO]] =
    result match
      case Left(error) => html.registerPage(Some(error.message))
      case Right(_)    => SeeOther(Location(uri"/login"))