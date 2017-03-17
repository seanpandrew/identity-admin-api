package models

import java.util.UUID

import org.scalatest.{Matchers, WordSpec}
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._
import repositories.PersistedUser

class SearchResponseTest extends WordSpec with MockitoSugar with Matchers {

  def createUser: PersistedUser = {
    val user = mock[PersistedUser]
    when(user._id).thenReturn(Some(UUID.randomUUID().toString))
    when(user.primaryEmailAddress).thenReturn("test@test.com")
    when(user.publicFields).thenReturn(None)
    when(user.privateFields).thenReturn(None)
    when(user.dates).thenReturn(None)
    user
  }
  def createUsers(i: Int): Seq[PersistedUser] = (0 to i) map { _ => createUser }

  "SearchResponse" should {
    "hasMore should be true when the total is greater than offset + results size" in {
      val offset = 0
      val results = createUsers(10)
      val total = 20

      SearchResponse.create(total, offset, results).hasMore shouldEqual true
    }

    "hasMore should be false when the total is equal to the offset + results size" in {
      val offset = 0
      val results = createUsers(10)
      val total = 10

      SearchResponse.create(total, offset, results).hasMore shouldEqual false
    }
  }

}
