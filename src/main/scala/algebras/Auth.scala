package algebras

import domain.{User, LoginRequest, RegisterRequest, AppError}

trait Auth[F[_]]:
  def register(req: RegisterRequest): F[Either[AppError, User]]
  def login(req: LoginRequest): F[Either[AppError, String]]  // JWT token
  def validateToken(token: String): F[Option[User]]