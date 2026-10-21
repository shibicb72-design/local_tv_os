package com.gathin.files;

/** Constants with no Android dependency, so JVM tests can check them. */
final class FilesInfo {
    static final String PACKAGE = "com.gathin.files";
    static final String TITLE = "FILES";
    /** What the operator runs when the TV has no settings screen for All files access (plan D5). */
    static final String APPOPS_COMMAND =
            "adb shell appops set --uid " + PACKAGE + " MANAGE_EXTERNAL_STORAGE allow";

    private FilesInfo() {
    }
}
