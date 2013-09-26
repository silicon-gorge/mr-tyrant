# Nokia Entertainment Tyranitar

## Introduction

Tyranitar is a RESTful web service that exposes the information
required for the deployment of applications into the Nokia
Entertainment cloud computing environment. The information is
stored in git repositories in source.nokia.com. There are three
types of information:

* **deployment parameters** - information required by Asgard for the
deployment and application configuration process.

* **launch data** - information required for an application to
install itself onto a server.

* **application properties** - configuration parameters for an application,
for example urls to other used applications, logging levels, etc...

This information is held several git repositories in source.nokia.com
in the _tyranitar_ project. For each application, there are two
repositories named _{application}-dev_ and _{application}-prod_. If,
at some point, we were to have other environments, the naming would
follow the same pattern. Inside each repository there are three files
containing the configuration information:

* deployment-params.json

* launch-data.json

* application-properties.json

The whole point of this approach is that we keep all information
required for application configuation and deployment under tight
configuration control. We will be able to tie each release of
an application to specific configuration data. Generally, we will
release applications using the latest configuration data; but we
will also be able to revert to earlier setup if required and be
able to reset the head data set to an earlier one should it be
necessary. Initially, all of this control of configuation data
will be done by engineers commiting changes to the repositories.
Tyranitar is just a read-only view on this data. However, at a later
point we may decide to extend Tyranitar's capabiilities to include,
for example, the ability to revert the head to an earlier commit.

Simple to start with; more complicated later...possibly!

Note that the relationship between application releases and
configuration data commits will be managed by the Exploud
service.

## Notes

There is no longer a concept of environment variables, properties
are always specific to a service/environment combination.

## Resources

GET /1.x/ping (returns 'pong')

GET /1.x/status (returns status of the service)

GET /1.x/healthcheck (returns _200_ or _500_ depending on whether the service is healthy)

GET /1.x/applications/{env}/{app-name} (returns a list of the commits that exist for this
application/environment combination in latest-first order)

GET /1.x/applications/{env}/{app-name}/{commit}/{properties-set} (returns a specific set of
properties for this application/environment combination at the specified commit level)

GET /1.x/applications (returns a list of all the applications which have repositories in 
any environment).

GET /1.x/applications/{env} (returns a list of all the applications which have repositories 
in the specified environment).

POST /1.x/applications (creates new application repositories in 'dev' and 'prod' environments.
Application name specified in body).

## List Commits

### Resource Details

GET /1.x/applications/{env}/{app-name}

For the particular environment _{env} = dev | prod_ returns the list of commits of
configuration data for the specific application _{app-name}_. It only returns the
20 most recent commits. This is all we need for our purposes, probably.

### Example Request

    GET http://tyranitor.ent.nokia.com:8080/1.x/applications/prod/subscriptions

### Example Response

    200 OK
    Content-Type: application/json; charset=utf-8
    {
      "commits" : [
        {
          "hash" : "9cfe7ab02241b9cb6a068973fae90f833210ab72",
          "committer" : "mdaley",
          "email" : "matthew.daley@nokia.com",
          "message" : "decreased instance site to micro.",
          "date" : "2013-09-19T10:53:22Z"
        },
        {
          "hash" : "dc782a45e2984e20bad30ab25fba45aa94cb52be",
          "committer" : "micbell",
          "email" : "michael.1.bell@nokia.com",
          "message" : "corrected mistake in launch data",
          "date" : "2013-09-19T10:51:51Z"
        },
        {
          "hash" : "a78f553565f10b4931dab2657c461b3db2ae1b7e",
          "committer" : "wiekzor",
          "email" : "jerzy.wieczorek@nokia.com",
          "message" : "Initial properties for subscriptions service in prod.",
          "date" : "2013-09-18T14:25:49Z"
        }
      ]
    }


### Response Codes

200 OK

404 NotFound

500 InternalServerError

## Obtain Specific Data

### Resource Details

GET /1.x/applications/{env}/{app-name}/{commit}/{properties-set}

For the particular environment _{env} = dev | prod_, the application _{app-name}_
and the commit _{commit} = 40 character commit id | head_ get the particular set of properties _{properties-set} =
deployment-params | launch-data | application-properties_. The requested commit can be specified by either _head_
(case-insensitive) OR _head~n_ where 'n' is the number of revisions back from HEAD. Alternatively, the full 40-char
GIT hash may be used.

### Example Request

    GET http://tyranitar.ent.nokia.com:8080/1.x/applications/dev/gatekeeper/head~1/service-properties

### Example Response

    200 OK
    Content-Type: application/json; charset=utf-8
    {
      "hash" : "d78f553564f10b4931dab2627c46af3db2ae1b22",
      "data" : {
        "service-name" : "gatekeeper",
        "service-port" : "8080",
        "service-url":  "http://localhost:%s/1.x",
        "restdriver-port" :  "8081",
        "graphite-host" :  "graphite.brislabs.com",
        "graphite-port" : "8080",
        "nokia-account-baseurl" : "https://account.music.nokia.cq3.brislabs.com",
        "devicelicenses1-baseurl" : "http://devicelicenses.ent.cq3.brislabs.com:8080/1.x",
        "eapi1-baseurl" : "http://eapi.ent.cq3.brislabs.com/1.x",
        "keymaster1-baseur"l : "http://keymaster.ent.cq3.brislabs.com:8080/1.x",
        "jagus1-baseurl" : "http://jagus.ent.cq3.brislabs.com:8080/1.x"
      }

### Response Codes

200 OK

404 NotFound

500 InternalServerError
  
**************************************

## Obtain List of All Applications

### Resource Details
  
GET /1.x/applications 

Returns a list of all the applications which have repositories configured in any environment.

### Example Request

    GET /1.x/applications
    
### Example Response

    {
      "repositories": [
          "skeleton-dev",
          "test-dev",
          "test-prod",
          "test1-dev",
          "test1-prod"
      ]
    }

### Response Codes

200 OK

500 InternalServerError

## Obtain List of All Applications by Environment

### Resource Details

GET /1.x/applications/{env} 

Returns a list of all the applications which have repositories in the specified environment.

### Example Request

    GET /1.x/applications/dev
    
### Example Response

    {
      "repositories": [
          "skeleton-dev",
          "test-dev",
          "test1-dev",
      ]
    }

### Response Codes

200 OK

500 InternalServerError

## Add new Application

### Resource Details

POST /1.x/applications 

Creates new application repositories in 'dev' and 'prod' environments. Application name specified in body.

### Example Request

    POST /1.x/applications
    
    {"name": "myapp"}
    
### Example Response

    {
      "repositories": [
          "myapp-dev",
          "myapp-prod"
      ]
    }

### Response Codes

200 OK

409 CONFLICT - Application name already exists.

500 InternalServerError