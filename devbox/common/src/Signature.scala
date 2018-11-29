package devbox.common
import java.nio.file.Files

import upickle.default.{ReadWriter, macroRW}
import java.security.MessageDigest

import scala.collection.mutable

sealed trait Signature

object Signature{

  val blockSize = 4 * 1024 * 1024
  def compute(p: os.Path): Option[Signature] = {
    // Non-existent files are None
    if (!os.exists(p, followLinks = false)) None
    // Files within symlinked folders are None
    else if (!os.followLink(p/os.up).contains(p/os.up)) None
    // Anything else is Some (even broken symlinks)
    else {
      val stat = os.stat(p, followLinks = false)

      stat.fileType match{
        case os.FileType.SymLink => Some(Symlink(Files.readSymbolicLink(p.toNIO).toString))
        case os.FileType.Dir =>
          if (!os.followLink(p).exists(_.last == p.last)) None
          else Some(Dir(os.perms(p).toInt()))
        case os.FileType.File =>
          if (!os.followLink(p).exists(_.last == p.last)) None
          else {
            val digest = MessageDigest.getInstance("MD5")
            val chunks = mutable.ArrayBuffer.empty[Bytes]
            var size = 0L
            for(d <- Util.readChunks(p, blockSize)){
              val (buffer, n) = d
              size += n
              digest.reset()
              digest.update(buffer, 0, n)

              chunks.append(Bytes(digest.digest()))
            }
            Some(File(os.perms(p).toInt, chunks, size))
        }
      }
    }

  }

  case class File(perms: Int, blockHashes: Seq[Bytes], size: Long) extends Signature
  object File{ implicit val rw: ReadWriter[File] = macroRW }

  case class Dir(perms: Int) extends Signature
  object Dir{ implicit val rw: ReadWriter[Dir] = macroRW }

  case class Symlink(dest: String) extends Signature
  object Symlink{ implicit val rw: ReadWriter[Symlink] = macroRW }

  implicit val rw: ReadWriter[Signature] = macroRW
}

case class Bytes(value: Array[Byte]){
  override def hashCode() = java.util.Arrays.hashCode(value)
  override def equals(other: Any) = other match{
    case o: Bytes => java.util.Arrays.equals(value, o.value)
    case _ => false
  }

  override def toString = {
    val cutoff = 100
    val hex = upickle.core.Util.bytesToString(value.take(cutoff))
    val dots = if(value.length > cutoff) "..." else ""
    s"Bytes(${value.length}, $hex$dots)"
  }
}

object Bytes{ implicit val rw: ReadWriter[Bytes] = macroRW }