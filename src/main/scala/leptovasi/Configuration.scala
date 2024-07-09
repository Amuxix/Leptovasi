package leptovasi

import cats.effect.IO
import com.typesafe.config.{Config, ConfigFactory}
import pureconfig.{ConfigReader, ConfigSource}
import pureconfig.generic.derivation.default.derived
import pureconfig.module.catseffect.syntax.*

import scala.concurrent.duration.FiniteDuration

case class HIDConfiguration(
  vendorID: String,
  productID: String,
  usageID: String,
  usagePage: String,
) derives ConfigReader

case class Configuration(
  hid: HIDConfiguration,
  windowChangeInterval: FiniteDuration,
  readInterval: FiniteDuration,
  applications: String,
) derives ConfigReader

object Configuration:
  def fromConfig(config: Config = ConfigFactory.load()): IO[Configuration] =
    ConfigSource.fromConfig(config).loadF()
