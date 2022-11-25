# Exercice commencé
Votre note est de **10.5**/22.

## Détail
* Part 1 - Compilation & Tests: 2/4
    * There are test failures, see `mvn verify`

* Part 2 - CI: 1/2
    * Latest CI run of branch `main` was expected to be in *success* state but found: failure

* Part 3 - Code Coverage: 0/4
    * Coverage not available when there is test failures

* Part 4 - AMQP -> ES: 4/4
* Part 5 - File -> AMQP: 4/4
* Part 6 - Lucene querying: 4/4
* Git (proper descriptive messages): -0.5
    * `e8267ed` All-in-One --> 1 word is too short

* Coding style: -4
    * fr.lernejo.fileinjector.Launcher
      * L.25: The field `MESSAGES_TYPE_REF` must not have modifier `static`
      * L.28: Method has 19 lines, exceeding the maximum of 15
    * fr.lernejo.search.api.AmqpConfiguration
      * L.10: The field `GAME_INFO_QUEUE` must have modifier `private` and must not have modifier `static`
    * fr.lernejo.search.api.ElasticSearchConfiguration
      * L.23: The field `logger` must not have modifier `static`
      * L.24: The field `GAME_INDEX` must have modifier `private` and must not have modifier `static`



*Analyse effectuée à 1970-01-01T00:00:00Z.*
