package au.com.dius.pact.core.model.matchingrules

import au.com.dius.pact.core.support.Json
import com.google.gson.JsonObject
import spock.lang.Issue
import spock.lang.Specification

class MatchingRulesSpec extends Specification {

  def 'fromMap handles a null map'() {
    when:
    def matchingRules = MatchingRulesImpl.fromJson(null)

    then:
    matchingRules.empty
  }

  def 'fromMap handles an empty map'() {
    when:
    def matchingRules = MatchingRulesImpl.fromJson(new JsonObject())

    then:
    matchingRules.empty
  }

  def 'loads V2 matching rules'() {
    given:
    def matchingRulesMap = [
      '$.path': ['match': 'regex', 'regex': '\\w+'],
      '$.query.Q1': ['match': 'regex', 'regex': '\\d+'],
      '$.header.HEADERX': ['match': 'include', 'value': 'ValueA'],
      '$.headers.HEADERY': ['match': 'include', 'value': 'ValueA'],
      '$.body.animals': ['min': 1, 'match': 'type'],
      '$.body.animals[*].*': ['match': 'type'],
      '$.body.animals[*].children': ['min': 1],
      '$.body.animals[*].children[*].*': ['match': 'type']
    ]

    when:
    def matchingRules = MatchingRulesImpl.fromJson(Json.INSTANCE.toJson(matchingRulesMap))

    then:
    !matchingRules.empty
    matchingRules.categories == ['path', 'query', 'header', 'body'] as Set
    matchingRules.rulesForCategory('path') == new Category('path', [
      '': new MatchingRuleGroup([new RegexMatcher('\\w+') ]) ])
    matchingRules.rulesForCategory('query') == new Category('query', [
      Q1: new MatchingRuleGroup([ new RegexMatcher('\\d+') ]) ])
    matchingRules.rulesForCategory('header') == new Category('header', [
      HEADERX: new MatchingRuleGroup([ new IncludeMatcher('ValueA') ]),
      HEADERY: new MatchingRuleGroup([ new IncludeMatcher('ValueA') ]) ])
    matchingRules.rulesForCategory('body') == new Category('body', [
      '$.animals': new MatchingRuleGroup([ new MinTypeMatcher(1) ]),
      '$.animals[*].*': new MatchingRuleGroup([TypeMatcher.INSTANCE ]),
      '$.animals[*].children': new MatchingRuleGroup([ new MinTypeMatcher(1) ]),
      '$.animals[*].children[*].*': new MatchingRuleGroup([ TypeMatcher.INSTANCE ])
    ])
  }

  def 'loads V3 matching rules'() {
    given:
    def matchingRulesMap = [
      path: [
        'combine': 'OR',
        'matchers': [
          [ 'match': 'regex', 'regex': '\\w+' ]
        ]
      ],
      query: [
        'Q1': [
          'matchers': [
            [ 'match': 'regex', 'regex': '\\d+' ]
          ]
        ]
      ],
      header: [
        'HEADERY': [
          'combine': 'OR',
          'matchers': [
            ['match': 'include', 'value': 'ValueA'],
            ['match': 'include', 'value': 'ValueB']
          ]
        ]
      ],
      body: [
        '$.animals': [
          'matchers': [['min': 1, 'match': 'type']],
          'combine': 'OR'
        ],
        '$.animals[*].*': [
          'matchers': [['match': 'type']],
          'combine': 'AND',
        ],
        '$.animals[*].children': [
          'matchers': [['min': 1]],
          'combine': 'OTHER'
        ],
        '$.animals[*].children[*].*': [
          'matchers': [['match': 'type']]
        ]
      ]
    ]

    when:
    def matchingRules = MatchingRulesImpl.fromJson(Json.INSTANCE.toJson(matchingRulesMap))

    then:
    !matchingRules.empty
    matchingRules.categories == ['path', 'query', 'header', 'body'] as Set
    matchingRules.rulesForCategory('path') == new Category('path', [
      '': new MatchingRuleGroup([ new RegexMatcher('\\w+') ], RuleLogic.OR) ])
    matchingRules.rulesForCategory('query') == new Category('query', [
      Q1: new MatchingRuleGroup([ new RegexMatcher('\\d+') ]) ])
    matchingRules.rulesForCategory('header') == new Category('header', [
      HEADERY: new MatchingRuleGroup([ new IncludeMatcher('ValueA'), new IncludeMatcher('ValueB') ],
        RuleLogic.OR)
    ])
    matchingRules.rulesForCategory('body') == new Category('body', [
      '$.animals': new MatchingRuleGroup([ new MinTypeMatcher(1) ], RuleLogic.OR),
      '$.animals[*].*': new MatchingRuleGroup([ TypeMatcher.INSTANCE ]),
      '$.animals[*].children': new MatchingRuleGroup([ new MinTypeMatcher(1) ]),
      '$.animals[*].children[*].*': new MatchingRuleGroup([ TypeMatcher.INSTANCE ])
    ])
  }

  @Issue('#743')
  def 'loads matching rules affected by defect #743'() {
    given:
    def matchingRulesMap = [
      'path': [
        '': [
          'matchers': [
            [ 'match': 'regex', 'regex': '\\w+' ]
          ]
        ]
      ]
    ]

    when:
    def matchingRules = MatchingRulesImpl.fromJson(Json.INSTANCE.toJson(matchingRulesMap))

    then:
    !matchingRules.empty
    matchingRules.categories == ['path'] as Set
    matchingRules.rulesForCategory('path') == new Category('path', [
      '': new MatchingRuleGroup([ new RegexMatcher('\\w+') ]) ])
  }

  @Issue('#743')
  def 'generates path matching rules in the correct format'() {
    given:
    def matchingRules = new MatchingRulesImpl()
    matchingRules.addCategory('path').addRule(new RegexMatcher('\\w+'))

    expect:
    matchingRules.toV3Map() == [path: [matchers: [[match: 'regex', regex: '\\w+']], combine: 'AND']]
  }

  def 'do not include empty categories'() {
    given:
    def matchingRules = new MatchingRulesImpl()
    matchingRules.addCategory('path').addRule(new RegexMatcher('\\w+'))
    matchingRules.addCategory('body')
    matchingRules.addCategory('header')

    expect:
    matchingRules.toV3Map() == [path: [matchers: [[match: 'regex', regex: '\\w+']], combine: 'AND']]
  }

  @Issue('#882')
  def 'With V2 format, matching rules for headers are pluralised'() {
    given:
    def matchingRules = new MatchingRulesImpl()
    matchingRules.addCategory('path').addRule(new RegexMatcher('\\w+'))
    matchingRules.addCategory('body')
    matchingRules.addCategory('header').addRule('X', new RegexMatcher('\\w+'))

    expect:
    matchingRules.toV2Map() == [
      '$.path': [match: 'regex', regex: '\\w+'],
      '$.headers.X': [match: 'regex', regex: '\\w+']
    ]
  }

}
