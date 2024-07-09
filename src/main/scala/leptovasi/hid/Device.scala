package leptovasi.hid

import leptovasi.HIDConfiguration
import leptovasi.cleanString

import cats.data.OptionT
import cats.effect.{IO, Resource}
import cats.syntax.applicative.*
import org.hid4java.HidDevice as JavaHidDevice
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.FiniteDuration

class Device(val device: JavaHidDevice):
  protected val open: IO[Unit]  = IO(device.open()).void
  protected val close: IO[Unit] = IO(device.close())

  /*def write(message: String)(using Logger[IO]): IO[Unit] =
    message.toCharArray.map(_.toByte).grouped(32).zipWithIndex.toList.traverse_{
      (chunk, id) =>
        IO(device.write(chunk, 32, id, true)).flatMap {
          case bytesWritten if bytesWritten != 32 => Logger[IO].warn(s"Failed to wrtite chunk $id")
          case _                                  => IO.unit
        }
    }*/

  def write(message: String)(using Logger[IO]): IO[Unit] =
    val bytes = message.toCharArray.map(_.toByte)
    for
      _ <- IO.whenA(bytes.length > 32)(Logger[IO].error(s"$message too large, can only be 32 bytes at most!"))
      _ <- IO.blocking(device.write(bytes, 32, 0x00, true))
    yield ()

  def read(timeout: FiniteDuration): OptionT[IO, String] = OptionT {
    IO.blocking(device.read(32, timeout.toMillis.toInt).map(_.toByte).cleanString)
      .map(string => Option.unless(string.isBlank)(string))
      .attempt
      .map(_.toOption.flatten)
  }

object Device:
  def resource(device: JavaHidDevice): Resource[IO, Device] =
    val acquire = Device(device).pure[IO].flatTap(_.open)
    Resource.make(acquire)(_.close)

case class DeviceInfo(
  vendorID: String,
  productID: String,
  usage: String,
  usagePage: String,
  product: String,
  manufacturer: String,
  resource: Resource[IO, Device],
):
  def matches(hid: HIDConfiguration): Boolean =
    vendorID == hid.vendorID &&
      productID == hid.productID &&
      usage == hid.usageID &&
      usagePage == hid.usagePage

  override def toString: String = s"Device($manufacturer $product)"

object DeviceInfo:
  def apply(device: JavaHidDevice): DeviceInfo =
    DeviceInfo(
      vendorID = "0x" + device.getVendorId.toHexString,
      productID = "0x" + device.getProductId.toHexString,
      usage = "0x" + device.getUsage.toHexString,
      usagePage = "0x" + device.getUsagePage.toHexString,
      product = device.getProduct,
      manufacturer = device.getManufacturer,
      resource = Device.resource(device),
    )
