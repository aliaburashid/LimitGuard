Feature: Automatic reservation expiry

  Scenario: The scheduler expires only overdue reservations
    Given a facility with an overdue and a future reservation
    When the expiry scheduler runs
    Then only the overdue reservation is expired
    And the overdue amount is released back to facility headroom
    And exactly one expiry notification is queued for the overdue reservation
