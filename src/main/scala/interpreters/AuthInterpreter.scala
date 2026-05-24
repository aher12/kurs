package interpreters

import cats.effect.IO
import algebras.Auth
import config.ServerConfig
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

class AuthInterpreter(cfg: ServerConfig) extends Auth[IO]:

  private val users: TrieMap[UUID, User] = loadUsers()

  private def loadUsers(): TrieMap[UUID, User] =
    val path = Paths.get(cfg.usersFile)
    if Files.exists(path) then
      val json = Source.fromFile(cfg.usersFile).mkString
      val list = decode[List[User]](json).getOrElse(Nil)
      val pairs = list.map(u => u.id -> u)
      TrieMap.from(pairs)
    else TrieMap.empty

  private def saveUsers(): Unit =
    val json = users.values.toList.asJson.spaces2
    val pw = PrintWriter(cfg.usersFile)
    pw.write(json)
    pw.close()

  def register(req: RegisterRequest): IO[Either[AppError, User]] = IO {
    users.values.find(_.login == req.login) match
      case Some(_) => Left(AppError.UserAlreadyExists)
      case None    => Right(createUser(req.login, req.password))
  }

  def login(req: LoginRequest): IO[Either[AppError, String]] = IO {
    users.values.find(_.login == req.login) match
      case None                       => Left(AppError.InvalidCredentials)
      case Some(u) if !checkPw(req, u) => Left(AppError.InvalidCredentials)
      case Some(u)                    => Right(encodeToken(u))
  }

  def validateToken(token: String): IO[Option[User]] = IO {
    JwtCirce.decode(token, cfg.jwtSecret, Seq(JwtAlgorithm.HS256)) match
      case util.Success(claim) => decode[User](claim.content).toOption
      case _                   => None
  }

  private def createUser(login: String, password: String): User =
    val user = User(
      id       = UUID.randomUUID,
      login    = login,
      password = BCrypt.hashpw(password, BCrypt.gensalt())
    )
    users += (user.id -> user)
    saveUsers()
    user

  private def checkPw(req: LoginRequest, user: User): Boolean =
    BCrypt.checkpw(req.password, user.password)

  private def encodeToken(user: User): String =
    val claim = JwtClaim(
      subject = Some(user.id.toString),
      content = user.asJson.noSpaces
    )
    JwtCirce.encode(claim, cfg.jwtSecret, JwtAlgorithm.HS256)