package com.example.limitguard.enums;

// the possible statuses a counterparty can have
public enum CounterpartyStatus {
    // means the bank is currently allowed to do business with that counterparty
    ACTIVE,
    // Risk might freeze it because its risk situation needs review
    // means the counterparty still exists in LimitGuard,
    // but the bank has temporarily stopped new credit activity with it.
    //------- Existing records/history remain
    //------- No new credit request
    FROZEN,
    // means the counterparty relationship is considered closed for new activity
    //-------- Historical information remains
    //-------- No new credit requests
    CLOSED
}
