MW2SPARQL ![CircleCI](https://img.shields.io/circleci/build/github/mw2sparql/mw2sparql) ![Uptime Robot ratio (30 days)](https://img.shields.io/uptimerobot/ratio/m785457671-7a0e820bedfad3595188afb7)
=========

This repository contains the code of a [Wikimedia Tools Labs](https://tools.wmflabs.org) tool allowing to query [Wikimedia Wikis SQL database](https://www.mediawiki.org/wiki/Database) using SPARQL:
* [User documentation](https://www.mediawiki.org/wiki/MW2SPARQL)
* [Live endpoint](https://mw2sparql.toolforge.org/)
* [K8s status](https://k8s-status.toolforge.org/namespaces/tool-mw2sparql/) 
* [CirceCI pipeline](https://app.circleci.com/pipelines/github/mw2sparql/mw2sparql)

## Install

This service depends on Java 11 and some maven dependencies.

To compile it as a fat .jar that could be deployed on Tools Labs you should run
```
mvn clean package
```

To run it requires to have in the same directory as the .jar file a file called `config.properties` with the following entries:

* `app.http.baseURI` with the base URI of the service like `http://mw2sparql.toolforge.org:8000/`
* `app.db.hostPattern` the SQL database host (with port when required) like `{siteId}.labsdb`. `{siteId}` is replaced on runtime by the id of the target wiki (like `enwiki`).
* `app.db.user` the database user to use. Stored in the `replica.my.cnf` file on Tools Labs
* `app.db.password`the database user password. Stored in the `replica.my.cnf` file on Tools Labs
