package leptovasi.hid

import org.hid4java.{HidServicesSpecification, ScanMode}

import scala.util.chaining.*

object Specification:
  def apply(
    autoStart: Boolean = true,
    autoShutdown: Boolean = true,
    autoDataRead: Boolean = false,
    scanInterval: Int = 500,
    pauseInterval: Int = 5000,
    dataReadInterval: Int = 200,
    scanMode: ScanMode = ScanMode.SCAN_AT_FIXED_INTERVAL,
  ): HidServicesSpecification =
    new HidServicesSpecification()
      .tap(_.setAutoStart(autoStart))
      .tap(_.setAutoShutdown(autoShutdown))
      .tap(_.setAutoDataRead(autoDataRead))
      .tap(_.setScanInterval(scanInterval))
      .tap(_.setPauseInterval(pauseInterval))
      .tap(_.setDataReadInterval(dataReadInterval))
      .tap(_.setScanMode(scanMode))
