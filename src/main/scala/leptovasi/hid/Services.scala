package leptovasi.hid

import cats.effect.{IO, Resource}
import org.hid4java.HidServices as JavaHidServices

import scala.jdk.CollectionConverters.*

class Services(services: JavaHidServices):
  protected val start: IO[Unit]    = IO(services.start())
  protected val shutdown: IO[Unit] = IO(services.shutdown())

  def addListener(listener: Listener): IO[Unit] = IO(services.addHidServicesListener(listener))

  def attachedDevices: List[DeviceInfo] = services.getAttachedHidDevices.asScala.toList.map(DeviceInfo(_))

object Services:
  def resource(listener: Listener): Resource[IO, Services] =
    val spec    = Specification(autoStart = false, autoShutdown = false)
    val acquire = IO(new JavaHidServices(spec))
      .map(Services(_))
      .flatTap(_.addListener(listener))
      .flatTap(_.start)
    Resource.make(acquire)(_.shutdown)
