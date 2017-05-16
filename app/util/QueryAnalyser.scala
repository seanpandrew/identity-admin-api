package util

object QueryAnalyser {
  sealed trait Query
  case class IdentityQuery(query: String) extends Query
  case class MembershipNumberQuery(membershipNumber: String) extends Query

  def apply(query: String): Query =
    if ((query forall Character.isDigit) && query.size < 8)
      MembershipNumberQuery(query)
    else
      IdentityQuery(query)
}
