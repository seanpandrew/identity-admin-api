# identity-admin-api

#Application configuration

Configuration files:
- Environment-specific configuration (`identity-admin-api/conf/<ENV>.conf`)
- Application configuration (`identity-admin-api/conf/application.conf`)
- System file with additional properties (`/etc/gu/identity-admin-api.conf`)

# Setting up Identity Admin locally

The Identity database needs to be running. Instructions on how to set this up and run it are on the Identity repo. 

#Running the Application

```
sbt
project identity-admin-api
devrun
```

#Hitting the API with curl

The API requires requests to have an authorization header to hit the API. A hmac token must be generated to be put in the header. This can be done in SBT

##Generating the authorization token

```
sbt
project identity-admin-api
runMain util.HmacGenerator "*date*" "*uri to hit*" "*hmac secret value*"
```
This will generate a hmac token

###Example
```
sbt
project identity-admin-api
runMain util.HmacGenerator "2015-10-26T18:13:04Z" "/v1/user/11111111" "secret"
```

##Curl

```
curl --header "Authorization: HMAC *hmac-token*" --header "Date: *date*" http://localhost:9500/*uri to hit*
```
Date must match the one used to generate the token and be in the form YYYY-MM-DDTHH:MM:SSZ.

###Example
```
curl --header "Authorization: HMAC 334ddfgdfgk4rt[q" --header "Date: 2015-10-26T18:13:04Z" http://localhost:9500/v1/user/11111111
```


