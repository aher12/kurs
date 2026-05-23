package services

import cats.effect.IO
import algebras.Auth
import domain.{LoginRequest, RegisterRequest, AppError, User}

class AuthService(auth: Auth[IO]):

  def register(login: String, password: String): IO[Either[AppError, User]] =
    if login.trim.isEmpty || password.trim.isEmpty then
      IO.pure(Left(AppError.InternalError("Login and password required")))
    else auth.register(RegisterRequest(login.trim, password))

  def login(login: String, password: String): IO[Either[AppError, String]] =
    auth.login(LoginRequest(login.trim, password))

  def checkToken(token: String): IO[Option[User]] =
    auth.validateToken(token)