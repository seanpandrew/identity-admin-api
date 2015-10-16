package repositories

import javax.inject.Singleton

import com.novus.salat.dao.SalatDAO
import com.novus.salat.global._

@Singleton
class UsersWriteRepository extends SalatDAO[User, String](collection=SalatMongoConnection.db()("users")){
  
  def createUser(user: User) = {
    insert(user)
  }
}
