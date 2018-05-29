# 0.2
* Adapting to breaking change in database replicas on tools labs (see [phab:T188506](https://phabricator.wikimedia.org/T188506) for details)
* Fix db connection leakage and compatibity issues with blazegraph regarding umlauts and url encoding
* Support all wikimedia projects (currently 880 sites) on reduced mappings (due to performance and memory constraints, see [ontology](https://tools.wmflabs.org/mw2sparql/sparql/ontology))
# 0.1
* Initial version that supports English, French and German wikipedias only
