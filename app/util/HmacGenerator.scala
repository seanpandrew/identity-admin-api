package util

import actions.HmacSigner

object HmacGenerator {

  def main(args: Array[String]) {
    if(args.length < 3)
      throw new IllegalArgumentException("Date (yyyy-MM-dd'T'HH:mm:ss'Z'), path and hmac secret must be provided.")

    val date = args(0)
    val path = args(1)
    val secret = args(2)

    val hmac = HmacSigner.sign(date, path, secret)

    println(s"HMAC token: $hmac")
  }
}
