import javax.inject.Inject

import com.google.inject.Singleton
import play.api.Environment
import salat.Context

/*
  https://stackoverflow.com/questions/20715277/salat-grater-glitch-classnotfoundexception
  https://github.com/salat/salat/wiki/CustomContext
 */
package object repositories {
  implicit val ctx = new Context {
    val name = "Custom_Classloader"
  }

  @Singleton
  class T @Inject() (playEnv: Environment) {
    ctx.clearAllGraters()
    ctx.registerClassLoader(playEnv.classLoader)
  }
}
