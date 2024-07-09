package leptovasi

extension (bytes: Array[Byte]) def cleanString: String = bytes.map(_.toChar).mkString.filter(!_.isControl)
