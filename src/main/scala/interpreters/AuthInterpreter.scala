package interpreters

import cats.effect.IO
import algebras.Auth
import domain.{User, LoginRequest, RegisterRequest, AppError}
import pdi.jwt.{JwtCirce, JwtAlgorithm, JwtClaim}
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID
import scala.collection.concurrent.TrieMap
import io.circe.parser.*
import scala.io.Source
import java.io.PrintWriter
import java.nio.file.{Files, Paths}

class AuthInterpreter(usersFile: String, jwtSecret: String) extends Auth[IO]:

  private val users: TrieMap[UUID, User] = loadUsers()

  private def loadUsers(): TrieMap[UUID, User] =
    val path = Paths.get(usersFile)
    if Files.exists(path) then
      val json = Source.fromFile(usersFile).mkString
      decode[List[User]](json).getOrElse(Nil)
        .foldLeft(TrieMap.empty[UUID, User])((m, u) => m += (u.id -> u))
    else TrieMap.empty

  private def saveUsers(): Unit =
    val json = users.values.toList.asJson.spaces2
    val pw = PrintWriter(usersFile)
    pw.write(json)
    pw.close()

  def register(req: RegisterRequest): IO[Either[AppError, User]] = IO {
    users.values.find(_.login == req.login) match
      case Some(_) => Left(AppError.UserAlreadyExists)
      case None =>
        val user = User(
          id       = UUID.randomUUID,
          login    = req.login,
          password = BCrypt.hashpw(req.password, BCrypt.gensalt())
        )
        users += (user.id -> user)
        saveUsers()
        Right(user)
  }

  def login(req: LoginRequest): IO[Either[AppError, String]] = IO {
    users.values.find(_.login == req.login) match
      case None => Left(AppError.InvalidCredentials)
      case Some(user) if !BCrypt.checkpw(req.password, user.password) =>
        Left(AppError.InvalidCredentials)
      case Some(user) =>
        val claim = JwtClaim(
          subject = Some(user.id.toString),
          content = user.asJson.noSpaces
        )
        val token = JwtCirce.encode(claim, jwtSecret, JwtAlgorithm.HS256)
        Right(token)
  }

  def validateToken(token: String): IO[Option[User]] = IO {
    JwtCirce.decode(token, jwtSecret, Seq(JwtAlgorithm.HS256)) match
      case util.Success(claim) => decode[User](claim.content).toOption
      case _ => None
  }