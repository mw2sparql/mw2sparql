MW2SPARQL
=========

This repository contains the code of a [Wikimedia Tools Labs](https://tools.wmflabs.org) tool allowing to query [Wikimedia Wikis SQL database](https://www.mediawiki.org/wiki/Database) using SPARQL.

It is live at https://tools.wmflabs.org/mw2sparql and a [user documentation](https://www.mediawiki.org/wiki/MW2SPARQL) is available.


## Install

This service depends on Java 8 and some maven dependencies.

To compile it as a fat .jar that could be deployed on Tools Labs you should run
```
mvn clean package
```

To run it requires to have in the same directory as the .jar file a file called `config.properties` with the following entries:

* `app.http.baseURI` with the base URI of the service like `http://tools.wmflabs.org:8000/mw2sparql/`
* `app.db.hostPattern` the SQL database host (with port when required) like `{siteId}.labsdb`. `{siteId}` is replaced on runtime by the id of the target wiki (like `enwiki`).
* `app.db.user` the database user to use. Stored in the `replica.my.cnf` file on Tools Labs
* `app.db.password`the database user password. Stored in the `replica.my.cnf` file on Tools Labs

