package leptovasi.hid

import leptovasi.HIDConfiguration

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.hid4java.HidServicesListener
import org.hid4java.event.HidServicesEvent
import org.typelevel.log4cats.Logger

class Listener(hid: HIDConfiguration, requestLayerStack: => IO[Unit])(using Logger[IO], IORuntime) extends HidServicesListener:
  extension (event: HidServicesEvent)
    def onDeviceMatch(f: DeviceInfo => IO[Unit]): Unit =
      (DeviceInfo(event.getHidDevice) match
        case device @ DeviceInfo(hid.vendorID, hid.productID, hid.usageID, hid.usagePage, _, _, _) =>
          f(device)
        case _                                                                                     => IO.unit
      )
      .unsafeRunAndForget()

  override def hidDeviceAttached(event: HidServicesEvent): Unit = event.onDeviceMatch { deviceInfo =>
    Logger[IO].debug(s"Device attached $deviceInfo") *> requestLayerStack
  }

  override def hidDeviceDetached(event: HidServicesEvent): Unit = event.onDeviceMatch { deviceInfo =>
    Logger[IO].debug(s"Device detached $deviceInfo")
  }

  override def hidFailure(event: HidServicesEvent): Unit = event.onDeviceMatch { deviceInfo =>
    Logger[IO].debug(s"Device failed $deviceInfo")
  }

  override def hidDataReceived(event: HidServicesEvent): Unit = () // This doesn't work too well...
