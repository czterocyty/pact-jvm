package au.com.dius.pact.core.model.generators

import au.com.dius.pact.core.support.Json
import com.google.gson.JsonObject
import spock.lang.Specification

import java.time.OffsetDateTime

@SuppressWarnings('LineLength')
class DateTimeGeneratorSpec extends Specification {

  def 'supports timezones'() {
    expect:
    new DateTimeGenerator('yyyy-MM-dd\'T\'HH:mm:ssZ', null).generate([:]) ==~
      /\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[-+]\d+/
  }

  def 'Uses any defined expression to generate the datetime value'() {
    expect:
    new DateTimeGenerator('yyyy-MM-dd\'T\'HH:mm:ssZ', '+ 1 day @ + 1 hour')
      .generate([baseDateTime: base]) == datetime

    where:
    base = OffsetDateTime.now()
    datetime = base.plusDays(1).plusHours(1).format('yyyy-MM-dd\'T\'HH:mm:ssZ')
  }

  def 'Uses json deserialization to work correctly with optional format fields'() {
    given:
    def map = [:]
    def json = Json.INSTANCE.toJson(map)
    def baseDateTime = OffsetDateTime.now()

    expect:
    DateTimeGenerator.@Companion.fromJson((JsonObject)json).generate([baseDateTime: baseDateTime]) == baseDateTime.toString()
  }
}
