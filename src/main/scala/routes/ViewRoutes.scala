package routes

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.implicits.uri
import org.http4s.headers.Location

class ViewRoutes:

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "login"    => html.loginPage(None)
    case GET -> Root / "register" => html.registerPage(None)
    case GET -> Root              => SeeOther(Location(uri"/files"))
    case GET -> Root / "logout"   => logout
  }

  private def logout: IO[Response[IO]] =
    SeeOther(Location(uri"/login"))
      .map(_.addCookie(ResponseCookie("jwt", "", path = Some("/"), maxAge = Some(0))))