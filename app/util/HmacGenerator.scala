package util

import actions.{AuthenticatedAction, Hmac}

object HmacGenerator {

  def main(args: Array[String]) {
    if(args.length < 3)
      throw new IllegalArgumentException("Date (yyyy-MM-dd'T'HH:mm:ss'Z'), path and hmac secret must be provided.")

    val date = args(0)
    val path = args(1)

    val hmac = Hmac.sign(date, path)

    println(s"HMAC token: $hmac")
  }
}
