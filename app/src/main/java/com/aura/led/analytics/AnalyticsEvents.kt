package com.aura.led.analytics

/**
 * The complete catalogue of what Aura may report.
 *
 * Every event is a [object]_[verb] name with a fixed, small property set; nothing else is
 * accepted (see [AnalyticsSanitizer]). Nothing here may ever describe notification content,
 * a sender, a contact or a group name, or a message: Aura reports *which feature was used*,
 * never *what the user received*.
 */
object AnalyticsEvents {
    // ---- Lifecycle ----
    const val APP_OPENED = "app_opened"
    const val CONSENT_GRANTED = "consent_granted"
    const val CONSENT_DECLINED = "consent_declined"

    // ---- Setup funnel ----
    const val SETUP_LANGUAGE_SELECTED = "setup_language_selected"
    const val SETUP_NOTIFICATION_ACCESS_OPENED = "setup_notification_access_opened"
    const val SETUP_NOTIFICATION_ACCESS_GRANTED = "setup_notification_access_granted"
    const val SETUP_PRIVILEGE_REQUESTED = "setup_privilege_requested"
    const val SETUP_PRIVILEGE_GRANTED = "setup_privilege_granted"
    const val SETUP_LED_TEST_RUN = "setup_led_test_run"
    const val SETUP_AUTOSTART_OPENED = "setup_autostart_opened"
    const val SETUP_BATTERY_EXEMPTION_REQUESTED = "setup_battery_exemption_requested"
    const val SETUP_BATTERY_EXEMPTION_GRANTED = "setup_battery_exemption_granted"

    // ---- Feature usage ----
    const val APP_RULE_TOGGLED = "app_rule_toggled"
    const val APP_RULE_COLOR_CHANGED = "app_rule_color_changed"
    const val APP_RULE_PREVIEWED = "app_rule_previewed"
    const val SENDER_PARSING_TOGGLED = "sender_parsing_toggled"
    const val SENDER_RULE_CREATED = "sender_rule_created"
    const val SENDER_RULE_DELETED = "sender_rule_deleted"
    const val ANIMATION_SELECTED = "animation_selected"
    const val LED_DURATION_CHANGED = "led_duration_changed"
    const val SYSTEM_LED_TOGGLED = "system_led_toggled"
    const val SERVICE_TOGGLED = "service_toggled"
    const val THEME_CHANGED = "theme_changed"
    const val REMINDER_TOGGLED = "reminder_toggled"
    const val REMINDER_INTERVAL_CHANGED = "reminder_interval_changed"
    const val REMINDER_QUIET_HOURS_CHANGED = "reminder_quiet_hours_changed"
    const val LED_EMITTED = "led_emitted"

    // ---- Update flow ----
    const val UPDATE_CHECK_RESULT = "update_check_result"
    const val UPDATE_PROMPT_SHOWN = "update_prompt_shown"
    const val UPDATE_PROMPT_ACCEPTED = "update_prompt_accepted"
    const val UPDATE_PROMPT_POSTPONED = "update_prompt_postponed"
    const val UPDATE_DOWNLOAD_RESULT = "update_download_result"
    const val UPDATE_INSTALL_LAUNCHED = "update_install_launched"

    // ---- Support / monetization intent ----
    const val SUPPORT_INTENT = "support_intent"
    const val SUPPORT_CLICKED = "support_clicked"

    // ---- Property keys (the allow-list enforced by AnalyticsSanitizer) ----
    const val P_LANGUAGE = "language"
    const val P_KIND = "kind"
    const val P_SUCCESS = "success"
    const val P_TARGET_PACKAGE = "target_package"
    const val P_ENABLED = "enabled"
    const val P_IS_CUSTOM = "is_custom"
    const val P_ANIMATION_ID = "animation_id"
    const val P_SECONDS = "seconds"
    const val P_DISABLED = "disabled"
    const val P_RUNNING = "running"
    const val P_MODE = "mode"
    const val P_INTERVAL_S = "interval_s"
    const val P_REASON = "reason"
    const val P_RESULT = "result"
    const val P_LATEST_TAG = "latest_tag"
    const val P_ANSWER = "answer"
    const val P_CHANNEL = "channel"

    const val P_THEME = "theme"
    const val P_LOCALE = "locale"
    const val P_IS_FIRST_RUN = "is_first_run"
    const val P_IS_UPDATE = "is_update"
    const val P_LISTENER_CONNECTED = "listener_connected"
    const val P_PRIVILEGE = "privilege"

    /** Every property key a call site is allowed to send. */
    val PROPERTY_KEYS: Set<String> = setOf(
        P_LANGUAGE, P_KIND, P_SUCCESS, P_TARGET_PACKAGE, P_ENABLED, P_IS_CUSTOM,
        P_ANIMATION_ID, P_SECONDS, P_DISABLED, P_RUNNING, P_MODE, P_INTERVAL_S,
        P_REASON, P_RESULT, P_LATEST_TAG, P_ANSWER, P_CHANNEL,
        P_THEME, P_LOCALE, P_IS_FIRST_RUN, P_IS_UPDATE, P_LISTENER_CONNECTED, P_PRIVILEGE,
    )

    // ---- Values reused on several call sites ----
    const val KIND_SHIZUKU = "shizuku"
    const val KIND_ROOT = "root"
    const val REASON_APP = "app"
    const val REASON_CONTACT = "contact"
    const val REASON_GROUP = "group"
}
