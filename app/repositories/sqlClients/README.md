# Potential Postgres Clients

Client implementations are contained in this directory.

To test them, use the sbt console. Setup is:

* install Postgres locally if not already done so
* create a users table and populate

For the latter:

    $ psql [...] identity // connect to db as superuser
    $ create user identity_admin CREATEDB LOGIN;
    $ \c postgres identity_admin
    $ create database identity;
    $ \c identity
    $ create table users (jdoc jsonb);
    $ insert into users (jdoc) values ('{"_id":"21836057","primaryEmailAddress":"zniedq5dl2mhpsl1ctb@gu.com","userGroups":[{"joinedDate":{"$date":1503396058},"packageCode":"CRE","path":"/sys/policies/basic-identity"},{"joinedDate":{"$date":1503396058},"packageCode":"RCO","path":"/sys/policies/basic-community"}],"searchFields":{"emailAddress":"zniedq5dl2mhpsl1ctb@gu.com","displayName":"zniedq5dl2mhpsl1ctb zniedq5dl2mhpsl1ctb"},"dates":{"lastActivityDate":{"$date":1503396058},"accountCreatedDate":{"$date":1503396058}},"publicFields":{"displayName":"znIeDq5dL2MhPsl1Ctb znIeDq5dL2MhPsl1Ctb"},"statusFields":{"userEmailValidated":false,"receiveGnmMarketing":true,"allowThirdPartyProfiling":true},"privateFields":{"lastActiveLocation":{"cityCode":"London","countryCode":"GNM"},"billingAddress2":"address 2","billingPostcode":"E8123","billingAddress1":"address 1","lastActiveIpAddress":"77.91.250.234","billingCountry":"United Kingdom","secondName":"znIeDq5dL2MhPsl1Ctb","registrationType":"guardian","billingAddress3":"town","firstName":"znIeDq5dL2MhPsl1Ctb"}}');
    
## Thoughts

I've looked at:

* [Scalike](http://scalikejdbc.org/)
* [Doobie](https://github.com/tpolecat/doobie)
* [Slick](http://slick.lightbend.com/doc/3.2.1) - used this previously

Rejected out of hand:

* [ScalikeAsync](https://github.com/scalikejdbc/scalikejdbc-async) - still in beta
* [Quill](http://getquill.io/) - can't handle the type stuff

Our specific needs, as I see them, are:

* simple to use/understand
* fast (enough)
* likely to be around for a long time/good community support
* support the jsonb operations we need

I agree that these are boring requirements.

The least usual requirement is for jsonb support. It rules out the DSL-based 
approaches (Slick DSL, Scalike DSL) because they assume operations are on 
columns rather than within columns.

Some libraries (e.g. Slick, Scalike) support 'raw' SQL as well as DSLs. This is 
potentially useful if we transition from jsonb to regular columns in the future.
I suspect we'd have to rewrite everything at that point anyway though so it is 
unlikely to help much. 

Scalike and Doobie seem the most promising. Both run on JDBC and support SQL 
strings with varying degrees of type-safety on top.

I've added detailed comments in the example code, but broadly:

* Scalike is simpler
* Doobie is more functional and arguably harder to use/understand
* Doobie is better at type safety (e.g. see 
[Typechecking Queries](http://tpolecat.github.io/doobie-0.2.1/06-Checking.html))
  which could really help write good tests

I'd lean towards Scalike as it's conceptually simpler, but the typechecking 
facilities (at runtime) of Doobie are attractive and could certainly catch 
errors when used to write tests.

*Comment on blocking vs non-blocking*

Doobie and Scalike are both blocking. Isn't that a disaster?! No. Firstly, 
Identity API is built on Scalatra and a thread-per-request/blocking model so 
async is not helpful. But more generally, asynchronous code is much harder to 
reason about and write. If necessary (as for Identity Admin API) it is easy to 
make a blocking API asynchronous by pushing work onto a threadpool of some kind.
