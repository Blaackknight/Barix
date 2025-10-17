package fr.bck.barix.lang;

/**
 * Copie locale de clés de traduction pour référence. La vraie enum LangKey est générée
 * dans build/generated/sources/langKeys. Cette version est volontairement non publique
 * et porte un autre nom pour éviter les conflits de compilation.
 */
enum LangKeyLocal {
    BARIX_PING_OK("barix.ping.ok"),
    BARIX_AUDIT_ENABLED("barix.audit.enabled"),
    BARIX_AUDIT_DISABLED("barix.audit.disabled"),
    BARIX_AUDIT_WINDOW_SET("barix.audit.window.set"),
    BARIX_AUDIT_FLAG("barix.audit.flag"),
    BARIX_AUDIT_ENTRIES_FOR("barix.audit.entries_for"),
    BARIX_COMPRESS_TRIGGERED("barix.compress.triggered"),

    BARIX_LAGSCAN_START("barix.lagscan.start"),
    BARIX_LAGSCAN_TITLE("barix.lagscan.title"),
    BARIX_LAGSCAN_LINE("barix.lagscan.line"),
    BARIX_LAGSCAN_END("barix.lagscan.end"),

    BARIX_ALERT_TEST("barix.alert.test"),
    BARIX_STARTUP("barix.startup");

    public final String id;

    LangKeyLocal(String id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return id;
    }
}
