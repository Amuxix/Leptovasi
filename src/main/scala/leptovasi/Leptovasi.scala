package leptovasi

import leptovasi.hid.{Device, Listener, Services}

import cats.effect.{IO, IOApp}
import cats.effect.kernel.Resource
import cats.effect.unsafe.IORuntime
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import retry.*

import scala.concurrent.duration.*

object Leptovasi extends IOApp.Simple:
  def resoures(config: Configuration)(using Logger[IO], IORuntime): Resource[IO, (LayerChanger, Device)] =
    for
      layerChanger <- LayerChanger.resource(config)
      listener      = Listener(config.hid, layerChanger.handleAllMessages)
      services     <- Services.resource(listener)
      deviceInfo   <- Resource.eval {
                        IO.fromOption(services.attachedDevices.find(_.matches(config.hid))) {
                          new Exception("Device not found")
                        }
                      }
      device       <- deviceInfo.resource
    yield (layerChanger, device)

  override def run: IO[Unit] = for
    given Logger[IO] <- Slf4jLogger.create[IO]
    given IORuntime   = runtime
    config           <- Configuration.fromConfig()
    _                <- retryingOnAllErrors[Unit](RetryPolicies.constantDelay[IO](5.seconds), noop[IO, Throwable])(
                          resoures(config).use((layerChanger, device) => layerChanger.loop(device, config)),
                        )
  yield ()
