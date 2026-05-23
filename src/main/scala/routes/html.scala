package routes

import cats.effect.IO
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.`Content-Type`

object html:

  private def page(title: String, body: String): IO[Response[IO]] =
    val html = s"""<!DOCTYPE html><html><head><title>$title</title>
      <meta charset="UTF-8"><style>
        body{font-family:Arial;max-width:600px;margin:50px auto;padding:20px}
        input{display:block;margin:10px 0;padding:8px;width:100%}
        button{padding:10px 20px}
        .error{color:red}
        table{width:100%;border-collapse:collapse}
        td,th{padding:8px;border-bottom:1px solid #ddd}
      </style></head><body>$body</body></html>"""
    Ok(html).map(_.withContentType(`Content-Type`(MediaType.text.html)))

  def loginPage(error: Option[String]): IO[Response[IO]] =
    val msg = error.map(e => s"<p class='error'>$e</p>").getOrElse("")
    page("Login",
      s"""<h1>Login</h1>$msg
      <form method="post">
        <input name="login" placeholder="Login" required>
        <input name="password" type="password" placeholder="Password" required>
        <button>Login</button>
      </form>
      <p><a href="/register">Register</a></p>""")

  def registerPage(error: Option[String]): IO[Response[IO]] =
    val msg = error.map(e => s"<p class='error'>$e</p>").getOrElse("")
    page("Register",
      s"""<h1>Register</h1>$msg
      <form method="post">
        <input name="login" placeholder="Login" required>
        <input name="password" type="password" placeholder="Password" required>
        <button>Register</button>
      </form>
      <p><a href="/login">Login</a></p>""")

  def filesPage(files: List[(String, String, Long)]): IO[Response[IO]] =
    val list = files.map { (id, name, size) =>
      s"<tr><td>$name</td><td>${size / 1024} KB</td><td><a href='/download/$id'>Download</a></td></tr>"
    }.mkString
    page("Files",
      s"""<h1>Files</h1>
      <h2>Upload</h2>
      <form method="post" action="/upload" enctype="multipart/form-data">
        <input name="file" type="file" required>
        <input name="password" type="password" placeholder="File password" required>
        <button>Upload</button>
      </form>
      <h2>Files</h2>
      <table><tr><th>Name</th><th>Size</th><th></th></tr>$list</table>
      <p><a href="/logout">Logout</a></p>""")

  def downloadPage(id: String): IO[Response[IO]] =
    page("Download",
      s"""<h1>Download</h1>
      <form method="post" action="/download/$id">
        <input name="password" type="password" placeholder="File password" required>
        <button>Download</button>
      </form>
      <p><a href="/files">Back to files</a></p>""")