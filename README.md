# Mr. Tyrant

## Introduction

Tyrant is a RESTful web service that exposes the information required for the deployment of applications using MixRadio's deployment tooling. The information is stored in Github Enterprise. There are three types of information:

* **application properties** - Configuration parameters for an application, for example URLs to other applications, connection strings and logging levels.

* **deployment parameters** - Information specifying how the application should be deployed, for example number of instances and security groups.

* **launch data** - An optional list of commands to run after an instance has started, but before the application is launched.

This information for an application is held in a number of repositories in Github Enterprise under a specific organisation. Each repository holds information for an application in a specific environment using the pattern `{application}-{environment}`.
Inside each repository there are three files which are created by Tyrant when the repository is created.

* application-properties.json
* deployment-params.json
* launch-data.json

The whole point of this approach is that we keep all information required for application configuration and deployment under version control. We are able to tie each release of an application to a specific configuration hash. Generally, an application release will use the latest configuration data; but we can also also revert to an earlier setup, if required, and be able to reset the `HEAD` data set to an earlier one should it be necessary (all using Git). Once the initial repository is created Tyrant provides read-only access to the data held within.

Note that the relationship between application releases and configuration data commits will be managed by the Maestro service.

## Resources

`GET /ping` - returns `pong` with a status of `200`

`GET /healthcheck` - returns `200` or `500` depending on whether the service is healthy, based on the status of its dependencies

`GET /applications/{env}/{app-name}` - returns a list of the commits that exist for this application/environment combination in latest-first order

`GET /applications/{env}/{app-name}/{commit}/{properties-set}` - returns a specific set of properties for this application/environment combination at the specified commit level

`GET /applications` - returns a list of all the applications which have repositories in any environment

`GET /applications/{env}` - returns a list of all the applications which have repositories in the specified environment

`POST /applications` - creates new application repositories in environments where `create-repo` has been specified

## List Commits

### Resource Details

`GET /applications/{env}/{app-name}`

For the particular environment `{env}` returns the list of commits of configuration data for the specific application `{app-name}`. It only returns the 20 most recent commits.

### Example Request

    GET http://tyrant/applications/prod/myapplication

### Example Response

    200 OK
    Content-Type: application/json; charset=utf-8
    {
      "commits" : [
        {
          "committer" : "someone",
          "date" : "2013-09-19T10:53:22Z",
          "email" : "someone@somewhere.com",
          "hash" : "acfe7ab02241b9cb6a068973fae90f833210ab73",
          "message" : "Did something"
        },
        ...
      ]
    }


### Response Codes

200 OK

404 NotFound

500 InternalServerError

## Obtain Specific Data

### Resource Details

`GET /applications/{env}/{app-name}/{commit}/{application-properties|deployment-params|launch-data}`

For the particular environment `{env}`, the application `{app-name}` and the commit `{commit}` (40 character commit ID or `HEAD`) get the particular set of properties. The requested commit can be specified by either `HEAD` OR `HEAD~n` where 'n' is the number of revisions back from `HEAD`. Alternatively, the full 40-char Git hash may be used.

### Example Request

    GET http://tyrant/applications/prod/myapplication/HEAD~1/application-properties

### Example Response

    200 OK
    Content-Type: application/json; charset=utf-8
    {
      "hash" : "e78f553564f10b4931dab2627c46af3db2ae1b23",
      "data" : {
        "service.name" : "myapplication",
        "service.port" : "8080",
        "graphite-host" :  "graphite",
        "graphite-port" : "2003"
      }
    }

### Response Codes

200 OK

404 NotFound

500 InternalServerError

## Obtain List of All Applications

### Resource Details

`GET /applications`

Returns a list of all the applications which have repositories configured in any environment.

### Example Request

    GET http://tyrant/applications

### Example Response

    200 OK
    Content-Type: application/json; charset=utf-8
    {
      "applications":{
        "tyrant":{
          "repositories":[
            {
              "name":"tyrant-poke",
              "path":"git@github:tyrant/tyrant-poke.git"
            },
            {
              "name":"tyrant-prod",
              "path":"git@github:tyrant/tyrant-prod.git"
            }
          ]
        },
        "baker":{
          "repositories":[
            {
              "name":"baker-poke",
              "path":"git@github:tyrant/baker-poke.git"
            }
          ]
        },
        "lister":{
          "repositories":[
            {
              "name":"lister-poke",
              "path":"git@github:tyrant/lister-poke.git"
            },
            {
              "name":"lister-prod",
              "path":"git@github:tyrant/lister-prod.git"
            }
          ]
        }
      }
    }

### Response Codes

200 OK

500 InternalServerError

## Obtain List of All Applications by Environment

### Resource Details

`GET /applications/{env}`

Returns a list of all the applications which have repositories in the specified environment.

### Example Request

    GET http://tyrant/applications/poke

### Example Response

    200 OK
    Content-Type: application/json; charset=utf-8
    {
      "applications":{
        "tyrant":{
          "repositories":[
            {
              "name":"tyrant-poke",
              "path":"git@github:tyrant/tyrant-poke.git"
            }
          ]
        },
        "baker":{
          "repositories":[
            {
              "name":"baker-poke",
              "path":"git@github:tyrant/baker-poke.git"
            }
          ]
        },
        "lister":{
          "repositories":[
            {
              "name":"lister-poke",
              "path":"git@github:tyrant/lister-poke.git"
            }
          ]
        }
      }
    }

### Response Codes

200 OK

500 InternalServerError

## Add new Application

### Resource Details

`POST /applications`

Creates new application repositories in 'dev' and 'prod' environments. Application name specified in body.

### Example Request

    POST http://tyrant/applications/myapplication

### Example Response

    201 CREATED
    Content-Type: application/json; charset=utf-8
    {
      "repositories": [
        {
          "name" : "myapplication-poke",
          "path" : "git@github:tyrant/myapplication-poke.git"
        },
        {
          "name" : "myapplication-prod",
          "path" : "git@github:tyrant/myapplication-prod.git"
        }
      ]
    }

### Response Codes

200 OK

409 Conflict - Application name already exists.

500 InternalServerError